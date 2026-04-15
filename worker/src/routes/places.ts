// REST endpoint for place resolution — used by the nickname edit screen
// to search for places without going through the LLM.

import { Hono } from "hono";
import type { Env } from "../index";
import { getDeviceId, touchDevice } from "../lib/auth";
import { handleResolvePlace } from "../tools/places";

export const places = new Hono<{ Bindings: Env }>()
  .use("*", async (c, next) => {
    const deviceId = getDeviceId(c);
    await touchDevice(c.env.DB, deviceId);
    await next();
  })

  // POST /resolve-place — body: { query, bias_lat?, bias_lng? }
  .post("/", async (c) => {
    const body = await c.req.json().catch(() => null);
    if (!body) return c.json({ error: "Invalid JSON body" }, 400);
    try {
      const results = await handleResolvePlace(body);
      return c.json({ places: results });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return c.json({ error: msg }, 400);
    }
  });
