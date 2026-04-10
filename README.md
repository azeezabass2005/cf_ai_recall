# Ranti

A voice-first reminder app that lives in the background of your phone. You talk to it the way you'd talk to a person — no rigid commands — and it figures out the rest.

For the full product story see [`USER_STORY.md`](./USER_STORY.md). For the technical spec see [`SPEC.md`](./SPEC.md). For the visual language see [`DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md).

---

## Repository Layout

```
ranti/
├── app/        ← Android client (Kotlin + Jetpack Compose)
├── worker/     ← Backend agent (TypeScript on Cloudflare Workers, Agents SDK)
├── SPEC.md
├── USER_STORY.md
└── DESIGN_SYSTEM.md
```

The Android app is intentionally thin: voice/text input → HTTPS to the Worker → response. All language understanding lives in the Worker as an LLM agent that reaches the world through tool calls. On-device responsibilities are limited to wake word, speech recognition, alarms, geofences, notifications, and TTS — see `SPEC.md` §1 for the full split.

## Status

This repo currently has the **basic scaffold for SPEC §1, §2, §3** in place — the architecture is wired up end to end and both the Worker and the Android project boot, but feature milestones (§4 onwards) are not started. See the "Build Status" table at the top of `SPEC.md`.

What works today:
- `worker/` — Hono router, `RantiAgent` Durable Object stub, tool stubs, REST stubs, D1 schema, anonymous device auth. Boots locally with `wrangler dev` and responds to `/health`, `/chat`, `/reminders`, `/nicknames`.
- `app/` — Compose Android project with a single `ChatScreen` that posts to the Worker and renders the reply. Mirrors the file layout described in SPEC §3.

What does **not** work yet:
- Wake word, speech recognition, real reminder creation, location resolution, alarms, geofences, notifications — all gated behind their respective milestones.
- The agent is currently an echo. The real Anthropic tool-use loop lands in milestone §5.

---

## Backend — `worker/`

### Prerequisites
- Node 20+
- A Cloudflare account if you want to deploy (local dev works without one)

### Local development

```sh
cd worker
npm install
npx wrangler d1 migrations apply ranti-db --local   # one-time, sets up local D1
npm run dev                                          # starts wrangler dev on http://localhost:8787
```

Smoke test:

```sh
# Health check
curl http://localhost:8787/health

# Chat with the agent (echo for now)
curl -X POST http://localhost:8787/chat \
  -H "Content-Type: application/json" \
  -H "X-Ranti-Device: dev-device-123" \
  -d '{"message":"remind me to call mum at 7pm"}'
```

Every request must carry an `X-Ranti-Device` header — this is the anonymous per-install UUID that the Android app generates on first launch and persists in DataStore.

### Deploying to Cloudflare

1. `npx wrangler login`
2. `npx wrangler d1 create ranti-db` and copy the `database_id` into `wrangler.toml`
3. `npm run db:migrate:remote`
4. `npx wrangler secret put ANTHROPIC_API_KEY`
5. `npx wrangler secret put GOOGLE_PLACES_API_KEY`
6. `npm run deploy`

---

## Frontend — `app/`

### Prerequisites
- Android Studio Koala or newer
- Android SDK 35
- JDK 17

### Setup

1. Open the repository root in Android Studio (it'll pick up `settings.gradle.kts`).
2. Let Gradle sync. The first run will download the wrapper, AGP, Compose, Ktor, and friends.
3. Set the Worker URL:
   - **Emulator + local `wrangler dev`**: the default `BuildConfig.RANTI_BASE_URL` is `http://10.0.2.2:8789`, which is how the emulator reaches the host machine. Run the worker with `npx wrangler dev --port 8789`.
   - **Physical device + LAN dev**: edit `app/build.gradle.kts` and replace the debug `RANTI_BASE_URL` with your machine's LAN IP (e.g. `http://192.168.1.42:8789`).
   - **Production**: edit the `release` build type to point at your deployed Worker.
4. Run the `app` configuration on an emulator or device.

You should land on the Chat screen with Ranti's welcome bubble. Type a message → tap **Send** → the Worker echoes it back.

### What's in the scaffold
- `MainActivity` + `RantiApp` + Compose theme
- Navigation graph with `Routes` constants for all 17 screens (only `chat/index` is implemented)
- `RantiApi` Ktor client with `X-Ranti-Device` header injection
- `DeviceId` DataStore-backed UUID generator

---

## How the two sides talk

```
┌─────────────────┐         HTTPS          ┌──────────────────────┐
│  Android (app/) │ ───────────────────▶  │  Cloudflare Worker   │
│                 │  X-Ranti-Device: <id>  │  (worker/)           │
│  ChatScreen ───▶│                        │   POST /chat ───────▶│
│  RantiApi       │                        │     RantiAgent (DO)  │
└─────────────────┘                        │       LLM + tools    │
                                           │       D1             │
                                           └──────────────────────┘
```

The wire contract is plain JSON. There is no native code, no JNI, no protobuf — just typed DTOs on each side that we keep in sync by hand for now. When we eventually port the backend to Rust, the Android app won't need to change a single line.

## Next milestones

The next thing to land is **§4 — Onboarding & Permissions** (Android) and **§5 — Conversation Interface (Chat)** (real LLM tool-use loop in the Worker, replacing the echo). After that, time-based reminders and notifications (§7 + §12) are the shortest path to "Ranti can actually remind you of something."

## License

Not yet decided.
