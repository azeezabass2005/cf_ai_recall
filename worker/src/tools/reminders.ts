// SPEC §7 — Reminder tools backed by D1.
//
// These are the primitives the LLM reaches for when a user says "remind me
// to …". The create_reminder tool accepts either a pre-resolved ISO timestamp
// (cheap path — the agent already ran parseTimeExpression) or a raw time_expr
// that we parse server-side using lib/time.ts. We never ask the model to do
// the arithmetic itself — LLMs are bad at "now + 15 minutes", and we have the
// user's wall-clock zone on hand.

import { z } from "zod";
import type { Reminder, ReminderSource, ReminderStatus, Trigger } from "../lib/types";
import {
  parseRecurrence,
  parseTimeExpression,
  nextOccurrence,
  type ParseContext,
} from "../lib/time";

// ─── Tool schemas (Anthropic tool_use format) ───────────────────────────────

export const createReminderTool = {
  name: "create_reminder",
  description:
    "Create a new reminder. Use this whenever the user asks to be reminded of something at a specific time, after a duration, on a recurring schedule, or when they arrive somewhere. Prefer passing `time_expr` verbatim from the user (e.g. 'in 15 minutes', 'tomorrow at 8am', 'every Tuesday at 6:50pm') — the backend parses it against the user's local timezone. Only pass `fire_at` when you've already resolved it to an ISO instant.",
  input_schema: {
    type: "object" as const,
    properties: {
      body: {
        type: "string",
        description: "What to remind the user about, phrased as they'd hear it back (e.g. 'Check the pot', 'Call Uncle Femi').",
      },
      time_expr: {
        type: "string",
        description: "Free-text time expression as the user said it — REQUIRED for time-based reminders unless `fire_at` is provided.",
      },
      fire_at: {
        type: "string",
        description: "Optional pre-resolved ISO 8601 UTC timestamp. Bypasses the time parser.",
      },
      location_query: {
        type: "string",
        description: "For location-based reminders only — free-text place name to resolve later.",
      },
    },
    required: ["body"],
  },
} as const;

export const listRemindersTool = {
  name: "list_reminders",
  description:
    "List the user's reminders. Use when they ask 'what do I have coming up', 'what reminders are active', 'list my reminders', etc.",
  input_schema: {
    type: "object" as const,
    properties: {
      filter: {
        type: "string",
        enum: ["active", "recurring", "history"],
        default: "active",
      },
    },
  },
} as const;

export const deleteReminderTool = {
  name: "delete_reminder",
  description:
    "Delete a reminder. Either pass the exact `reminder_id` (from a previous list_reminders call) or a short `match_text` that fuzzy-matches against the reminder body.",
  input_schema: {
    type: "object" as const,
    properties: {
      reminder_id: { type: "string" },
      match_text: { type: "string" },
    },
  },
} as const;

export const updateReminderTool = {
  name: "update_reminder",
  description:
    "Edit an existing reminder's body or time. Same matching rules as delete_reminder.",
  input_schema: {
    type: "object" as const,
    properties: {
      reminder_id: { type: "string" },
      match_text: { type: "string" },
      new_body: { type: "string" },
      new_time_expr: { type: "string" },
    },
  },
} as const;

export const pauseReminderTool = {
  name: "pause_reminder",
  description: "Pause a recurring reminder. It stops firing until resumed.",
  input_schema: {
    type: "object" as const,
    properties: {
      reminder_id: { type: "string" },
      match_text: { type: "string" },
    },
  },
} as const;

export const resumeReminderTool = {
  name: "resume_reminder",
  description: "Resume a paused recurring reminder.",
  input_schema: {
    type: "object" as const,
    properties: {
      reminder_id: { type: "string" },
      match_text: { type: "string" },
    },
  },
} as const;

export const resolveTimeTool = {
  name: "resolve_time",
  description:
    "Resolve a natural-language time expression into an ISO 8601 UTC timestamp, using the user's local timezone. Handy when you want to confirm a time to the user before actually creating the reminder.",
  input_schema: {
    type: "object" as const,
    properties: {
      time_expr: { type: "string" },
    },
    required: ["time_expr"],
  },
} as const;

// ─── Zod schemas for runtime validation ─────────────────────────────────────

const createReminderInput = z.object({
  body: z.string().min(1).max(500),
  time_expr: z.string().optional(),
  fire_at: z.string().optional(),
  location_query: z.string().optional(),
});

const matchOrIdInput = z.object({
  reminder_id: z.string().optional(),
  match_text: z.string().optional(),
});

const updateInput = matchOrIdInput.extend({
  new_body: z.string().optional(),
  new_time_expr: z.string().optional(),
});

const listInput = z.object({
  filter: z.enum(["active", "recurring", "history"]).default("active"),
});

// ─── D1 row ↔ Reminder mapping ──────────────────────────────────────────────

interface ReminderRow {
  id: string;
  device_id: string;
  body: string;
  trigger_type: "time" | "location";
  trigger_data: string;
  recurrence_data: string | null;
  status: ReminderStatus;
  source: ReminderSource;
  created_at: string;
  fired_at: string | null;
  fire_count: number;
  next_fire_at: string | null;
  snoozed_until: string | null;
}

function rowToReminder(row: ReminderRow): Reminder {
  return {
    id: row.id,
    device_id: row.device_id,
    body: row.body,
    trigger: JSON.parse(row.trigger_data) as Trigger,
    recurrence: row.recurrence_data ? JSON.parse(row.recurrence_data) : null,
    status: row.status,
    source: row.source,
    created_at: row.created_at,
    fired_at: row.fired_at,
    fire_count: row.fire_count,
    next_fire_at: row.next_fire_at,
    snoozed_until: row.snoozed_until,
  };
}

// ─── Handlers ───────────────────────────────────────────────────────────────

export interface HandlerContext {
  db: D1Database;
  deviceId: string;
  source: ReminderSource;
  parseCtx: ParseContext;
}

export async function handleCreateReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ reminder: Reminder; note?: string }> {
  const input = createReminderInput.parse(rawInput);

  let trigger: Trigger;
  let recurrence: Reminder["recurrence"] = null;
  let nextFireAt: string | null = null;
  let note: string | undefined;

  if (input.location_query) {
    // Location reminders are milestone §8 — we persist the intent here so
    // the list_reminders call shows them, but resolution happens later.
    trigger = {
      type: "location",
      place_name: input.location_query,
      place_id: null,
      lat: 0,
      lng: 0,
      radius_m: 100,
      original_expr: input.location_query,
    };
  } else {
    // Time-based. Try recurrence first, then one-shot.
    const expr = input.time_expr ?? "";
    const pre = input.fire_at;

    if (pre) {
      trigger = { type: "time", fire_at: pre, original_expr: expr || pre };
      nextFireAt = pre;
    } else {
      if (!expr) {
        throw new Error("create_reminder needs either time_expr, fire_at, or location_query");
      }
      const rec = parseRecurrence(expr, ctx.parseCtx);
      if (rec) {
        recurrence = rec.rule;
        nextFireAt = rec.next_fire_at;
        trigger = {
          type: "time",
          fire_at: rec.next_fire_at,
          original_expr: expr,
        };
      } else {
        const parsed = parseTimeExpression(expr, ctx.parseCtx);
        if (!parsed) {
          throw new Error(`Couldn't parse time expression: "${expr}"`);
        }
        trigger = { type: "time", fire_at: parsed.fire_at, original_expr: expr };
        nextFireAt = parsed.fire_at;
        note = parsed.note;
      }
    }
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();

  await ctx.db
    .prepare(
      `INSERT INTO reminders (
         id, device_id, body, trigger_type, trigger_data, recurrence_data,
         status, source, created_at, fired_at, fire_count, next_fire_at, snoozed_until
       ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'pending', ?7, ?8, NULL, 0, ?9, NULL)`,
    )
    .bind(
      id,
      ctx.deviceId,
      input.body,
      trigger.type,
      JSON.stringify(trigger),
      recurrence ? JSON.stringify(recurrence) : null,
      ctx.source,
      now,
      nextFireAt,
    )
    .run();

  const reminder: Reminder = {
    id,
    device_id: ctx.deviceId,
    body: input.body,
    trigger,
    recurrence,
    status: "pending",
    source: ctx.source,
    created_at: now,
    fired_at: null,
    fire_count: 0,
    next_fire_at: nextFireAt,
    snoozed_until: null,
  };
  return { reminder, note };
}

export async function handleListReminders(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<Reminder[]> {
  const { filter } = listInput.parse(rawInput ?? {});
  let whereClause: string;
  switch (filter) {
    case "recurring":
      whereClause = "device_id = ?1 AND recurrence_data IS NOT NULL AND status != 'expired'";
      break;
    case "history":
      whereClause = "device_id = ?1 AND status IN ('fired', 'dismissed', 'expired')";
      break;
    default:
      whereClause = "device_id = ?1 AND status IN ('pending', 'snoozed', 'paused')";
  }
  const { results } = await ctx.db
    .prepare(`SELECT * FROM reminders WHERE ${whereClause} ORDER BY next_fire_at ASC LIMIT 50`)
    .bind(ctx.deviceId)
    .all<ReminderRow>();
  return (results ?? []).map(rowToReminder);
}

/**
 * Look up a single reminder by id (exact) or body (fuzzy-ish — substring
 * match against the body, tie-break by soonest). Returns null if nothing
 * matches. Restricted to the caller's own device.
 */
async function findReminder(
  input: { reminder_id?: string; match_text?: string },
  ctx: HandlerContext,
): Promise<Reminder | null> {
  if (input.reminder_id) {
    const row = await ctx.db
      .prepare("SELECT * FROM reminders WHERE id = ?1 AND device_id = ?2")
      .bind(input.reminder_id, ctx.deviceId)
      .first<ReminderRow>();
    return row ? rowToReminder(row) : null;
  }
  if (input.match_text) {
    const like = `%${input.match_text.toLowerCase()}%`;
    const row = await ctx.db
      .prepare(
        `SELECT * FROM reminders
         WHERE device_id = ?1
           AND LOWER(body) LIKE ?2
           AND status IN ('pending', 'snoozed', 'paused')
         ORDER BY next_fire_at ASC
         LIMIT 1`,
      )
      .bind(ctx.deviceId, like)
      .first<ReminderRow>();
    return row ? rowToReminder(row) : null;
  }
  return null;
}

export async function handleDeleteReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ deleted: Reminder | null }> {
  const input = matchOrIdInput.parse(rawInput);
  const found = await findReminder(input, ctx);
  if (!found) return { deleted: null };
  await ctx.db
    .prepare("DELETE FROM reminders WHERE id = ?1 AND device_id = ?2")
    .bind(found.id, ctx.deviceId)
    .run();
  return { deleted: found };
}

export async function handleUpdateReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ updated: Reminder | null }> {
  const input = updateInput.parse(rawInput);
  const found = await findReminder(input, ctx);
  if (!found) return { updated: null };

  let newBody = found.body;
  let newTrigger: Trigger = found.trigger;
  let newNextFireAt: string | null = found.next_fire_at;
  let newRecurrence = found.recurrence;

  if (input.new_body) newBody = input.new_body;

  if (input.new_time_expr) {
    const rec = parseRecurrence(input.new_time_expr, ctx.parseCtx);
    if (rec) {
      newRecurrence = rec.rule;
      newNextFireAt = rec.next_fire_at;
      newTrigger = { type: "time", fire_at: rec.next_fire_at, original_expr: input.new_time_expr };
    } else {
      const parsed = parseTimeExpression(input.new_time_expr, ctx.parseCtx);
      if (!parsed) throw new Error(`Couldn't parse time expression: "${input.new_time_expr}"`);
      newRecurrence = null;
      newNextFireAt = parsed.fire_at;
      newTrigger = { type: "time", fire_at: parsed.fire_at, original_expr: input.new_time_expr };
    }
  }

  await ctx.db
    .prepare(
      `UPDATE reminders
       SET body = ?1, trigger_data = ?2, recurrence_data = ?3, next_fire_at = ?4
       WHERE id = ?5 AND device_id = ?6`,
    )
    .bind(
      newBody,
      JSON.stringify(newTrigger),
      newRecurrence ? JSON.stringify(newRecurrence) : null,
      newNextFireAt,
      found.id,
      ctx.deviceId,
    )
    .run();

  return {
    updated: {
      ...found,
      body: newBody,
      trigger: newTrigger,
      recurrence: newRecurrence,
      next_fire_at: newNextFireAt,
    },
  };
}

export async function handlePauseReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ paused: Reminder | null }> {
  const input = matchOrIdInput.parse(rawInput);
  const found = await findReminder(input, ctx);
  if (!found) return { paused: null };
  await ctx.db
    .prepare("UPDATE reminders SET status = 'paused' WHERE id = ?1 AND device_id = ?2")
    .bind(found.id, ctx.deviceId)
    .run();
  return { paused: { ...found, status: "paused" } };
}

export async function handleResumeReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ resumed: Reminder | null }> {
  const input = matchOrIdInput.parse(rawInput);
  const found = await findReminder(input, ctx);
  if (!found) return { resumed: null };

  // Recompute next_fire_at from now so a paused daily-at-8am reminder picks
  // up at the *next* 8am, not the one it missed while paused.
  let nextFireAt = found.next_fire_at;
  if (found.recurrence) {
    nextFireAt = nextOccurrence(found.recurrence, new Date(), ctx.parseCtx.tz).toISOString();
  }

  await ctx.db
    .prepare(
      "UPDATE reminders SET status = 'pending', next_fire_at = ?1 WHERE id = ?2 AND device_id = ?3",
    )
    .bind(nextFireAt, found.id, ctx.deviceId)
    .run();

  return { resumed: { ...found, status: "pending", next_fire_at: nextFireAt } };
}

export async function handleResolveTime(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ fire_at: string; note?: string; recurring: boolean }> {
  const input = z.object({ time_expr: z.string() }).parse(rawInput);
  const rec = parseRecurrence(input.time_expr, ctx.parseCtx);
  if (rec) return { fire_at: rec.next_fire_at, recurring: true };
  const parsed = parseTimeExpression(input.time_expr, ctx.parseCtx);
  if (!parsed) throw new Error(`Couldn't parse time expression: "${input.time_expr}"`);
  return { fire_at: parsed.fire_at, note: parsed.note, recurring: false };
}

export async function handleSnoozeReminder(
  rawInput: unknown,
  ctx: HandlerContext,
): Promise<{ snoozed: Reminder | null }> {
  const input = matchOrIdInput.extend({ minutes: z.number().min(1).max(120).default(10) }).parse(rawInput);
  const found = await findReminder(input, ctx);
  if (!found) return { snoozed: null };

  const snoozedUntil = new Date(Date.now() + input.minutes * 60_000).toISOString();
  await ctx.db
    .prepare(
      "UPDATE reminders SET status = 'snoozed', snoozed_until = ?1, next_fire_at = ?1 WHERE id = ?2 AND device_id = ?3",
    )
    .bind(snoozedUntil, found.id, ctx.deviceId)
    .run();
  return {
    snoozed: { ...found, status: "snoozed", snoozed_until: snoozedUntil, next_fire_at: snoozedUntil },
  };
}

// ─── Tool dispatch table ────────────────────────────────────────────────────

export const reminderTools = [
  createReminderTool,
  listRemindersTool,
  deleteReminderTool,
  updateReminderTool,
  pauseReminderTool,
  resumeReminderTool,
  resolveTimeTool,
] as const;

export type ReminderToolName =
  | "create_reminder"
  | "list_reminders"
  | "delete_reminder"
  | "update_reminder"
  | "pause_reminder"
  | "resume_reminder"
  | "resolve_time";

export async function dispatchReminderTool(
  name: string,
  input: unknown,
  ctx: HandlerContext,
): Promise<unknown> {
  switch (name as ReminderToolName) {
    case "create_reminder": return handleCreateReminder(input, ctx);
    case "list_reminders":  return handleListReminders(input, ctx);
    case "delete_reminder": return handleDeleteReminder(input, ctx);
    case "update_reminder": return handleUpdateReminder(input, ctx);
    case "pause_reminder":  return handlePauseReminder(input, ctx);
    case "resume_reminder": return handleResumeReminder(input, ctx);
    case "resolve_time":    return handleResolveTime(input, ctx);
    default:
      throw new Error(`Unknown reminder tool: ${name}`);
  }
}
