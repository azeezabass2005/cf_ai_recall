// REST handlers for nickname management (also reachable via the agent's tools).
// Stubs for the scaffold — wired up in milestone §10.

import { Hono } from "hono";
import type { Env } from "../index";
import { getDeviceId, touchDevice } from "../lib/auth";

export const nicknames = new Hono<{ Bindings: Env }>()
  .use("*", async (c, next) => {
    const deviceId = getDeviceId(c);
    await touchDevice(c.env.DB, deviceId);
    c.set("deviceId" as never, deviceId as never);
    await next();
  })
  .get("/", (c) => c.json({ nicknames: [], note: "stub — milestone §10" }))
  .post("/", (c) => c.json({ error: "not implemented (milestone §10)" }, 501))
  .delete("/:id", (c) => c.json({ error: "not implemented (milestone §10)" }, 501));
