// REST handlers for nickname management (also reachable via the agent's tools).
// SPEC §10 — nickname CRUD for the in-app nicknames screen.

import { Hono } from "hono";
import type { Env } from "../index";
import { getDeviceId, touchDevice } from "../lib/auth";
import type { NicknameHandlerContext } from "../tools/nicknames";
import {
  handleSaveNickname,
  handleGetNicknames,
  handleDeleteNickname,
} from "../tools/nicknames";

function buildNicknameCtx(c: { env: { DB: D1Database } }, deviceId: string): NicknameHandlerContext {
  return { db: c.env.DB, deviceId };
}

export const nicknames = new Hono<{ Bindings: Env }>()
  .use("*", async (c, next) => {
    const deviceId = getDeviceId(c);
    await touchDevice(c.env.DB, deviceId);
    c.set("deviceId" as never, deviceId as never);
    await next();
  })

  // GET /nicknames — list all nicknames for this device
  .get("/", async (c) => {
    const deviceId = getDeviceId(c);
    const ctx = buildNicknameCtx(c, deviceId);
    const result = await handleGetNicknames({}, ctx);
    return c.json(result);
  })

  // POST /nicknames — create or update a nickname (UPSERT)
  .post("/", async (c) => {
    const deviceId = getDeviceId(c);
    const body = await c.req.json().catch(() => null);
    if (!body) return c.json({ error: "Invalid JSON body" }, 400);
    const ctx = buildNicknameCtx(c, deviceId);
    try {
      const result = await handleSaveNickname(body, ctx);
      return c.json(result, 201);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  })

  // DELETE /nicknames/:nickname — delete by nickname text
  .delete("/:nickname", async (c) => {
    const deviceId = getDeviceId(c);
    const nickname = decodeURIComponent(c.req.param("nickname"));
    const ctx = buildNicknameCtx(c, deviceId);
    const result = await handleDeleteNickname({ nickname }, ctx);
    if (!result.deleted) return c.json({ error: "Nickname not found" }, 404);
    return c.json(result);
  });
