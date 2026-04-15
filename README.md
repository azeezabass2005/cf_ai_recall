# Recall

A voice-first reminder app for Android. You talk to it like you'd talk to a person — "remind me to call mum at 7pm tomorrow", "ping me when I get to the office" — and it handles the rest.

- [`USER_STORY.md`](./USER_STORY.md) — what the app feels like to use
- [`SPEC.md`](./SPEC.md) — technical spec and milestone status
- [`DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) — colors, typography, components
- [`SETUP_GUIDE.md`](./SETUP_GUIDE.md) — longer guide for running on a physical phone

## Layout

```
recall/
├── app/        Android client (Kotlin, Jetpack Compose)
└── worker/     Backend (TypeScript, Cloudflare Workers + Agents SDK)
```

The Android app is thin: voice/text input → HTTPS to the Worker → response. Language understanding lives in the Worker as an LLM agent that calls tools (create reminder, resolve place, save nickname, etc.). On-device code handles speech recognition, wake word, alarms, geofences, notifications, and TTS.

## Requirements

**Backend (`worker/`)**
- Node.js 20+
- A Cloudflare account (only needed to deploy; local dev works without one)

**Android (`app/`)**
- Android Studio Koala (2024.1.1) or newer
- Android SDK 35
- JDK 17
- A device or emulator running Android 10+ (API 29)

## Run the backend locally

```sh
cd worker
npm install
npm run db:migrate:local
npm run dev -- --port 8789
```

The Worker comes up on `http://localhost:8789`. Quick check:

```sh
curl http://localhost:8789/health
```

Every request (except `/health` and `/`) needs an `X-Recall-Device` header — the Android app generates this UUID on first launch. For manual testing:

```sh
curl -X POST http://localhost:8789/chat \
  -H "Content-Type: application/json" \
  -H "X-Recall-Device: dev-device-123" \
  -d '{"message":"remind me to call mum at 7pm"}'
```

The agent runs on Cloudflare Workers AI (Llama 3.3) via the `AI` binding — no API key needed for local dev, Wrangler stubs it automatically. The only optional key is `GOOGLE_PLACES_API_KEY`, which you only need if you want to test location-based reminders. Drop it in `worker/.dev.vars` (git-ignored):

```
GOOGLE_PLACES_API_KEY=...
```

## Run the Android app

1. Open the repo root (`recall/`, **not** `recall/app/`) in Android Studio.
2. Wait for Gradle sync to finish. First sync downloads the wrapper, AGP, Compose, Ktor, and friends — give it a few minutes.
3. Point the app at your Worker:
    - **Emulator + local Worker:** nothing to change. The default `RECALL_BASE_URL` is `http://10.0.2.2:8789`, which is how the Android emulator reaches your host machine.
    - **Physical device on same wifi:** edit the `debug` block in `app/build.gradle.kts` and replace `10.0.2.2` with your laptop's LAN IP (e.g. `http://192.168.1.42:8789`). Start the Worker with `npm run dev -- --port 8789 --ip 0.0.0.0` so it binds to all interfaces.
    - **Deployed Worker:** edit the `release` block in `app/build.gradle.kts` and put in your `https://recall-worker.<subdomain>.workers.dev` URL.
4. Pick a device in the toolbar dropdown and hit **Run** (▶).

First launch walks through the onboarding flow (welcome → permissions → wake word opt-in → ready). Grant microphone, notifications, and location when prompted, then you land on the chat screen.

If the Run button is greyed out, Gradle sync hasn't finished yet — check the bottom status bar. If sync fails, the usual culprits are missing SDK 35 (`Tools → SDK Manager`) or the wrong Gradle JDK (`Settings → Build → Gradle → Gradle JDK` → pick a 17).

## Deploy the Worker to Cloudflare

```sh
cd worker
npx wrangler login
npx wrangler d1 create recall-db
# Copy the database_id into wrangler.toml
npm run db:migrate:remote
npx wrangler secret put GOOGLE_PLACES_API_KEY   # optional, for location reminders
npm run deploy
```

Wrangler prints the deployed URL. Drop it into the `release` block of `app/build.gradle.kts`, then build a release APK with `./gradlew assembleRelease`. The APK lands in `app/build/outputs/apk/release/`.

See [`SETUP_GUIDE.md`](./SETUP_GUIDE.md) for the longer version, including installing the release APK on a phone.

## How the two sides talk

```
┌─────────────────┐        HTTPS         ┌──────────────────────┐
│  Android app    │ ───────────────────▶ │  Cloudflare Worker   │
│                 │  X-Recall-Device: id  │                      │
│  Chat / Voice   │                      │    POST /chat        │
│  Reminders UI   │                      │      ↓               │
│  Alarms         │                      │    RecallAgent (DO)   │
│  Geofences      │                      │      ↓               │
│                 │                      │    Anthropic + tools │
│                 │                      │      ↓               │
│                 │                      │    D1 (SQLite)       │
└─────────────────┘                      └──────────────────────┘
```

Wire contract is plain JSON — typed DTOs on each side kept in sync by hand. No JNI, no protobuf.

## Status

§1–§13 are implemented. The app can take a voice or text message, create time-based and location-based reminders, fire notifications with Done and Snooze actions, manage reminders and nicknames in-app, and survive reboots. See the "Build Status" table in `SPEC.md` for the per-milestone breakdown.

## License

Not yet decided.
