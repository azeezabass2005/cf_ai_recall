// SPEC §15 — Typed D1 query helpers.
//
// Every SQL statement the worker executes lives here. Tool handlers and route
// handlers import these instead of writing raw SQL inline, keeping the
// data-access surface in one place and making it trivial to audit or migrate.

import type {
  Reminder,
  ReminderSource,
  ReminderStatus,
  Trigger,
  DeviceId,
  IsoTimestamp,
} from "../lib/types";

// ─── D1 row shapes ──────────────────────────────────────────────────────────
// These map 1:1 to the columns in 0001_init.sql. JSON columns are stored as
// TEXT and parsed/stringified at the boundary.

export interface ReminderRow {
  id: string;
  device_id: string;
  body: string;
  trigger_type: "time" | "location";
  trigger_data: string;          // JSON blob → Trigger
  recurrence_data: string | null; // JSON blob → RecurrenceRule | null
  status: ReminderStatus;
  source: ReminderSource;
  created_at: string;
  fired_at: string | null;
  fire_count: number;
  next_fire_at: string | null;
  snoozed_until: string | null;
}

export interface NicknameRow {
  id: string;
  device_id: string;
  nickname: string;
  place_name: string;
  place_id: string | null;
  lat: number;
  lng: number;
  created_at: string;
  updated_at: string;
}

export interface MessageRow {
  id: string;
  device_id: string;
  sender: "user" | "recall";
  text: string;
  timestamp: string;
  related_reminder_id: string | null;
  input_mode: "text" | "voice";
}

export interface DeviceRow {
  id: string;
  first_seen_at: string;
  last_seen_at: string;
}

// ─── Row ↔ Domain converters ────────────────────────────────────────────────

export function rowToReminder(row: ReminderRow): Reminder {
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

import type { Nickname } from "../lib/types";

export function rowToNickname(row: NicknameRow): Nickname {
  return {
    id: row.id,
    device_id: row.device_id,
    nickname: row.nickname,
    place_name: row.place_name,
    place_id: row.place_id,
    lat: row.lat,
    lng: row.lng,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

// ─── Device queries ─────────────────────────────────────────────────────────

/**
 * Upsert device row. Idempotent — safe to call on every request.
 */
export async function touchDevice(
  db: D1Database,
  deviceId: DeviceId,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO devices (id, first_seen_at, last_seen_at)
       VALUES (?1, ?2, ?2)
       ON CONFLICT(id) DO UPDATE SET last_seen_at = excluded.last_seen_at`,
    )
    .bind(deviceId, new Date().toISOString())
    .run();
}

// ─── Reminder queries ───────────────────────────────────────────────────────

export interface InsertReminderParams {
  id: string;
  deviceId: DeviceId;
  body: string;
  trigger: Trigger;
  recurrence: Reminder["recurrence"];
  source: ReminderSource;
  nextFireAt: string | null;
}

export async function insertReminder(
  db: D1Database,
  p: InsertReminderParams,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO reminders (
         id, device_id, body, trigger_type, trigger_data, recurrence_data,
         status, source, created_at, fired_at, fire_count, next_fire_at, snoozed_until
       ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'pending', ?7, ?8, NULL, 0, ?9, NULL)`,
    )
    .bind(
      p.id,
      p.deviceId,
      p.body,
      p.trigger.type,
      JSON.stringify(p.trigger),
      p.recurrence ? JSON.stringify(p.recurrence) : null,
      p.source,
      new Date().toISOString(),
      p.nextFireAt,
    )
    .run();
}

export async function selectReminderById(
  db: D1Database,
  id: string,
  deviceId: DeviceId,
): Promise<Reminder | null> {
  const row = await db
    .prepare("SELECT * FROM reminders WHERE id = ?1 AND device_id = ?2")
    .bind(id, deviceId)
    .first<ReminderRow>();
  return row ? rowToReminder(row) : null;
}

export async function selectReminderByMatch(
  db: D1Database,
  matchText: string,
  deviceId: DeviceId,
): Promise<Reminder | null> {
  const like = `%${matchText.toLowerCase()}%`;
  const row = await db
    .prepare(
      `SELECT * FROM reminders
       WHERE device_id = ?1
         AND LOWER(body) LIKE ?2
         AND status IN ('pending', 'snoozed', 'paused')
       ORDER BY next_fire_at ASC
       LIMIT 1`,
    )
    .bind(deviceId, like)
    .first<ReminderRow>();
  return row ? rowToReminder(row) : null;
}

export type ReminderFilter = "active" | "recurring" | "history";

export async function selectReminders(
  db: D1Database,
  deviceId: DeviceId,
  filter: ReminderFilter,
): Promise<Reminder[]> {
  let whereClause: string;
  switch (filter) {
    case "recurring":
      whereClause =
        "device_id = ?1 AND recurrence_data IS NOT NULL AND status != 'expired'";
      break;
    case "history":
      whereClause =
        "device_id = ?1 AND status IN ('fired', 'dismissed', 'expired')";
      break;
    default:
      whereClause =
        "device_id = ?1 AND status IN ('pending', 'snoozed', 'paused')";
  }
  const { results } = await db
    .prepare(
      `SELECT * FROM reminders WHERE ${whereClause} ORDER BY next_fire_at ASC LIMIT 50`,
    )
    .bind(deviceId)
    .all<ReminderRow>();
  return (results ?? []).map(rowToReminder);
}

export async function updateReminderFields(
  db: D1Database,
  id: string,
  deviceId: DeviceId,
  fields: {
    body?: string;
    trigger?: Trigger;
    recurrence?: Reminder["recurrence"];
    nextFireAt?: string | null;
    status?: ReminderStatus;
    snoozedUntil?: string | null;
  },
): Promise<void> {
  const sets: string[] = [];
  const binds: unknown[] = [];
  let idx = 1;

  if (fields.body !== undefined) {
    sets.push(`body = ?${idx}`);
    binds.push(fields.body);
    idx++;
  }
  if (fields.trigger !== undefined) {
    sets.push(`trigger_data = ?${idx}`);
    binds.push(JSON.stringify(fields.trigger));
    idx++;
    // Also update trigger_type to stay consistent
    sets.push(`trigger_type = ?${idx}`);
    binds.push(fields.trigger.type);
    idx++;
  }
  if (fields.recurrence !== undefined) {
    sets.push(`recurrence_data = ?${idx}`);
    binds.push(fields.recurrence ? JSON.stringify(fields.recurrence) : null);
    idx++;
  }
  if (fields.nextFireAt !== undefined) {
    sets.push(`next_fire_at = ?${idx}`);
    binds.push(fields.nextFireAt);
    idx++;
  }
  if (fields.status !== undefined) {
    sets.push(`status = ?${idx}`);
    binds.push(fields.status);
    idx++;
  }
  if (fields.snoozedUntil !== undefined) {
    sets.push(`snoozed_until = ?${idx}`);
    binds.push(fields.snoozedUntil);
    idx++;
  }

  if (sets.length === 0) return;

  // Append id and device_id as the final two bind params.
  const idIdx = idx;
  const deviceIdx = idx + 1;
  binds.push(id, deviceId);

  await db
    .prepare(
      `UPDATE reminders SET ${sets.join(", ")} WHERE id = ?${idIdx} AND device_id = ?${deviceIdx}`,
    )
    .bind(...binds)
    .run();
}

export async function deleteReminder(
  db: D1Database,
  id: string,
  deviceId: DeviceId,
): Promise<void> {
  await db
    .prepare("DELETE FROM reminders WHERE id = ?1 AND device_id = ?2")
    .bind(id, deviceId)
    .run();
}

// ─── Nickname queries ───────────────────────────────────────────────────────

export async function upsertNickname(
  db: D1Database,
  params: {
    id: string;
    deviceId: DeviceId;
    nickname: string;
    placeName: string;
    placeId: string | null;
    lat: number;
    lng: number;
  },
): Promise<Nickname> {
  const now = new Date().toISOString();
  await db
    .prepare(
      `INSERT INTO nicknames (id, device_id, nickname, place_name, place_id, lat, lng, created_at, updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8)
       ON CONFLICT (device_id, nickname) DO UPDATE SET
         place_name = excluded.place_name,
         place_id   = excluded.place_id,
         lat        = excluded.lat,
         lng        = excluded.lng,
         updated_at = excluded.updated_at`,
    )
    .bind(
      params.id,
      params.deviceId,
      params.nickname,
      params.placeName,
      params.placeId,
      params.lat,
      params.lng,
      now,
    )
    .run();

  // Fetch the actual row (UPSERT might have reused the existing id)
  const row = await db
    .prepare("SELECT * FROM nicknames WHERE device_id = ?1 AND nickname = ?2")
    .bind(params.deviceId, params.nickname)
    .first<NicknameRow>();

  return rowToNickname(row!);
}

export async function selectNicknames(
  db: D1Database,
  deviceId: DeviceId,
): Promise<Nickname[]> {
  const { results } = await db
    .prepare(
      "SELECT * FROM nicknames WHERE device_id = ?1 ORDER BY nickname ASC LIMIT 100",
    )
    .bind(deviceId)
    .all<NicknameRow>();
  return (results ?? []).map(rowToNickname);
}

export async function selectNicknameByText(
  db: D1Database,
  deviceId: DeviceId,
  nickname: string,
): Promise<Nickname | null> {
  const row = await db
    .prepare(
      "SELECT * FROM nicknames WHERE device_id = ?1 AND LOWER(nickname) = LOWER(?2)",
    )
    .bind(deviceId, nickname)
    .first<NicknameRow>();
  return row ? rowToNickname(row) : null;
}

export async function deleteNickname(
  db: D1Database,
  id: string,
  deviceId: DeviceId,
): Promise<void> {
  await db
    .prepare("DELETE FROM nicknames WHERE id = ?1 AND device_id = ?2")
    .bind(id, deviceId)
    .run();
}

// ─── Message queries ────────────────────────────────────────────────────────

export interface InsertMessageParams {
  id: string;
  deviceId: DeviceId;
  sender: "user" | "recall";
  text: string;
  timestamp: IsoTimestamp;
  relatedReminderId: string | null;
  inputMode: "text" | "voice";
}

export async function insertMessage(
  db: D1Database,
  p: InsertMessageParams,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO messages (id, device_id, sender, text, timestamp, related_reminder_id, input_mode)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)`,
    )
    .bind(
      p.id,
      p.deviceId,
      p.sender,
      p.text,
      p.timestamp,
      p.relatedReminderId,
      p.inputMode,
    )
    .run();
}

export async function selectMessages(
  db: D1Database,
  deviceId: DeviceId,
  limit = 50,
): Promise<MessageRow[]> {
  const { results } = await db
    .prepare(
      `SELECT * FROM messages
       WHERE device_id = ?1
       ORDER BY timestamp DESC
       LIMIT ?2`,
    )
    .bind(deviceId, limit)
    .all<MessageRow>();
  return results ?? [];
}
