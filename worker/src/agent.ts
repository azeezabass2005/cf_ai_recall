import { Agent } from "agents";
import type { Env } from "./index";
import type { Reminder } from "./lib/types";
import {
  dispatchReminderTool,
  reminderTools,
  type HandlerContext,
} from "./tools/reminders";
import {
  dispatchNicknameTool,
  nicknameTools,
  type NicknameHandlerContext,
} from "./tools/nicknames";
import {
  resolvePlaceTool,
  handleResolvePlace,
  type PlaceResult,
} from "./tools/places";

/**
 * RantiAgent — one Durable Object per device id.
 *
 * Backed by **Cloudflare Workers AI** (Llama 3.3 70B instruct, fast variant).
 * Runs a tool-use loop with reminder, nickname, and place-resolution tools.
 * Returns structured payloads so the Android client can schedule AlarmManager
 * alarms (time reminders) and register Geofences (location reminders).
 */

// Workers AI uses an OpenAI-style chat format.
interface AiMessage {
  role: "system" | "user" | "assistant" | "tool";
  content: string;
  tool_calls?: AiToolCall[];
  tool_call_id?: string;
  name?: string;
}

interface AiToolCall {
  id?: string;
  name: string;
  arguments: Record<string, unknown>;
}

/**
 * Workers AI's Llama 3.3 is inconsistent about tool-call shape. Depending on
 * the runtime patch level you get one of:
 *
 *   A)  { name: "create_reminder", arguments: {...} }          (flat)
 *   B)  { id, type: "function", function: { name, arguments } } (OpenAI)
 *   C)  { name, arguments: "<JSON string>" }                    (stringified args)
 */
function normaliseToolCall(raw: unknown): AiToolCall | null {
  if (!raw || typeof raw !== "object") return null;
  const obj = raw as Record<string, unknown>;

  const fn = obj.function as Record<string, unknown> | undefined;
  const name =
    (typeof obj.name === "string" ? obj.name : undefined) ??
    (fn && typeof fn.name === "string" ? fn.name : undefined);
  if (!name) return null;

  const rawArgs =
    obj.arguments ?? (fn ? fn.arguments : undefined) ?? {};
  let args: Record<string, unknown> = {};
  if (typeof rawArgs === "string") {
    try {
      const parsed = JSON.parse(rawArgs);
      if (parsed && typeof parsed === "object") {
        args = parsed as Record<string, unknown>;
      }
    } catch {
      /* ignore — leave args empty */
    }
  } else if (rawArgs && typeof rawArgs === "object") {
    args = rawArgs as Record<string, unknown>;
  }

  const id = typeof obj.id === "string" ? obj.id : undefined;
  return { id, name, arguments: args };
}

interface AiFunctionTool {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

interface AiResponse {
  response?: string;
  tool_calls?: AiToolCall[];
}

interface AgentState {
  history: AiMessage[];
}

interface ChatRequestBody {
  message: string;
  input_mode?: "text" | "voice";
  tz?: string;
  client_now?: string;
  /** User's current lat/lng for proximity bias in place resolution. */
  user_lat?: number;
  user_lng?: number;
}

export interface DisambiguationOption {
  place_id: string;
  name: string;
  formatted_address: string;
  lat: number;
  lng: number;
}

interface ChatResponseBody {
  response_text: string;
  action:
    | "noop"
    | "reminder_created"
    | "reminder_deleted"
    | "reminder_updated"
    | "reminder_paused"
    | "reminder_resumed"
    | "reminders_listed"
    | "nickname_saved"
    | "nicknames_listed"
    | "nickname_deleted"
    | "disambiguation_needed";
  reminder: Reminder | null;
  reminders?: Reminder[];
  disambiguation: {
    prompt: string;
    options: DisambiguationOption[];
  } | null;
}

const MAX_TOOL_ITERATIONS = 8;
const MAX_HISTORY_MESSAGES = 40;
const MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";

const REMINDER_TOOL_NAMES: Set<string> = new Set(reminderTools.map((t) => t.name));
const NICKNAME_TOOL_NAMES: Set<string> = new Set(nicknameTools.map((t) => t.name));

const SYSTEM_PROMPT = `You are Ranti, a voice-first reminder assistant built for Nigerians. Your job is to help the user remember things — at specific times, on recurring schedules, or when they arrive at a place.

Personality:
- Warm, concise, and natural. Write the way a Nigerian friend would speak. One or two sentences.
- You're the user's buddy, not a customer support agent. Say things like "Sure!", "Of course!", "No wahala!", "Got you!" — never start with "Your reminder has been set…" or anything stiff like that.
- When the user asks if you CAN do something (e.g. "Can you remind me of something?"), respond enthusiastically and ask what they need. For example: "Of course! What should I remind you about, and when?"
- When the user is vague or missing details, ask for them warmly and casually — like a friend would. Never say "You have not provided enough information." Instead say something like "Sure, I can do that! Just tell me what to remind you about and when."
- When you set a reminder, confirm the specific time, recurrence, or place back to the user. This builds trust.
- When the user is vague ("remind me later"), ask a quick friendly clarifying question instead of guessing.

Tools you have:

TIME REMINDERS:
- create_reminder: create a new reminder. Pass the user's time phrase VERBATIM as time_expr — the backend parses it against the user's local timezone. Do NOT try to compute ISO dates yourself; you will get them wrong.
- list_reminders, delete_reminder, update_reminder, pause_reminder, resume_reminder: manage existing ones.
- resolve_time: if you want to double-check a phrase before creating, resolve it first.

LOCATION REMINDERS (§8–§10):
- resolve_place: resolve a free-text place name (e.g. "Shoprite", "Faculty of Technology") to concrete coordinates. ALWAYS call this before creating a location-based reminder — you need the lat/lng.
- save_nickname: save a personal nickname for a place (e.g. "my hostel" → the resolved place). Call this when the user says something like "save that as my hostel" or when they use a nickname you don't recognise and you've just resolved the place.
- get_nicknames: list the user's saved place nicknames. Call this first when the user mentions a place nickname so you can look up its coordinates.
- delete_nickname: delete a saved place nickname.

LOCATION REMINDER FLOW:
1. CRITICAL: When the user says anything like "when I reach [place]", "when I get to [place]", "when I arrive at [place]", "at [place]", "when I'm at [place]" — this is ALWAYS a LOCATION reminder, NEVER a time reminder. Do NOT pass these phrases to time_expr. ALWAYS call resolve_place first.
2. User says "remind me when I get to Shoprite" → call resolve_place({ query: "Shoprite" }).
3. If resolve_place returns exactly ONE result → call create_reminder with that result's lat, lng, place_name, place_id plus location_query.
4. If resolve_place returns MULTIPLE results → DO NOT create the reminder yet. Instead, tell the user you found multiple matches and list them clearly (numbered). Ask them to pick one. When they pick, use that result's coordinates.
5. If resolve_place returns ZERO results → tell the user you couldn't find that place and ask them to be more specific.
6. IMPORTANT: Even if a place name contains words that look like times (e.g. "Car Park" does NOT mean a time), you must still treat the entire phrase as a location query.

NICKNAME FLOW:
- When the user mentions a place like "my hostel", "home", "the shop" that sounds like a personal nickname, first call get_nicknames to check if it's saved.
- If found: use its coordinates directly for create_reminder (no resolve_place needed).
- If not found: ask the user where that is, then resolve_place → create_reminder, and offer to save the nickname for next time.
- When the user says "save [place] as [nickname]" → call save_nickname.

RULES:
- IMPORTANT: Only create a reminder when the user's message clearly expresses an intent to be reminded of something. If the user says something that doesn't make sense as a reminder (random statements, gibberish, unrelated questions), just chat back normally — do NOT call create_reminder. For example, "the sky is purple" is NOT a reminder request.
- CRITICAL: If the user asks you to remind them of anything ("remind me...", "don't let me forget...", etc.), you MUST call the appropriate tool(s) BEFORE replying. NEVER tell the user "done" or "I'll remind you" without actually calling create_reminder. A reply without a tool call is a LIE that breaks the user's trust.
- CRITICAL: NEVER say you "cannot" or "are not able to" do something if the user is asking for a reminder — you have ALL the tools you need. If the user asks for a location reminder, call resolve_place then create_reminder. If the user asks for a time reminder, call create_reminder with time_expr. NEVER refuse a reminder request.
- CRITICAL: When the user uses phrases like "when I reach", "when I get to", "when I arrive at", "when I'm near" — these are LOCATION triggers. You MUST call resolve_place with the place name, then create_reminder with the coordinates. Do NOT try to parse these as time expressions.
- CRITICAL: When the user follows up on a reminder they JUST set in the previous turn by saying something like "everyday", "every day", "daily", "make it recurring", "every Monday", etc. — DO NOT call create_reminder. Instead, call update_reminder with the reminder's id from the previous turn and pass the recurrence phrase as new_time_expr. This prevents duplicate reminders.
- Never invent reminders that weren't asked for.
- Never ask for the user's timezone — it's already supplied in your context.
- When the user says "every <something>", that's recurring. Pass the whole phrase as time_expr and let the backend decide.
- Keep your final reply SHORT (1–2 sentences). No bullet lists in normal replies — except when listing disambiguation options.
- CRITICAL: Your responses must sound like a warm Nigerian friend, NOT like a system notification. NEVER say things like "You have a reminder set for..." — that sounds like a robot. Instead say things like "Sure! I'll remind you at 6:50 PM about your class 👍" or "Got it — I'll ping you every day at 6:50 PM about the class ⏰". Always be casual and human.
- When confirming a reminder, always rephrase the user's words from first person to second person. "charge my power bank" → "charge your power bank". "call my mum" → "call your mum". Never parrot the user's exact phrasing back with "my" — it sounds robotic.
- When the user just chats (no reminder ask), just reply conversationally — don't call any tool.

Examples:
User: "remind me at 3:45 to greet my friend"
→ Call create_reminder({ body: "greet my friend", time_expr: "at 3:45" }). Then reply: "Sure! I'll nudge you at 3:45 to greet your friend 👍"

User: "in 10 minutes remind me to check the rice"
→ Call create_reminder({ body: "check the rice", time_expr: "in 10 minutes" }). Then reply: "On it — 10 minutes on the clock ⏰"

User: "remind me at 6:50 PM that I have a class by 7:00 PM"
→ Call create_reminder({ body: "you have a class by 7:00 PM", time_expr: "at 6:50 PM" }). Then reply: "Got you! I'll remind you at 6:50 PM about your class 👍"
User (follow-up): "everyday"
→ Call update_reminder({ reminder_id: "<id from previous turn>", new_time_expr: "every day at 6:50 PM" }). Then reply: "Sure, I'll remind you about the class every day! 🔁"

User: "remind me to buy bread when I get to Shoprite"
→ Call resolve_place({ query: "Shoprite" }). If 1 result: call create_reminder({ body: "buy bread", location_query: "Shoprite", place_name: "Shoprite Obafemi Awolowo", place_id: "...", lat: ..., lng: ... }). Reply: "I'll ping you when you're near Shoprite Obafemi Awolowo."

User: "remind me when I get home to call mum"
→ Call get_nicknames({}). If "home" found: call create_reminder with saved coords. Reply: "Done — I'll remind you when you get home."

User: "Can you remind me of something?"
→ Do NOT call any tool. Reply: "Of course! What do you want me to remind you about, and when?"

User: "the moon is made of cheese lol"
→ Do NOT call any tool. Reply conversationally, e.g. "Haha, I wish! Anything you need me to remind you about though? 😄"

User: "when I reach Fajuyi Car Park remind me to give my friend his charger"
→ Call resolve_place({ query: "Fajuyi Car Park" }). If 1 result: call create_reminder({ body: "give your friend his charger", location_query: "Fajuyi Car Park", place_name: "...", ... }). Reply: "Got it — I'll remind you when you're near Fajuyi Car Park!"

User: "remind me when I reach Adekunle Fajuyi Hall to give my friend his book"
→ Call resolve_place({ query: "Adekunle Fajuyi Hall" }). Then create_reminder with coordinates. This is a LOCATION reminder, not a time reminder.

COMMON MISTAKES TO AVOID:
- NEVER interpret "when I reach [place]" as a time expression. "reach" here means "arrive at", it is NOT a time.
- NEVER refuse a location-based reminder request. You have resolve_place and create_reminder — use them.
- NEVER pass place names to time_expr. Place names go to resolve_place({ query: "..." }).`;

/**
 * Convert our Anthropic-style tool schemas to Workers AI function-calling format.
 */
function toWorkersAiTools(): AiFunctionTool[] {
  const allTools = [
    ...reminderTools,
    ...nicknameTools,
    resolvePlaceTool,
  ];
  return allTools.map((t) => ({
    name: t.name,
    description: t.description,
    parameters: t.input_schema as unknown as Record<string, unknown>,
  }));
}

export class RantiAgent extends Agent<Env, AgentState> {
  override initialState: AgentState = { history: [] };

  override async onRequest(request: Request): Promise<Response> {
    if (request.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }

    const body = (await request.json().catch(() => null)) as ChatRequestBody | null;
    if (!body?.message) {
      return Response.json({ error: "Missing 'message' in body" }, { status: 400 });
    }

    const deviceId = request.headers.get("X-Ranti-Device") ?? this.name;
    const tz = body.tz || "Africa/Lagos";
    const source = body.input_mode === "voice" ? "voice" : "chat_text";

    const reminderCtx: HandlerContext = {
      db: this.env.DB,
      deviceId,
      source,
      parseCtx: { now: new Date().toISOString(), tz },
    };

    const nicknameCtx: NicknameHandlerContext = {
      db: this.env.DB,
      deviceId,
    };

    // Pull history out of Durable Object state and append the new turn.
    const state = this.state ?? this.initialState;
    const history: AiMessage[] = [...(state.history ?? [])];
    history.push({ role: "user", content: body.message });

    const nowInZone = new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      dateStyle: "full",
      timeStyle: "short",
    }).format(new Date());
    const systemWithCtx = `${SYSTEM_PROMPT}\n\nCurrent context:\n- User timezone: ${tz}\n- Current local time: ${nowInZone}\n- Input mode: ${source}`;

    const tools = toWorkersAiTools();

    // ─── Tool-use loop ──────────────────────────────────────────────────
    let lastTextResponse = "";
    let action: ChatResponseBody["action"] = "noop";
    let createdReminder: Reminder | null = null;
    let listedReminders: Reminder[] | undefined;
    let confirmationNote: string | undefined;
    let disambiguation: ChatResponseBody["disambiguation"] = null;
    /** Guard against the model calling create_reminder multiple times. */
    let reminderAlreadyCreated = false;

    try {
      for (let iter = 0; iter < MAX_TOOL_ITERATIONS; iter++) {
        const messages: AiMessage[] = [
          { role: "system", content: systemWithCtx },
          ...history,
        ];

        const completion = (await (this.env.AI as unknown as {
          run: (model: string, input: unknown) => Promise<unknown>;
        }).run(MODEL, { messages, tools })) as AiResponse;

        const rawToolCalls = (completion.tool_calls ?? []) as unknown[];
        const toolCalls = rawToolCalls
          .map(normaliseToolCall)
          .filter((c): c is AiToolCall => c !== null);
        if (rawToolCalls.length > 0 && toolCalls.length === 0) {
          console.warn(
            "Workers AI returned tool_calls we couldn't parse:",
            JSON.stringify(rawToolCalls),
          );
        }
        const textOut = (completion.response ?? "").trim();
        if (textOut) lastTextResponse = textOut;

        if (toolCalls.length === 0) {
          history.push({ role: "assistant", content: textOut });
          break;
        }

        // Record the assistant turn with its tool calls.
        history.push({
          role: "assistant",
          content: textOut,
          tool_calls: toolCalls,
        });

        // Execute each tool call and feed the results back as tool messages.
        for (const call of toolCalls) {
          const callId = call.id ?? `call_${iter}_${call.name}`;
          try {
            let result: unknown;

            // Prevent the model from creating multiple reminders in one turn.
            if (call.name === "create_reminder" && reminderAlreadyCreated) {
              history.push({
                role: "tool",
                tool_call_id: callId,
                name: call.name,
                content: JSON.stringify({
                  error: "A reminder was already created in this turn. Do not call create_reminder again — just confirm the reminder to the user.",
                }),
              });
              continue;
            }

            if (call.name === "resolve_place") {
              // Place resolution — inject user lat/lng bias if available.
              const args = { ...call.arguments };
              if (body.user_lat != null && body.user_lng != null) {
                args.bias_lat = args.bias_lat ?? body.user_lat;
                args.bias_lng = args.bias_lng ?? body.user_lng;
              }
              const places = await handleResolvePlace(args);
              result = { places };

              // If multiple results, set disambiguation so the client can show a picker.
              if (places.length > 1) {
                disambiguation = {
                  prompt: "I found a few places. Which one did you mean?",
                  options: places.map((p) => ({
                    place_id: p.place_id,
                    name: p.name,
                    formatted_address: p.formatted_address,
                    lat: p.lat,
                    lng: p.lng,
                  })),
                };
                action = "disambiguation_needed";
              }
            } else if (NICKNAME_TOOL_NAMES.has(call.name)) {
              result = await dispatchNicknameTool(call.name, call.arguments ?? {}, nicknameCtx);

              if (call.name === "save_nickname") action = "nickname_saved";
              else if (call.name === "get_nicknames") action = "nicknames_listed";
              else if (call.name === "delete_nickname") action = "nickname_deleted";
            } else if (REMINDER_TOOL_NAMES.has(call.name)) {
              result = await dispatchReminderTool(call.name, call.arguments ?? {}, reminderCtx);

              if (call.name === "create_reminder") {
                const r = result as { reminder: Reminder; note?: string };
                createdReminder = r.reminder;
                confirmationNote = r.note;
                action = "reminder_created";
                reminderAlreadyCreated = true;
              } else if (call.name === "list_reminders") {
                listedReminders = result as Reminder[];
                action = "reminders_listed";
              } else if (call.name === "delete_reminder") {
                const r = result as { deleted: Reminder | null };
                if (r.deleted) {
                  createdReminder = r.deleted;
                  action = "reminder_deleted";
                }
              } else if (call.name === "update_reminder") {
                const r = result as { updated: Reminder | null };
                if (r.updated) {
                  createdReminder = r.updated;
                  action = "reminder_updated";
                }
              } else if (call.name === "pause_reminder") {
                const r = result as { paused: Reminder | null };
                if (r.paused) {
                  createdReminder = r.paused;
                  action = "reminder_paused";
                }
              } else if (call.name === "resume_reminder") {
                const r = result as { resumed: Reminder | null };
                if (r.resumed) {
                  createdReminder = r.resumed;
                  action = "reminder_resumed";
                }
              }
            } else {
              result = { error: `Unknown tool: ${call.name}` };
            }

            history.push({
              role: "tool",
              tool_call_id: callId,
              name: call.name,
              content: JSON.stringify(result),
            });
          } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            history.push({
              role: "tool",
              tool_call_id: callId,
              name: call.name,
              content: JSON.stringify({ error: message }),
            });
          }
        }
      }
    } catch (err) {
      console.error("Workers AI call failed", err);
      const message = err instanceof Error ? err.message : String(err);
      return Response.json(
        {
          response_text: `I hit a snag talking to my brain (${message}). Try again in a moment?`,
          action: "noop",
          reminder: null,
          disambiguation: null,
        } satisfies ChatResponseBody,
      );
    }

    // If the model never produced any text but DID create/modify a reminder,
    // synthesise a friendly confirmation so the user isn't left hanging.
    if (!lastTextResponse) {
      lastTextResponse = friendlyDefaultFor(action, createdReminder);
    }

    // Truncate history so DO storage doesn't balloon.
    const trimmed = history.slice(-MAX_HISTORY_MESSAGES);
    await this.setState({ history: trimmed });

    const responseText = confirmationNote
      ? `${lastTextResponse}\n${confirmationNote}`.trim()
      : lastTextResponse;

    return Response.json({
      response_text: responseText,
      action,
      reminder: createdReminder,
      reminders: listedReminders,
      disambiguation,
    } satisfies ChatResponseBody);
  }
}

function friendlyDefaultFor(
  action: ChatResponseBody["action"],
  reminder: Reminder | null,
): string {
  switch (action) {
    case "reminder_created":
      return reminder
        ? `Got it — I'll remind you to ${reminder.body.replace(/\bmy\b/gi, "your")}.`
        : "Got it.";
    case "reminder_deleted":
      return "Done — I've removed that reminder.";
    case "reminder_updated":
      return "Updated.";
    case "reminder_paused":
      return "Paused.";
    case "reminder_resumed":
      return "Back on.";
    case "reminders_listed":
      return "Here's what you have coming up.";
    case "nickname_saved":
      return "Saved that nickname.";
    case "nickname_deleted":
      return "Deleted that nickname.";
    case "disambiguation_needed":
      return "I found a few places — which one did you mean?";
    default:
      return "Got it.";
  }
}
