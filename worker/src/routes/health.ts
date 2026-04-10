import { Hono } from "hono";
import type { Env } from "../index";

export const health = new Hono<{ Bindings: Env }>().get("/", (c) =>
  c.json({ ok: true, service: "ranti-worker", time: new Date().toISOString() }),
);
