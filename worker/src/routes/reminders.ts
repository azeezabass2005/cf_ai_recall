// REST handlers for reminder CRUD — the manual-form path (no LLM involvement).
// These endpoints reuse the same handler functions that the LLM agent's tools
// call, ensuring consistent behavior whether the user manages reminders via
// the chat or via the in-app screens.

import { Hono } from "hono";
import type { Env } from "../index";
import { getDeviceId, touchDevice } from "../lib/auth";
import type { HandlerContext } from "../tools/reminders";
import {
  handleCreateReminder,
  handleListReminders,
  handleDeleteReminder,
  handleUpdateReminder,
  handlePauseReminder,
  handleResumeReminder,
  handleSnoozeReminder,
} from "../tools/reminders";

function buildCtx(c: { env: { DB: D1Database }; req: { header: (name: string) => string | undefined } }, deviceId: string): HandlerContext {
  const tz = c.req.header("X-Ranti-Tz") || "Africa/Lagos";
  return {
    db: c.env.DB,
    deviceId,
    source: "manual_form",
    parseCtx: { now: new Date().toISOString(), tz },
  };
}

export const reminders = new Hono<{ Bindings: Env }>()
  .use("*", async (c, next) => {
    const deviceId = getDeviceId(c);
    await touchDevice(c.env.DB, deviceId);
    c.set("deviceId" as never, deviceId as never);
    await next();
  })

  // GET /reminders?filter=active|recurring|history
  .get("/", async (c) => {
    const deviceId = getDeviceId(c);
    const filter = c.req.query("filter") || "active";
    const ctx = buildCtx(c, deviceId);
    const list = await handleListReminders({ filter }, ctx);
    return c.json({ reminders: list });
  })

  // POST /reminders — create from the manual form
  .post("/", async (c) => {
    const deviceId = getDeviceId(c);
    const body = await c.req.json().catch(() => null);
    if (!body) return c.json({ error: "Invalid JSON body" }, 400);
    const ctx = buildCtx(c, deviceId);
    try {
      const result = await handleCreateReminder(body, ctx);
      return c.json(result, 201);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  })

  // PATCH /reminders/:id — update body or time
  .patch("/:id", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const body = await c.req.json().catch(() => null);
    if (!body) return c.json({ error: "Invalid JSON body" }, 400);
    const ctx = buildCtx(c, deviceId);
    try {
      const result = await handleUpdateReminder({ reminder_id: id, ...body }, ctx);
      if (!result.updated) return c.json({ error: "Reminder not found" }, 404);
      return c.json(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  })

  // DELETE /reminders/:id
  .delete("/:id", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const ctx = buildCtx(c, deviceId);
    const result = await handleDeleteReminder({ reminder_id: id }, ctx);
    if (!result.deleted) return c.json({ error: "Reminder not found" }, 404);
    return c.json(result);
  })

  // POST /reminders/:id/pause
  .post("/:id/pause", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const ctx = buildCtx(c, deviceId);
    const result = await handlePauseReminder({ reminder_id: id }, ctx);
    if (!result.paused) return c.json({ error: "Reminder not found" }, 404);
    return c.json(result);
  })

  // POST /reminders/:id/resume
  .post("/:id/resume", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const ctx = buildCtx(c, deviceId);
    const result = await handleResumeReminder({ reminder_id: id }, ctx);
    if (!result.resumed) return c.json({ error: "Reminder not found" }, 404);
    return c.json(result);
  })

  // POST /reminders/:id/snooze — body: { minutes: 10 }
  .post("/:id/snooze", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const body = await c.req.json().catch(() => null);
    const minutes = (body as Record<string, unknown> | null)?.minutes;
    const ctx = buildCtx(c, deviceId);
    try {
      const result = await handleSnoozeReminder(
        { reminder_id: id, minutes: typeof minutes === "number" ? minutes : 10 },
        ctx,
      );
      if (!result.snoozed) return c.json({ error: "Reminder not found" }, 404);
      return c.json(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  })

  // POST /reminders/:id/done — mark a reminder as dismissed
  .post("/:id/done", async (c) => {
    const deviceId = getDeviceId(c);
    const id = c.req.param("id");
    const ctx = buildCtx(c, deviceId);
    try {
      const { updateReminderFields } = await import("../db/queries");
      await updateReminderFields(ctx.db, id, deviceId, {
        status: "dismissed",
      });
      return c.json({ ok: true, id });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  });
