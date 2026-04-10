import { Agent } from "agents";
import type { Env } from "./index";
import type { Reminder } from "./lib/types";
import {
  dispatchReminderTool,
  reminderTools,
  type HandlerContext,
} from "./tools/reminders";

/**
 * RantiAgent — one Durable Object per device id.
 *
 * Backed by **Cloudflare Workers AI** (Llama 3.3 70B instruct, fast variant).
 * No external API key — the `AI` binding is provisioned on every Worker with
 * Workers AI enabled. Free tier handles a lot of turns before rate limiting
 * kicks in, and the `-fp8-fast` flavour is tuned for low latency.
 *
 * Responsibilities:
 *   • Holds per-device conversation state in Durable Object storage so the
 *     model sees prior turns (disambiguation, follow-ups, "cancel the one I
 *     just set", …).
 *   • Runs a tool-use loop. Tools are the only way the agent can affect the
 *     world — right now that's the reminder surface in tools/reminders.ts.
 *   • Returns both a human-readable `response_text` and a structured
 *     `reminder` payload. The Android client uses the structured payload to
 *     schedule a local AlarmManager alarm so time-based reminders fire even
 *     when the network is down.
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
 *
 * If we only parse (A) — which we used to — every (B)/(C) call looks like the
 * model produced *no* tool calls, so we just echo its prose reply and never
 * actually schedule the reminder. That's exactly the "request has been
 * accepted" hallucination the user was seeing.
 */
function normaliseToolCall(raw: unknown): AiToolCall | null {
  if (!raw || typeof raw !== "object") return null;
  const obj = raw as Record<string, unknown>;

  // Shape B: OpenAI nested form.
  const fn = obj.function as Record<string, unknown> | undefined;
  const name =
    (typeof obj.name === "string" ? obj.name : undefined) ??
    (fn && typeof fn.name === "string" ? fn.name : undefined);
  if (!name) return null;

  const rawArgs =
    obj.arguments ?? (fn ? fn.arguments : undefined) ?? {};
  let args: Record<string, unknown> = {};
  if (typeof rawArgs === "string") {
    // Shape C: stringified JSON.
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
  /** IANA timezone from the client, e.g. "Africa/Lagos". */
  tz?: string;
  /** Device-local wall-clock time as an ISO 8601 string, for logging. */
  client_now?: string;
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
    | "reminders_listed";
  reminder: Reminder | null;
  reminders?: Reminder[];
  disambiguation: null;
}

const MAX_TOOL_ITERATIONS = 5;
const MAX_HISTORY_MESSAGES = 40;
const MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";

const SYSTEM_PROMPT = `You are Ranti, a voice-first reminder assistant built for Nigerians. Your job is to help the user remember things — at specific times, on recurring schedules, or when they arrive at a place.

Personality:
- Warm, concise, and natural. Write the way a Nigerian friend would speak. One or two sentences.
- When you set a reminder, confirm the specific time or recurrence back to the user ("Sure — I'll remind you at 3:42 PM."). This builds trust.
- When the user is vague ("remind me later"), ask a quick clarifying question instead of guessing.

Tools you have:
- create_reminder: create a new reminder. Pass the user's time phrase VERBATIM as time_expr — the backend parses it against the user's local timezone. Do NOT try to compute ISO dates yourself; you will get them wrong.
- list_reminders, delete_reminder, update_reminder, pause_reminder, resume_reminder: manage existing ones.
- resolve_time: if you want to double-check a phrase before creating, resolve it first.

Rules:
- CRITICAL: If the user asks you to remind them of anything at any time ("remind me...", "don't let me forget...", "when it is 3:45...", "in 10 minutes...", "every morning..."), you MUST call the create_reminder tool BEFORE replying. NEVER tell the user "done", "I'll remind you", "request accepted", or anything similar unless the create_reminder tool has actually been called and returned a result. A reply without a tool call is a LIE that breaks the user's trust.
- Never invent reminders that weren't asked for.
- Never ask for the user's timezone — it's already supplied in your context.
- Reminders about a place ("when I get to Shoprite") take a location_query instead of a time_expr. Location reminders aren't fully wired yet — if the user asks for one, acknowledge it and say you're still learning places.
- When the user says "every <something>", that's recurring. Pass the whole phrase as time_expr and let the backend decide.
- Keep your final reply SHORT (1–2 sentences). No bullet lists in normal replies.
- When the user just chats (no reminder ask), just reply conversationally — don't call any tool.

Examples:
User: "remind me at 3:45 to greet my friend"
→ Call create_reminder({ body: "greet my friend", time_expr: "at 3:45" }). Then reply: "Got it — I'll nudge you at 3:45 to greet your friend."

User: "in 10 minutes remind me to check the rice"
→ Call create_reminder({ body: "check the rice", time_expr: "in 10 minutes" }). Then reply: "Sure — 10 minutes on the clock."`;

/**
 * Convert our Anthropic-style tool schemas to the OpenAI / Workers AI
 * function-calling format. We keep the source schemas readable and only
 * translate at the boundary.
 */
function toWorkersAiTools(): AiFunctionTool[] {
  return reminderTools.map((t) => ({
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

    const handlerCtx: HandlerContext = {
      db: this.env.DB,
      deviceId,
      source,
      parseCtx: { now: new Date().toISOString(), tz },
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

    try {
      for (let iter = 0; iter < MAX_TOOL_ITERATIONS; iter++) {
        const messages: AiMessage[] = [
          { role: "system", content: systemWithCtx },
          ...history,
        ];

        // Workers AI's typed schema for this model is narrower than what the
        // runtime actually accepts (it doesn't describe nested `oneOf`
        // schemas or the `tool_calls` shape in the response). The call is
        // plain JSON under the hood, so we cast at the boundary.
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
            const result = await dispatchReminderTool(
              call.name,
              call.arguments ?? {},
              handlerCtx,
            );

            if (call.name === "create_reminder") {
              const r = result as { reminder: Reminder; note?: string };
              createdReminder = r.reminder;
              confirmationNote = r.note;
              action = "reminder_created";
            } else if (call.name === "list_reminders") {
              listedReminders = result as Reminder[];
              action = "reminders_listed";
            } else if (call.name === "delete_reminder") {
              const r = result as { deleted: Reminder | null };
              if (r.deleted) {
                createdReminder = r.deleted; // client uses the id to cancel alarm
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
      disambiguation: null,
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
        ? `Got it — I'll remind you: "${reminder.body}".`
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
    default:
      return "Got it.";
  }
}
