// Recall Worker — entry point.
//
// Routes:
//   GET  /health         → liveness probe
//   POST /chat           → forwards to the per-device RecallAgent (Durable Object)
//   /reminders/*         → REST handlers for the manual form path
//   /nicknames/*         → REST handlers for nickname management
//
// Auth: every request must carry an `X-Recall-Device` header (anonymous
// per-install UUID generated client-side). See SPEC §1 "Network Bridge".

import { Hono } from "hono";
import { getAgentByName, type AgentNamespace } from "agents";
import { health } from "./routes/health";
import { reminders } from "./routes/reminders";
import { nicknames } from "./routes/nicknames";
import { places } from "./routes/places";
import { getDeviceId, touchDevice, MissingDeviceError } from "./lib/auth";
import { RecallAgent } from "./agent";

export { RecallAgent };

export interface Env {
  DB: D1Database;
  RECALL_AGENT: AgentNamespace<RecallAgent>;
  AI: Ai;
  GOOGLE_PLACES_API_KEY?: string;
  ENVIRONMENT: string;
}

const app = new Hono<{ Bindings: Env }>();

app.onError((err, c) => {
  if (err instanceof MissingDeviceError) {
    return c.json({ error: err.message }, 401);
  }
  console.error("Unhandled error", err);
  return c.json({ error: "Internal error" }, 500);
});

app.route("/health", health);
app.route("/reminders", reminders);
app.route("/nicknames", nicknames);
app.route("/resolve-place", places);

// /chat — the only endpoint that runs the agent.
// We forward the raw request to the per-device Durable Object so it can
// hold conversation state across turns.
app.post("/chat", async (c) => {
  const deviceId = getDeviceId(c);
  await touchDevice(c.env.DB, deviceId);

  const stub = await getAgentByName<Env, RecallAgent>(c.env.RECALL_AGENT, deviceId);
  return stub.fetch(c.req.raw);
});

app.get("/", (c) =>
  c.json({
    service: "recall-worker",
    docs: "see SPEC.md §1 and §14",
    endpoints: ["/health", "/chat", "/reminders", "/nicknames", "/resolve-place"],
  }),
);

export default app;
