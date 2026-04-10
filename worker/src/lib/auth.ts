import type { Context } from "hono";
import type { DeviceId } from "./types";

export class MissingDeviceError extends Error {
  constructor() {
    super("Missing X-Ranti-Device header");
  }
}

/**
 * Extracts the per-install device id from the request.
 * MVP auth: anonymous device-bound token. Generated client-side on first launch,
 * persisted in DataStore, sent on every request.
 */
export function getDeviceId(c: Context): DeviceId {
  const id = c.req.header("X-Ranti-Device");
  if (!id || id.length < 8) throw new MissingDeviceError();
  return id;
}

/**
 * Upsert device row so we have a record of every device that has ever called us.
 * Idempotent — safe to call on every request.
 */
export async function touchDevice(db: D1Database, deviceId: DeviceId): Promise<void> {
  await db
    .prepare(
      `INSERT INTO devices (id, first_seen_at, last_seen_at)
       VALUES (?1, ?2, ?2)
       ON CONFLICT(id) DO UPDATE SET last_seen_at = excluded.last_seen_at`,
    )
    .bind(deviceId, new Date().toISOString())
    .run();
}
