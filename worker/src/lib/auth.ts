import type { Context } from "hono";
import type { DeviceId } from "./types";
import { touchDevice as touchDeviceQuery } from "../db/queries";

export class MissingDeviceError extends Error {
  constructor() {
    super("Missing X-Recall-Device header");
  }
}

/**
 * Extracts the per-install device id from the request.
 * MVP auth: anonymous device-bound token. Generated client-side on first launch,
 * persisted in DataStore, sent on every request.
 */
export function getDeviceId(c: Context): DeviceId {
  const id = c.req.header("X-Recall-Device");
  if (!id || id.length < 8) throw new MissingDeviceError();
  return id;
}

/**
 * Upsert device row so we have a record of every device that has ever called us.
 * Idempotent — safe to call on every request.
 *
 * Delegates to db/queries.ts — the SQL lives there.
 */
export async function touchDevice(db: D1Database, deviceId: DeviceId): Promise<void> {
  return touchDeviceQuery(db, deviceId);
}
