# Recall — App Specification

**Version 1.1 | April 2026 | Android (Kotlin UI) + Cloudflare Workers (TypeScript Agent)**

> This document is the implementation spec for the MVP. For the user stories, see `USER_STORY.md`. For the design system, see `DESIGN_SYSTEM.md`.

> **v1.1 note:** Originally specced with a Rust core compiled via NDK. Switched to a TypeScript backend on Cloudflare Workers using Cloudflare's Agents SDK so we can ship the MVP faster — Cloudflare ships TS bindings, not Rust ones. The data models in §15 are kept language-agnostic so the agent can be ported to Rust later without touching the Android client.

| Field           | Value                                    |
| --------------- | ---------------------------------------- |
| Platform        | Android (MVP), iOS (post-MVP)            |
| UI Framework    | Jetpack Compose (Material3)              |
| Navigation      | Jetpack Navigation Compose               |
| Agent / Backend | TypeScript on Cloudflare Workers, Cloudflare Agents SDK (`agents`) |
| Agent State     | Durable Object (one per user) — holds conversation context |
| Persistent Storage | Cloudflare D1 (SQLite at the edge) — reminders, nicknames, messages |
| Local Cache     | Android DataStore (preferences) + Room (cached reminders for offline read) |
| NLP / Intent    | LLM-driven agent with tool calls (no rule-based parser) |
| Voice           | Android SpeechRecognizer API             |
| Wake Word       | Vosk (grammar-mode keyword spotting)     |
| Location        | Google Places API (called from the Worker) + Android Geofencing API (on device) |
| Notifications   | Android AlarmManager + NotificationManager |
| TTS             | Android TextToSpeech API                 |
| Auth (MVP)      | Anonymous device-bound token (`X-Recall-Device` header) |
| Design System   | See `DESIGN_SYSTEM.md`                   |

---

## Build Status

| Section | Status |
|---------|--------|
| §1 Architecture Overview | ✅ Done — scaffolded in `worker/` and `app/` |
| §2 Navigation Architecture | ✅ Done — `NavGraph.kt` + `Routes.kt` in place |
| §3 File Structure & Screen Map | ✅ Done — directory layout matches spec |
| §4 Milestone 1 — Onboarding & Permissions | ✅ Done — 4 screens, runtime permission flow, first-launch routing |
| §5 Milestone 2 — Conversation Interface (Chat) | ✅ Done — ChatScreen, ChatBubble, ChatViewModel, text/voice input modes, typing indicator |
| §6 Milestone 3 — Voice Input & Wake Word | ✅ Done — SpeechRecognizerManager, TextToSpeechManager, WakeWordService + VoskWakeWordEngine (real "Hi Recall" detection), BootReceiver, voice mode wired into ChatViewModel |
| §7 Milestone 4 — Time-Based Reminders | ✅ Done — Cloudflare Agent tool-use loop with create/list/delete/update/pause/resume/resolve_time tools, timezone-aware time + recurrence parser (`worker/src/lib/time.ts`), D1-backed reminder storage, Android ReminderScheduler + ReminderReceiver with AlarmManager.setExactAndAllowWhileIdle and local recurrence rescheduling |
| §8 Milestone 5 — Location-Based Reminders | ✅ Done — Google Places Text Search (New) API in `places.ts`, `create_reminder` accepts lat/lng/place_name/place_id, Android GeofenceManager + GeofenceBroadcastReceiver + GeofencePrefs + LocationHelper, play-services-location wired, ChatViewModel registers geofences for location triggers |
| §9 Milestone 6 — Location Disambiguation | ✅ Done — Agent `resolve_place` tool returns 0–3 results with Haversine 50m compaction, `disambiguation` field in chat response, `DisambiguationSheet` ModalBottomSheet in ChatScreen, user pick sent back as chat message |
| §10 Milestone 7 — Location Nicknames | ✅ Done — D1-backed `save_nickname`/`get_nicknames`/`delete_nickname` handlers with UPSERT, REST `/nicknames` CRUD routes, agent system prompt with nickname flow, NicknamesScreen (list + delete), NicknameEditScreen (create with place search via `/resolve-place`), NavGraph wired |
| §11 Milestone 8 — Reminder Management | ✅ Done — Worker REST endpoints live, RecallApi CRUD methods, RemindersScreen (3 tabs, swipe-to-delete, pull-to-refresh), ReminderDetailScreen, ReminderFormScreen (create + edit), ReminderCard component, RemindersViewModel |
| §12 Milestone 9 — Notification & Firing | ✅ Done — Done + Snooze 10 min action buttons, ReminderActionReceiver, upgraded notification channel with alarm audio attributes and vibration |
| §13 Settings & Preferences | ✅ Done — SettingsScreen, WakeWordSettingsScreen, VoiceSettingsScreen, NotificationSettingsScreen, LocationSettingsScreen, AboutScreen; theme pref wired to RecallTheme via DataStore; all routes wired in NavGraph |
| §14 Worker / Agent Architecture | ✅ Done — TypeScript architecture documented (replaces Rust section); module table, agent-loop state machine, HTTP API table, D1 schema overview |
| §15 Data Models | ✅ Done — TypeScript types in `worker/src/lib/types.ts`, typed D1 query helpers in `worker/src/db/queries.ts`, Kotlin DTOs in `app/.../network/ApiModels.kt` |
| §16 Agent Tools & Intent Resolution | ✅ Done — 10-tool inventory, intent-to-tool mapping, system prompt rules, fuzzy matching |
| §17 Android-Specific Behaviors | ✅ Done — Offline behavior, data privacy, and reboot handling updated to reflect Cloudflare/D1 architecture |
| §18 Third-Party SDK Integration | ✅ Done — Removed abandoned Rust/NDK toolchain; Google Places corrected to REST API via Cloudflare Worker |
| §19 Screen Inventory | ✅ Done — 17 routes verified against `Routes.kt`; composable names and background service class names corrected |

## Table of Contents

1. [Architecture Overview](#1-architecture-overview) ✅
2. [Navigation Architecture](#2-navigation-architecture) ✅
3. [File Structure & Screen Map](#3-file-structure--screen-map) ✅
4. [Milestone 1 — Onboarding & Permissions](#4-milestone-1--onboarding--permissions) ✅
5. [Milestone 2 — Conversation Interface (Chat)](#5-milestone-2--conversation-interface-chat) ✅
6. [Milestone 3 — Voice Input & Wake Word](#6-milestone-3--voice-input--wake-word) ✅
7. [Milestone 4 — Time-Based Reminders](#7-milestone-4--time-based-reminders) ✅
8. [Milestone 5 — Location-Based Reminders](#8-milestone-5--location-based-reminders) ✅
9. [Milestone 6 — Location Disambiguation](#9-milestone-6--location-disambiguation) ✅
10. [Milestone 7 — Location Nicknames](#10-milestone-7--location-nicknames) ✅
11. [Milestone 8 — Reminder Management](#11-milestone-8--reminder-management) ✅
12. [Milestone 9 — Notification & Firing](#12-milestone-9--notification--firing) ✅
13. [Milestone 10 — Settings & Preferences](#13-milestone-10--settings--preferences) ✅
14. [Worker / Agent Architecture](#14-worker--agent-architecture) ✅
15. [Data Models](#15-data-models) ✅
16. [Agent Tools & Intent Resolution](#16-agent-tools--intent-resolution) ✅
17. [Android-Specific Behaviors](#17-android-specific-behaviors) ✅
18. [Third-Party SDK Integration](#18-third-party-sdk-integration) ✅
19. [Screen Inventory (Complete)](#19-screen-inventory-complete) ✅

---

## 1. Architecture Overview

Recall uses a **thin client, agent backend** architecture. The Android (Kotlin/Compose) app handles UI, platform APIs (microphone, GPS, alarms, geofences, notifications), and lifecycle. All language understanding and reminder orchestration lives in a **TypeScript agent on Cloudflare Workers**, built with the Cloudflare Agents SDK. The agent is LLM-driven — it interprets natural language by *calling tools*, not by running a hand-rolled regex parser.

```
┌─────────────────────────────────────────────┐
│        Android — Kotlin / Compose UI        │
│  ┌───────┐ ┌───────┐ ┌────────┐ ┌────────┐ │
│  │ Chat  │ │ Voice │ │ Remind │ │Settings│ │
│  │Screen │ │ Input │ │  List  │ │ Screens│ │
│  └───┬───┘ └───┬───┘ └───┬────┘ └───┬────┘ │
│      │         │         │          │      │
│  ┌───┴─────────┴─────────┴──────────┴───┐  │
│  │   RecallApi (HTTP client + cache)    │  │
│  └─────────────────┬────────────────────┘  │
│                    │                        │
│  ┌─────────────────┴────────────────────┐  │
│  │  On-device only (no agent involved): │  │
│  │  WakeWordService · SpeechRecognizer  │  │
│  │  AlarmManager · GeofencingClient     │  │
│  │  NotificationHelper · TextToSpeech   │  │
│  └──────────────────────────────────────┘  │
└────────────────────┬────────────────────────┘
                     │ HTTPS (JSON)
                     │ X-Recall-Device: <uuid>
                     ▼
┌─────────────────────────────────────────────┐
│       Cloudflare Worker — TypeScript        │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  Router (src/index.ts)                │  │
│  │  • POST /chat        → Agent          │  │
│  │  • POST /reminders   → REST (form)    │  │
│  │  • GET  /reminders   → REST (list)    │  │
│  │  • PATCH/DELETE /reminders/:id        │  │
│  │  • GET/POST/DELETE /nicknames         │  │
│  └───────────────┬───────────────────────┘  │
│                  │                          │
│  ┌───────────────┴───────────────────────┐  │
│  │  RecallAgent (Durable Object)          │  │
│  │  one instance per device id           │  │
│  │  • holds conversation context         │  │
│  │  • runs LLM with tool use loop        │  │
│  │  ┌─────────────────────────────────┐  │  │
│  │  │ Tools                           │  │  │
│  │  │  create_reminder                │  │  │
│  │  │  update_reminder                │  │  │
│  │  │  delete_reminder                │  │  │
│  │  │  list_reminders                 │  │  │
│  │  │  pause_reminder                 │  │  │
│  │  │  resume_reminder                │  │  │
│  │  │  resolve_place                  │  │  │
│  │  │  save_nickname                  │  │  │
│  │  │  get_nicknames                  │  │  │
│  │  └─────────────────────────────────┘  │  │
│  └───────────────┬───────────────────────┘  │
│                  │                          │
│  ┌───────────────┴───────────────────────┐  │
│  │  D1 (SQLite) — reminders, nicknames,  │  │
│  │  messages, devices                    │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  External: Anthropic API · Google Places    │
└─────────────────────────────────────────────┘
```

### Why Cloudflare Agents (TypeScript)

1. **MVP velocity.** Cloudflare ships first-class TS bindings for Workers, Durable Objects, D1, and the Agents SDK. We can be on the air in days, not weeks.
2. **Tool-calling primitive.** The Agents SDK gives us the LLM tool-use loop for free, so we never write a regex parser.
3. **Per-user state via Durable Objects.** Each device's conversation context lives in a single-threaded DO instance — no race conditions on multi-turn flows like disambiguation or nickname capture.
4. **Future Rust port stays cheap.** The wire contract between Android and the Worker is plain JSON over HTTPS. A future `worker-rs` rewrite swaps the implementation without touching the client.

### Why a tool-calling agent (and not the v1.0 regex parser)

The original spec used a deterministic regex pipeline with an LLM as a *fallback*. v1.1 inverts that: the LLM is the primary path, and tools are the only way it can affect the world. Reasons:
- Recall's surface area is small (~9 verbs) — perfect for a tool-use loop.
- Multi-turn flows (disambiguation, nickname capture, "cancel the Shoprite reminder") collapse into one consistent mechanism instead of a hand-coded state machine.
- Adding a new capability later means adding a new tool, not editing the parser.

### Network Bridge

The Android app talks to the Worker over plain HTTPS with a JSON body. There is no JNI, no native library, no FFI. Identity is a per-install UUID sent in the `X-Recall-Device` header — generated on first launch, persisted in DataStore, never collected anywhere else.

```kotlin
// Kotlin side — see app/src/main/java/com/recall/network/RecallApi.kt
interface RecallApi {
    suspend fun chat(message: ChatRequest): ChatResponse                // POST /chat
    suspend fun listReminders(filter: String): List<Reminder>           // GET  /reminders?filter=
    suspend fun createReminder(form: ReminderForm): Reminder            // POST /reminders   (manual form)
    suspend fun updateReminder(id: String, form: ReminderForm): Reminder// PATCH /reminders/:id
    suspend fun deleteReminder(id: String): Boolean                     // DELETE /reminders/:id
    suspend fun pauseReminder(id: String): Reminder                     // POST /reminders/:id/pause
    suspend fun resumeReminder(id: String): Reminder                    // POST /reminders/:id/resume
    suspend fun listNicknames(): List<Nickname>                         // GET /nicknames
    suspend fun saveNickname(req: NicknameRequest): Nickname            // POST /nicknames
    suspend fun deleteNickname(id: String): Boolean                     // DELETE /nicknames/:id
}
```

The chat endpoint is the *only* endpoint that runs the agent. All other endpoints are plain REST handlers — they exist so the manual form (§11.3) and the reminder list (§11.1) don't waste tokens going through the LLM for already-structured input.

### What stays on-device, and why

These cannot move to the Worker — they need to keep working when the screen is off, the app is killed, or there's no network:

| On-device | Why |
|-----------|-----|
| Wake word (Vosk) | Continuous mic + low-latency, must work offline |
| Speech recognition | Android `SpeechRecognizer` is local on most modern devices |
| Alarm scheduling | `AlarmManager.setExactAndAllowWhileIdle()` is the only way to fire on time during Doze |
| Geofencing | Google Play Services geofences fire even when the app is dead |
| Notifications | Have to be local — there's no notification once the alarm has nothing to talk to |
| TTS | Latency, offline support |

When the Worker creates or updates a time/location reminder, the response includes the data the Android side needs to register a local alarm or geofence. The Worker is the system of record; the device is the system of *firing*.

---

## 2. Navigation Architecture

The app uses **Jetpack Navigation Compose** with a simple navigation graph. There are no bottom tabs — Recall's primary interface is the conversation screen, but the app is fully usable without ever speaking a word. Voice is the *fastest* way in; the screens are the *complete* way in.

```
NavGraph
  ├── onboarding/          ← First-time setup (permissions, wake word training)
  │     welcome
  │     permissions
  │     wake-word-setup
  │     ready
  │
  ├── chat/                ← Primary screen (conversation + voice)
  │     index              ← Main chat/conversation screen
  │
  ├── reminders/           ← Reminder list & management
  │     index              ← All reminders (active + history + recurring)
  │     detail             ← Single reminder detail
  │     new                ← Manual reminder builder (form)
  │     edit               ← Edit existing reminder via form
  │
  ├── nicknames/           ← Location nickname management
  │     index              ← Saved nicknames list
  │     edit               ← Edit/delete a nickname
  │
  └── settings/            ← App preferences
        index              ← Settings menu
        wake-word          ← Wake word toggle + sensitivity
        notifications      ← Notification preferences
        location           ← Location/geofence settings
        voice              ← Voice & TTS preferences
        about              ← App version, licenses
```

### Navigation Rules

- **First launch** → `onboarding/welcome` → linear flow → `chat/index`
- **Subsequent launches** → `chat/index` directly
- **Wake word activation** → brings `chat/index` to foreground in listening state
- **Notification tap** → opens `chat/index` with the fired reminder visible, or `reminders/detail` if from the notification action
- **Back from any screen** → returns to `chat/index` (chat is always the root)
- **Manual reminder creation** → reachable from a `+` FAB on both `chat/index` and `reminders/index`, navigates to `reminders/new`

---

## 3. File Structure & Screen Map

### Kotlin / Android

```
app/src/main/
  java/com/recall/
    RecallApp.kt                       ← Application class, DI setup
    MainActivity.kt                   ← Single activity, hosts Compose NavHost
    
    navigation/
      NavGraph.kt                     ← Navigation graph definition
      Routes.kt                       ← Route constants
    
    ui/
      theme/                          ← Design system (see DESIGN_SYSTEM.md)
        Color.kt
        Type.kt
        Theme.kt
        Spacing.kt
        Shape.kt
      
      components/                     ← Reusable UI components
        Text.kt
        Button.kt
        Card.kt
        Badge.kt
        Avatar.kt
        Input.kt
        ChatBubble.kt
        VoiceOrb.kt
        ReminderCard.kt
        TimePill.kt
        LocationPill.kt
        RecurrencePill.kt              ← Shows recurrence summary ("Every Tue · 6:50 PM")
        DisambiguationSheet.kt
        CountdownRing.kt
        Toggle.kt
        Divider.kt
        Toast.kt
        DatePicker.kt                  ← Wraps Material3 date picker, themed
        TimePicker.kt                  ← Wraps Material3 time picker, themed
        WeekdayPicker.kt               ← Multi-select chip row for weekdays
      
      screens/
        onboarding/
          WelcomeScreen.kt
          PermissionsScreen.kt
          WakeWordSetupScreen.kt
          ReadyScreen.kt
        
        chat/
          ChatScreen.kt               ← Primary conversation interface
          ChatViewModel.kt
        
        reminders/
          RemindersScreen.kt           ← Reminder list (active, recurring, history)
          ReminderDetailScreen.kt
          ReminderFormScreen.kt        ← Manual builder (used for both new and edit)
          ReminderFormViewModel.kt
          RemindersViewModel.kt
        
        nicknames/
          NicknamesScreen.kt
          NicknameEditScreen.kt
          NicknamesViewModel.kt
        
        settings/
          SettingsScreen.kt
          WakeWordSettingsScreen.kt
          NotificationSettingsScreen.kt
          LocationSettingsScreen.kt
          VoiceSettingsScreen.kt
          AboutScreen.kt
    
    service/
      WakeWordService.kt              ← Foreground service for wake word detection
      GeofenceService.kt              ← Geofence monitoring service
      ReminderScheduler.kt            ← AlarmManager wrapper
      NotificationHelper.kt           ← Notification creation
    
    network/
      RecallApi.kt                      ← Retrofit/Ktor HTTP client to the Worker
      ApiModels.kt                     ← DTOs (request/response shapes for /chat, /reminders, /nicknames)
      DeviceId.kt                      ← Generates + persists per-install UUID for X-Recall-Device header

    data/
      ReminderRepository.kt            ← Wraps RecallApi + Room cache, single source of truth for the UI
      NicknameRepository.kt
      LocalCache.kt                    ← Room database for offline reads
    
    voice/
      SpeechRecognizerManager.kt       ← Android SpeechRecognizer wrapper
      TextToSpeechManager.kt           ← Android TTS wrapper
    
    location/
      LocationManager.kt               ← Fused Location Provider wrapper
      GeofenceManager.kt               ← Geofence registration
      PlacesManager.kt                 ← Google Places API wrapper
```

### Worker — TypeScript / Cloudflare

```
worker/
  package.json                         ← deps: agents, @anthropic-ai/sdk, hono, zod
  wrangler.toml                        ← Worker name, D1 binding, DO binding, secrets
  tsconfig.json
  worker-configuration.d.ts            ← Generated by `wrangler types`
  README.md                            ← Local dev + deploy instructions

  src/
    index.ts                           ← Hono router; mounts /chat (agent) and REST handlers

    agent.ts                           ← RecallAgent class — extends Agent from `agents` SDK
                                          Holds conversation state, runs LLM tool-use loop.

    tools/
      index.ts                         ← Tool registry export
      reminders.ts                     ← create_reminder / update_reminder / delete_reminder /
                                          list_reminders / pause_reminder / resume_reminder
      places.ts                        ← resolve_place (calls Google Places from the Worker)
      nicknames.ts                     ← save_nickname / get_nicknames

    routes/
      reminders.ts                     ← REST handlers for the manual form path (no LLM)
      nicknames.ts                     ← REST handlers for nickname management
      health.ts                        ← GET /health for uptime checks

    db/
      schema.sql                       ← D1 schema (matches §15.5)
      migrations/
        0001_init.sql
      queries.ts                       ← Typed query helpers over the D1 binding

    lib/
      types.ts                         ← Shared TS types (Reminder, Nickname, RecurrenceRule, …)
      time.ts                          ← Recurrence rule evaluator (computes next occurrence)
      auth.ts                          ← X-Recall-Device extraction + device row upsert
      llm.ts                           ← Anthropic client wrapper (model selection, retries)
      errors.ts                        ← Typed error responses

  test/
    agent.test.ts
    tools.test.ts
```

The Worker has no on-device counterpart — it is the entire backend. The Android app does not bundle any business logic. A future Rust port replaces `worker/` wholesale; nothing else has to change.

---

## 4. Milestone 1 — Onboarding & Permissions

> **Status: ✅ Done.** All four screens implemented in `app/src/main/java/com/recall/ui/screens/onboarding/`. First-launch routing wired through `MainActivity` + `OnboardingPrefs` (DataStore). Runtime permission requests via the activity-result API. Wake word screen uses the real `VoskWakeWordEngine` for the "Test wake word" flow — detection is live, not simulated. Sensitivity setting persists to DataStore.

**Goal:** Get the user through permissions setup and into the chat screen in under 60 seconds.

### Screen-by-Screen Specification

#### 4.1 Welcome — `onboarding/WelcomeScreen.kt`

| Element              | Detail                                              |
| -------------------- | --------------------------------------------------- |
| Hero                 | Recall logo (voice orb visual) + tagline "Remember everything. Say it once." |
| Illustration         | Warm, minimal illustration of someone speaking to their phone hands-free |
| Primary CTA          | `Button` variant=Primary, label="Get Started" → navigates to `permissions` |
| Design               | `bg: base`, centered content, `display` variant for tagline |

#### 4.2 Permissions — `onboarding/PermissionsScreen.kt`

| Element              | Detail                                              |
| -------------------- | --------------------------------------------------- |
| Title                | "Recall needs a few things to work properly"          |
| Permission cards     | Each permission shown as a `Card` with icon, title, explanation, and toggle/grant button |
| Permissions          | See table below                                      |
| Progress             | Visual indicator showing how many permissions granted |
| CTA                  | "Continue" — enabled when all required permissions granted |

**Permission cards:**

| Permission | Icon | Required | Explanation |
|-----------|------|----------|-------------|
| Microphone | `Microphone` | Yes | "So I can hear you when you speak" |
| Notifications | `Bell` | Yes | "So I can remind you on time" |
| Location (fine) | `MapPin` | Yes (for location reminders) | "So I can remind you when you arrive somewhere" |
| Background Location | `MapPin` | Yes (for geofencing) | "So location reminders work even when the app is closed" |
| Battery Optimization Exempt | `Battery` | Recommended | "So your reminders are never delayed" |

**Behavior:**
- Each permission card shows current state: Granted (green check), Not Granted (grey), Denied (red X with "Open Settings" link)
- Tapping "Grant" triggers the Android system permission dialog
- If a permission is permanently denied, show "Open Settings" which launches app settings via `Intent`
- Microphone and Notifications are hard requirements — CTA stays disabled without them
- Location permissions are requested but can be skipped — location reminders will be unavailable, shown with a persistent banner on the chat screen

#### 4.3 Wake Word Setup — `onboarding/WakeWordSetupScreen.kt`

| Element              | Detail                                              |
| -------------------- | --------------------------------------------------- |
| Title                | "Say 'Hi Recall' to test"                            |
| VoiceOrb             | Large `VoiceOrb` in `Listening` state                |
| Instructions         | "Try saying 'Hi Recall' — I should respond right away" |
| Test result          | On detection: orb transitions to `Speaking`, Recall says "I'm here! That worked perfectly." |
| Sensitivity slider   | "Sensitivity" — Low / Medium / High, default Medium  |
| Skip option          | "Skip — I'll use the app manually" → disables wake word service |
| CTA                  | "Sounds Good" → navigates to `ready`                 |

**Behavior:**
- Vosk grammar-mode recognizer is initialized with the "Hi Recall" keyword
- Sensitivity slider adjusts the engine's sensitivity parameter (0.3 / 0.5 / 0.7)
- The test runs for up to 30 seconds before showing "I didn't hear that. Try again?" with a retry button

#### 4.4 Ready — `onboarding/ReadyScreen.kt`

| Element              | Detail                                              |
| -------------------- | --------------------------------------------------- |
| Title                | "You're all set"                                     |
| Body                 | "Just say 'Hi Recall' anytime, or open the app and type. I'll remember so you don't have to." |
| Tips                 | 3 example prompts shown as `ChatBubble` previews: "Remind me to call mum at 7pm", "Remind me to buy eggs when I get to Shoprite", "Remind me to check the pot in 15 minutes" |
| CTA                  | "Start Using Recall" → navigates to `chat/index`, starts `WakeWordService` |

---

## 5. Milestone 2 — Conversation Interface (Chat)

> **Status (✅ Done):** `ChatScreen`, `ChatBubble`, and `ChatViewModel` are wired up with text-mode input, an in-memory message list, auto-scrolling `LazyColumn`, a `TypingIndicator`, and the voice-mode UI (orb + keyboard toggle). Sending hits the worker `/chat` endpoint via `RecallApi`, which currently echoes — that's fine, the response shape is correct. **Deferred:** server-side message persistence to D1 lands in §7; the Reminders / + / Settings header buttons currently show snackbars pointing at §11/§13.

**Goal:** The primary screen where users interact with Recall via text or voice.

### 5.1 Chat Screen — `chat/ChatScreen.kt`

This is Recall's home screen. It is a **chat-style interface** where the user speaks or types, and Recall responds conversationally. This is not a settings panel or a form — it's a conversation.

| Element              | Detail                                              |
| -------------------- | --------------------------------------------------- |
| Header               | "Recall" title (left) + Reminders list icon + `+` FAB (→ `reminders/new`) + Settings gear icon (right) |
| Chat area            | Scrollable list of `ChatBubble` components (newest at bottom) |
| Input bar            | Text `Input` + mic toggle icon + send button         |
| Voice orb            | `VoiceOrb` replaces the input bar when mic is active |
| Empty state          | First launch: Recall's welcome message as a `ChatBubble` — "Hi! I'm Recall. Tell me what to remember and when, and I'll make sure you don't forget." |

**Input bar layout:**

```
+----------------------------------------------+
| [Keyboard icon]  Type a reminder...  [Send]  |
+----------------------------------------------+
```

When mic is tapped:

```
+----------------------------------------------+
|              [VoiceOrb: Listening]            |
|            "Listening..."                     |
|         [Keyboard icon to switch back]        |
+----------------------------------------------+
```

**Chat flow — text input:**

1. User types "Remind me to call mum at 7pm" and taps send
2. User's message appears as `ChatBubble(sender=User)`
3. Input is sent to Rust core via `processUserInput()`
4. Rust core parses intent, creates reminder, returns response JSON
5. Recall's response appears as `ChatBubble(sender=Recall)`: "Okay, reminder set for 7:00 PM — Call your mum."
6. A `Toast(type=Success)` briefly confirms: "Reminder saved"

**Chat flow — voice input:**

1. User taps mic icon (or wake word activates)
2. `VoiceOrb` transitions to `Listening` state
3. Android `SpeechRecognizer` captures speech, converts to text
4. Transcribed text appears as `ChatBubble(sender=User)`
5. Same flow as text input from step 3 onward
6. Recall also speaks the response via Android TTS

**Chat flow — wake word activation:**

1. `WakeWordService` detects "Hi Recall"
2. App is brought to foreground (or notification shade prompt if locked)
3. `VoiceOrb` is already in `Listening` state
4. User speaks their reminder
5. Same flow as voice input from step 3 onward

**Multi-turn conversation handling:**

If the Rust core determines the input is incomplete or ambiguous, it returns a response that asks a follow-up question. The conversation continues until the intent is fully resolved.

Example:
```
User: "Remind me to buy eggs when I get to the market"
Recall: "Which market? I found Oja Oba Market (2.3 km) and Oja Tuntun (4.1 km). Which one?"
User: "Oja Oba"
Recall: "Got it. I'll remind you to buy eggs when you're near Oja Oba Market."
```

The Rust core tracks conversation context so follow-up responses are tied to the original intent.

**Data management:**
- Chat messages are stored in SQLite (via Rust core) for persistence across app restarts
- Messages older than 30 days are auto-deleted (configurable in settings)
- Only the last 50 messages are loaded on screen mount; older messages load on scroll-up

### 5.2 Chat ViewModel — `chat/ChatViewModel.kt`

| State Field | Type | Description |
|-------------|------|-------------|
| `messages` | `List<ChatMessage>` | All visible chat messages |
| `inputText` | `String` | Current text input value |
| `inputMode` | `InputMode` | `Text` or `Voice` |
| `voiceState` | `OrbState` | Current voice orb state |
| `isProcessing` | `Boolean` | True while Rust core is processing |
| `pendingDisambiguation` | `DisambiguationData?` | Non-null when location disambiguation is needed |

---

## 6. Milestone 3 — Voice Input & Wake Word

> **Status (✅ Done):** Real `SpeechRecognizerManager` (Android `SpeechRecognizer`, en-NG with platform fallback, partial results, 2s silence auto-stop) and `TextToSpeechManager` (Android TTS, 0.95x speed, voice-only replies) are wired through `ChatViewModel`. Tapping the mic flips chat into voice mode and starts a real recognizer session; the partial transcript renders under the orb; the final transcript flows through the same `/chat` path; Recall's reply is spoken back when the input was voice. `WakeWordService` ships as a `microphone` foreground service with persistent low-priority notification, started/stopped from the onboarding screen and on app launch from the saved pref, and `BootReceiver` restarts it after reboot. Wake-word activations bring `MainActivity` forward via a `singleTop` intent and flip chat into voice mode through a `wakeEvents` flow. The `VoskWakeWordEngine` is fully wired in — it uses Vosk's offline grammar-mode recognizer (constrained to "hi recall" / "hey recall") with a bundled `vosk-model-small-en-us-0.15` model (~40 MB). No API keys needed; detection runs entirely on-device.

**Goal:** Users can speak to Recall hands-free via wake word or by tapping the mic.

### 6.1 Wake Word Service — `service/WakeWordService.kt`

| Aspect | Detail |
|--------|--------|
| Type | Android Foreground Service with persistent notification |
| Engine | Vosk (grammar-mode keyword spotting, `com.alphacephei:vosk-android`) |
| Wake word | "Hi Recall" — grammar-mode recognizer constrained to `["hi recall", "hey recall", "[unk]"]` |
| Sensitivity | Configurable: 0.3 (low), 0.5 (medium, default), 0.7 (high) |
| Lifecycle | Started on app launch (if enabled), survives app backgrounding |
| Notification | Persistent: "Recall is listening for 'Hi Recall'" with tap-to-open action |
| Battery | Vosk runs on-device, low-power — grammar mode limits CPU usage |

**On wake word detection:**

1. Vibrate device briefly (50ms haptic)
2. Play subtle activation sound (short chime, ~200ms)
3. Bring `MainActivity` to foreground via `Intent` with `FLAG_ACTIVITY_NEW_TASK`
4. `ChatScreen` receives activation event, transitions `VoiceOrb` to `Listening`
5. `SpeechRecognizer` starts capturing audio
6. If screen is locked: show heads-up notification "Recall is listening..." with a full-screen intent

**Service lifecycle:**

| Event | Behavior |
|-------|----------|
| App launched | Start service if wake word enabled in settings |
| App backgrounded | Service continues running |
| Phone rebooted | `BootReceiver` restarts service if wake word enabled |
| User disables wake word | Stop service, remove notification |
| Battery saver mode | Service continues but logs warning (user warned during onboarding) |

### 6.2 Speech Recognition — `voice/SpeechRecognizerManager.kt`

| Aspect | Detail |
|--------|--------|
| Engine | Android `SpeechRecognizer` (uses Google's on-device or cloud model) |
| Language | `en-NG` (English, Nigeria) with fallback to `en-US` |
| Partial results | Shown in real-time as ghost text below the `VoiceOrb` |
| Silence detection | Auto-stops after 2 seconds of silence |
| Max duration | 30 seconds per utterance |
| Error handling | On recognition error: "I didn't catch that. Try again?" |

**States:**

| State | VoiceOrb | Behavior |
|-------|----------|----------|
| Ready | `Idle` | Waiting for tap or wake word |
| Listening | `Listening` | Capturing audio, showing partial text |
| Processing | `Processing` | Audio captured, sending to Rust core |
| Speaking | `Speaking` | TTS playing Recall's response |
| Error | `Idle` | Error message shown, ready to retry |

### 6.3 Text-to-Speech — `voice/TextToSpeechManager.kt`

| Aspect | Detail |
|--------|--------|
| Engine | Android `TextToSpeech` API |
| Voice | Default system voice, locale `en-NG` or `en-US` |
| Speed | 0.95x (slightly slower than default for clarity) |
| Pitch | 1.0 (neutral) |
| When used | After voice input only — text-input responses are not spoken |
| Interruption | New user input cancels current TTS playback |
| Setting | Can be disabled entirely in settings |

---

## 7. Milestone 4 — Time-Based Reminders (One-Shot & Recurring)

**Status:** ✅ Done. The time parser, recurrence parser, and the full reminder tool surface live in `worker/src/lib/time.ts` and `worker/src/tools/reminders.ts`. The Cloudflare Agent (`worker/src/agent.ts`) runs the Anthropic tool-use loop and returns structured `reminder` payloads that the Android side mirrors into `AlarmManager` via `app/.../reminders/ReminderScheduler.kt` and `ReminderReceiver.kt`.

**Goal:** Users can set reminders for specific times, relative durations, or recurring schedules using natural language or the in-app form.

### 7.1 Supported Time Expressions

The Rust core's time parser handles these patterns:

| Pattern | Example | Parsed As |
|---------|---------|-----------|
| Relative minutes | "in 15 minutes" | now + 15 min |
| Relative hours | "in 2 hours" | now + 2 hours |
| Relative combined | "in 1 hour and 30 minutes" | now + 1.5 hours |
| Absolute clock time | "at 7pm", "at 19:00" | Today 19:00 (or tomorrow if past) |
| Absolute with "by" | "by 9am" | Today 08:45 (15 min before, with note) |
| Named time | "this evening", "tonight" | Today 18:00 / 21:00 |
| Tomorrow | "tomorrow at 3pm", "tomorrow morning" | Tomorrow 15:00 / 08:00 |
| Day of week | "on Friday at noon" | Next Friday 12:00 |
| Specific date | "on April 30th at 2:30pm" | 2026-04-30 14:30 |
| Before time | "before 11pm" | Today 22:45 (15 min early, as shown in user story) |

### 7.1.1 Supported Recurrence Expressions

The Rust core's recurrence parser handles these patterns:

| Pattern | Example | Parsed As |
|---------|---------|-----------|
| Daily | "every day at 8am", "every morning at 8" | Daily, time_of_day=08:00 |
| Weekday only | "every weekday at 9am" | Weekly, by_weekday=[Mon..Fri], 09:00 |
| Weekend only | "every weekend at 10am" | Weekly, by_weekday=[Sat,Sun], 10:00 |
| Specific weekday | "every Tuesday at 6:50pm" | Weekly, by_weekday=[Tue], 18:50 |
| Multiple weekdays | "every Monday and Wednesday at 7pm" | Weekly, by_weekday=[Mon,Wed], 19:00 |
| Bi-weekly | "every two weeks on Friday at 5pm" | Weekly, interval=2, by_weekday=[Fri], 17:00 |
| Monthly by day | "every month on the 1st at 9am" | Monthly, by_month_day=[1], 09:00 |
| Implicit weekly | "every Tuesday for my class at 6:50pm" | Weekly, by_weekday=[Tue], 18:50 |

The recurrence parser runs *before* the one-shot time parser. If a recurrence is detected, the time portion is extracted from the same expression and used as `time_of_day`.

**Smart defaults:**
- "this morning" → 08:00
- "this afternoon" → 14:00
- "this evening" → 18:00
- "tonight" → 21:00
- "tomorrow" → 08:00 tomorrow
- "before X" → 15 minutes before X (confirmed to user: "I'll remind you at [X - 15min] so you have time")

### 7.2 Time Reminder Flow (Rust Core)

```
Input: "Remind me to check the pot in 15 minutes"
  │
  ├─ Intent Parser
  │    ├─ action: "check the pot"
  │    ├─ trigger_type: Time
  │    └─ time_expr: "in 15 minutes"
  │
  ├─ Time Parser
  │    ├─ base: now (2026-04-08 15:27:00 WAT)
  │    ├─ offset: +15 minutes
  │    └─ resolved: 2026-04-08 15:42:00 WAT
  │
  ├─ Reminder Engine
  │    ├─ create reminder {
  │    │     id: uuid,
  │    │     body: "Check the pot",
  │    │     trigger: TimeTrigger { fire_at: 15:42 WAT },
  │    │     status: Pending,
  │    │     created_at: now
  │    │   }
  │    └─ persist to SQLite
  │
  └─ Response Builder
       └─ "Sure, I'll remind you at 3:42 PM."
```

### 7.3 Scheduling (Android Side)

When the Rust core creates a time-based reminder, the Kotlin layer:

1. Receives the reminder with its `next_fire_at` (one-shot uses `fire_at`; recurring uses the computed next occurrence)
2. Calls `ReminderScheduler.schedule(reminderId, nextFireAt)`
3. `ReminderScheduler` uses `AlarmManager.setExactAndAllowWhileIdle()` for precise timing
4. When the alarm fires, `ReminderReceiver` (BroadcastReceiver) triggers `NotificationHelper` to show the notification
5. Notification text is the reminder body verbatim ("Check the pot")
6. After firing:
   - **One-shot:** status set to `Fired`, no rescheduling
   - **Recurring:** Rust core's `recurrence::next_occurrence()` computes the next valid fire time, `next_fire_at` is updated, `fire_count` is incremented, and `ReminderScheduler` schedules the next alarm. Status stays `Pending`.

**Reliability:**
- `setExactAndAllowWhileIdle()` ensures alarms fire even in Doze mode
- A `BootReceiver` reschedules all pending time reminders (one-shot and recurring) after device reboot by re-reading `next_fire_at` from SQLite
- If the user opens the app and a reminder was missed (one-shot, `fire_at` in the past, not yet fired), it fires immediately
- For recurring reminders missed during reboot: the core computes the *next* occurrence after `now`, skipping any past ones (we don't fire stale recurring reminders)

### 7.4 Recurrence Lifecycle

| Action | Behavior |
|--------|----------|
| Create | Rust core parses recurrence expression OR receives a `RecurrenceRule` from the manual form. Computes first `next_fire_at`, persists, schedules alarm. |
| Fire | Notification shown. Recurrence rule evaluated for next occurrence. New alarm scheduled. |
| Pause (voice or in-app) | Status set to `Paused`. Alarm cancelled. Reminder remains in list with paused indicator. |
| Resume | Status set back to `Pending`. `next_fire_at` recomputed from now. Alarm rescheduled. |
| Stop | Reminder deleted entirely. Alarm cancelled. |
| End date reached | If `ends_on` is set and the next occurrence would fall after it, status set to `Expired`. No further alarms. |

---

## 8. Milestone 5 — Location-Based Reminders

> **Status: ✅ Done.** Google Places Text Search (New) API integrated in `worker/src/tools/places.ts`. `create_reminder` tool accepts `lat`/`lng`/`place_name`/`place_id` for location triggers. Android side: `GeofenceManager`, `GeofenceBroadcastReceiver`, `GeofencePrefs`, `LocationHelper` wired with `play-services-location`. `ChatViewModel` registers geofences for location-triggered reminders on receipt from the agent.

**Goal:** Users can set reminders that trigger when they arrive at a named place.

### 8.1 Location Resolution Flow

```
Input: "Remind me to give Sade her book when I reach Faculty of Technology"
  │
  ├─ Intent Parser
  │    ├─ action: "give Sade her book"
  │    ├─ trigger_type: Location
  │    └─ location_expr: "Faculty of Technology"
  │
  ├─ Location Resolver (Rust → calls Kotlin → Google Places API)
  │    ├─ query: "Faculty of Technology"
  │    ├─ bias: user's current lat/lng
  │    ├─ results: [ { name, address, lat, lng, place_id } ]
  │    └─ decision: single strong match → auto-accept
  │
  ├─ Reminder Engine
  │    ├─ create reminder {
  │    │     id: uuid,
  │    │     body: "Give Sade her book",
  │    │     trigger: LocationTrigger {
  │    │       place_name: "Faculty of Technology Lecture Theatre",
  │    │       lat: 7.5186, lng: 4.5300,
  │    │       radius_m: 100,
  │    │       place_id: "ChIJ..."
  │    │     },
  │    │     status: Pending,
  │    │     created_at: now
  │    │   }
  │    └─ persist to SQLite
  │
  └─ Response Builder
       └─ "Done. I'll remind you once you're within 100 metres of Faculty of Technology Lecture Theatre."
```

### 8.2 Google Places Integration — `location/PlacesManager.kt`

| Aspect | Detail |
|--------|--------|
| API | Google Places SDK for Android (New) |
| Search type | `searchByText()` with location bias (user's current position) |
| Radius bias | 50 km from user's current location |
| Max results | 5 (Rust core trims to 3 for disambiguation) |
| Fields requested | Name, formatted address, location (lat/lng), place ID |
| Caching | Results cached in-memory for 15 minutes to reduce API calls |

### 8.3 Geofencing — `location/GeofenceManager.kt`

| Aspect | Detail |
|--------|--------|
| API | Android Geofencing API (via Google Play Services) |
| Transition type | `GEOFENCE_TRANSITION_ENTER` |
| Radius | 100 metres (default, configurable per reminder in settings) |
| Loitering delay | 0ms (trigger immediately on enter) |
| Expiration | Never (until reminder is dismissed or deleted) |
| Max geofences | Android limit: 100 per app. Recall warns at 90. |
| Responsiveness | 30 seconds (balance between accuracy and battery) |

**On geofence trigger:**

1. `GeofenceBroadcastReceiver` receives the transition event
2. Looks up the reminder by geofence request ID (= reminder ID)
3. Fires notification with reminder body text
4. Updates reminder status to `Fired` in Rust core
5. Removes the geofence

**When GPS is off:**
- Geofence monitoring pauses automatically (Android behavior)
- When GPS is re-enabled, existing geofences resume
- Recall does NOT show an error — it silently waits
- If the user opens the app with GPS off and has location reminders, a subtle banner shows: "Location is off — your location reminders are paused"

---

## 9. Milestone 6 — Location Disambiguation

> **Status: ✅ Done.** Agent `resolve_place` tool returns 0–3 results with Haversine 50 m compaction. Chat response includes a `disambiguation` field when multiple places match. `DisambiguationSheet` (Material3 `ModalBottomSheet`) wired into `ChatScreen`; user pick is sent back as a chat message for the agent to consume.

**Goal:** Handle ambiguous location names gracefully.

### 9.1 Disambiguation Logic (Rust Core)

When the location resolver returns multiple results, the Rust core applies this decision tree:

```
Results from Google Places API
  │
  ├─ 0 results → respond: "I couldn't find a place called [query]. Could you be more specific?"
  │
  ├─ 1 result → auto-accept, confirm to user
  │
  ├─ 2+ results
  │    ├─ Calculate pairwise distances between all results
  │    ├─ If ALL results within 50m of each other
  │    │    └─ Pick highest-prominence result → auto-accept, confirm which was chosen
  │    ├─ If 2+ results but spread apart
  │    │    └─ Return top 3 as disambiguation options → ask user
  │    └─ User responds with natural language selection
  │         ├─ Rust core matches response against option names (fuzzy match)
  │         └─ If confident match → set reminder, confirm
  │              If still ambiguous → ask again (max 2 rounds, then: "Could you give me the full name?")
```

### 9.2 Disambiguation UI

When Rust core returns a disambiguation response, `ChatScreen` shows:

1. Recall's question as a `ChatBubble`: "I found a few places matching 'the plaza'..."
2. `DisambiguationSheet` bottom sheet with the options
3. User can tap an option OR type/speak their choice
4. If user taps: selection sent to Rust core directly
5. If user types/speaks: natural language sent to Rust core for fuzzy matching

---

## 10. Milestone 7 — Location Nicknames

> **Status: ✅ Done.** D1-backed `save_nickname`/`get_nicknames`/`delete_nickname` tool handlers with UPSERT logic. REST `/nicknames` CRUD routes in `worker/src/routes/nicknames.ts`. Agent system prompt includes the nickname flow. Android: `NicknamesScreen` (list + delete), `NicknameEditScreen` (create with place search via `/resolve-place`), `NicknamesViewModel`, all wired in `NavGraph`.

**Goal:** Users can assign personal names to places for quick reference.

### 10.1 Nickname Detection (Rust Core)

The intent parser maintains a list of **nickname trigger phrases**:

| Pattern | Examples |
|---------|----------|
| Possessive + place type | "my hostel", "my department", "my house", "my church" |
| "home" | "home", "at home" |
| Informal names | Any location query that returns 0 Google Places results AND matches no known nickname |

**Flow when nickname is detected:**

```
User: "Remind me to take my umbrella when I leave my hostel"
  │
  ├─ Intent Parser detects "my hostel" → checks nickname store
  │
  ├─ If nickname "my hostel" exists → resolve to saved location → create reminder
  │
  ├─ If nickname "my hostel" does NOT exist:
  │    └─ Recall responds: "Where is your hostel? Give me the name so I can find it."
  │         │
  │         User: "Adekunle Fajuyi Hall of Residence"
  │         │
  │         ├─ Location Resolver finds the place
  │         ├─ Saves nickname: "my hostel" → { place_name, lat, lng, place_id }
  │         ├─ Creates the original reminder
  │         └─ Recall: "Got it — I've saved 'my hostel' as Adekunle Fajuyi Hall of Residence.
  │                    I'll remind you to take your umbrella when you leave there."
```

### 10.2 Nickname Storage (Rust Core)

SQLite table:

```sql
CREATE TABLE nicknames (
    id          TEXT PRIMARY KEY,
    nickname    TEXT NOT NULL UNIQUE,       -- "my hostel", "home"
    place_name  TEXT NOT NULL,              -- "Adekunle Fajuyi Hall of Residence"
    place_id    TEXT,                       -- Google Places ID
    lat         REAL NOT NULL,
    lng         REAL NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);
```

### 10.3 Nickname Management Screens

#### `nicknames/NicknamesScreen.kt`

| Element | Detail |
|---------|--------|
| List | All saved nicknames as `Card` items: nickname label, resolved place name, `LocationPill` |
| Empty state | "No saved places yet. When you mention 'my hostel' or 'home', I'll ask where that is and remember." |
| Tap | Navigate to `NicknameEditScreen` |
| Add | "Add a place" FAB → prompts for nickname + location |

#### `nicknames/NicknameEditScreen.kt`

| Element | Detail |
|---------|--------|
| Nickname | Current nickname (editable) |
| Location | Current resolved place (tap to re-resolve via Google Places) |
| Delete | "Delete this place" button → confirmation dialog |
| Save | "Save Changes" button |

---

## 11. Milestone 8 — Reminder Management & Manual Builder

**Goal:** Users can view, create, edit, and delete reminders inside the app — without ever speaking. The in-app form is the complete alternative to voice and is also the only way to set up some advanced configurations comfortably (specific future dates, complex recurrences, custom geofence radius).

### 11.1 Reminder List — `reminders/RemindersScreen.kt`

| Element | Detail |
|---------|--------|
| Header | "Your Reminders" title + `+` FAB (top-right) → `reminders/new` |
| Tabs | `Active` / `Recurring` / `History` |
| Active list | `ReminderCard` for each pending one-shot reminder, sorted by `next_fire_at` ascending |
| Recurring list | `ReminderCard` for each recurring reminder, with `RecurrencePill` ("Every Tue · 6:50 PM"), sorted alphabetically |
| History list | `ReminderCard` for each fired/dismissed/expired reminder, sorted by `fired_at` descending |
| Empty state (active) | "No reminders right now. Tap + or say 'Hi Recall' to add one." |
| Empty state (recurring) | "No recurring reminders. Set one up for things that happen on a schedule." |
| Empty state (history) | "No completed reminders yet." |
| Swipe-to-delete | Swipe left on any card → red background with trash icon → deletes reminder |
| Pull-to-refresh | Refreshes reminder list from Rust core |

**Reminder card interactions:**

| Action | Behavior |
|--------|----------|
| Tap card | Navigate to `ReminderDetailScreen` |
| Swipe left | Delete with confirmation toast ("Reminder deleted. Undo?") |
| Long press | Opens action menu: Edit / Pause-Resume (recurring only) / Delete |

### 11.2 Reminder Detail — `reminders/ReminderDetailScreen.kt`

| Element | Detail |
|---------|--------|
| Body | Reminder text in `h3` variant |
| Trigger | `TimePill` or `LocationPill` showing trigger details |
| Recurrence | `RecurrencePill` shown if reminder is recurring (e.g. "Every Tuesday · 6:50 PM, until Dec 15") |
| Status | `Badge` showing current status |
| Countdown | `CountdownRing` if time-based and still pending — shows time until `next_fire_at` |
| Map preview | Static map thumbnail if location-based (Google Static Maps API) |
| Created | "Set on [date] at [time]" — `caption`, muted |
| Last fired | "Last reminded on [date]" — shown for recurring with `fire_count > 0` |
| Fire count | "Fired 12 times" — for recurring |
| Actions | "Edit" (primary), "Pause"/"Resume" (recurring only), "Delete" (ghost-danger) |
| Edit | Navigates to `reminders/edit` with the form pre-populated |

### 11.3 Manual Reminder Builder — `reminders/ReminderFormScreen.kt`

The same screen handles both `new` and `edit` routes. The form is the complete in-app alternative to voice — every reminder configuration possible via voice is also possible via the form, plus a few that are easier with a UI (specific future dates, multi-weekday recurrence, custom geofence radius).

**Form sections:**

| Section | Field | Detail |
|---------|-------|--------|
| **Body** | Reminder text | Multi-line `Input`, required, max 200 chars. "What should I remind you about?" |
| **Trigger type** | Segmented control | `Time` / `Location` — required. Determines which sections appear below. |
| **Time section** (when type=Time) | Date | `DatePicker` — defaults to today. Required. |
| | Time | `TimePicker` — defaults to next round half-hour. Required. |
| | Repeat | `Toggle` — "Repeat this reminder" |
| **Recurrence section** (when Repeat is on) | Frequency | Picker: `Daily` / `Weekly` / `Monthly` |
| | Interval | Stepper: "Every [N] week(s)" — default 1 |
| | Weekdays (Weekly) | `WeekdayPicker` — multi-select chip row Mon–Sun |
| | Days of month (Monthly) | Multi-select 1–31 |
| | End date | Optional `DatePicker` — "Stop repeating on" |
| **Location section** (when type=Location) | Place | Search input → calls Google Places via Kotlin → results list. Or pick from saved nicknames. |
| | Radius | Slider: 50m / 100m / 200m / 500m — default 100m |
| **Notes** | Optional notes | Multi-line `Input`, max 500 chars — appears in detail view but not the notification |

**Form behavior:**

- The form is a vertically scrolling `Column` inside a themed `Scaffold`
- Sections expand/collapse based on the trigger type and the Repeat toggle
- A **live preview** at the bottom shows what the reminder will look like when fired (e.g. a `ReminderCard` preview)
- Validation runs on submit: missing required fields highlight in red with inline error
- Submit button: "Save Reminder" (primary, full-width, sticky to bottom)
- On submit:
  1. Form data is serialized to JSON
  2. Sent to Rust core via `RecallBridge.createReminderFromForm(json)` (new JNI function)
  3. Rust core constructs the `Reminder` directly (no NLP parsing — the form supplies structured data)
  4. Persisted, scheduled, and the user is navigated back to the reminder list with a success `Toast`
- For edit mode: form is pre-populated from existing reminder; submit calls `updateReminder`

### 11.4 Voice-Based Management

Users can also manage reminders via conversation — both ways are first-class:

| User Says | Recall Response |
|-----------|---------------|
| "Cancel the Shoprite reminder" | Fuzzy-matches against active reminders → "I found 'Buy glucose biscuits when I pass Shoprite'. Delete it?" → "Yes" → deleted |
| "What reminders do I have?" | Lists active reminders: "You have 3 reminders: 1. Call mum at 7 PM, 2. Buy eggs at Shoprite, 3. Submit assignment before 11 PM." |
| "Change the call mum reminder to 8pm" | Finds matching reminder, updates trigger → "Updated. I'll remind you to call mum at 8 PM instead." |
| "Pause my Tuesday class reminder" | Finds matching recurring reminder → sets status to `Paused` → "Paused. I'll stop reminding you about your class until you tell me to resume." |
| "Resume my Tuesday class reminder" | Sets status back to `Pending`, recomputes next occurrence → "Resumed. Next reminder is Tuesday at 6:50 PM." |
| "Stop my Tuesday class reminder" | Deletes the recurring reminder entirely (after confirmation) |

The Rust core handles these as management intents (`DeleteReminder`, `ListReminders`, `EditReminder`, `PauseReminder`, `ResumeReminder`) with fuzzy matching against the reminder body text.

---

## 12. Milestone 9 — Notification & Firing

**Goal:** Reminders fire reliably and clearly, whether the app is open or not.

### 12.1 Notification Design

| Aspect | Detail |
|--------|--------|
| Channel | "Recall Reminders" — importance: HIGH (heads-up notification) |
| Title | "Recall" |
| Body | Reminder body text verbatim (e.g. "Check the pot") |
| Icon | Recall logo (small icon), reminder type icon (large icon: clock or map pin) |
| Sound | Default notification sound |
| Vibration | Default vibration pattern |
| Actions | "Done" (dismisses + marks complete), "Snooze 10 min" (reschedules +10 min) |
| Tap action | Opens app → `ChatScreen` with the reminder highlighted |

### 12.2 Time-Based Firing

| Aspect | Detail |
|--------|--------|
| Mechanism | `AlarmManager.setExactAndAllowWhileIdle()` |
| Receiver | `ReminderBroadcastReceiver` extends `BroadcastReceiver` |
| On fire | Show notification, update status to `Fired` in Rust core, vibrate |
| Snooze | "Snooze 10 min" action reschedules alarm for now + 10 min |
| Missed | If alarm fires but notification was swiped without action → status stays `Fired` (visible in history) |

### 12.3 Location-Based Firing

| Aspect | Detail |
|--------|--------|
| Mechanism | Android Geofencing API transition event |
| Receiver | `GeofenceBroadcastReceiver` extends `BroadcastReceiver` |
| On fire | Show notification, update status to `Fired`, remove geofence |
| Re-entry | Geofence is removed after first fire — no repeat triggers |

### 12.4 Notification Preferences

Configurable in settings:

| Setting | Options | Default |
|---------|---------|---------|
| Sound | On / Off | On |
| Vibration | On / Off | On |
| Heads-up | On / Off | On |
| Snooze duration | 5 / 10 / 15 / 30 minutes | 10 min |
| Re-notify if not dismissed | On / Off | Off |
| Re-notify interval | 5 / 10 / 15 minutes | 10 min |

---

## 13. Milestone 10 — Settings & Preferences

**Goal:** Users can configure Recall's behavior to their preferences.

### 13.1 Settings Menu — `settings/SettingsScreen.kt`

| Menu Item | Icon | Navigates To | Description |
|-----------|------|-------------|-------------|
| Wake Word | `Waveform` | `WakeWordSettingsScreen` | Enable/disable, sensitivity |
| Voice & Speech | `Microphone` | `VoiceSettingsScreen` | TTS, speech recognition language |
| Notifications | `Bell` | `NotificationSettingsScreen` | Sound, vibration, snooze |
| Location | `MapPin` | `LocationSettingsScreen` | Geofence radius, GPS status |
| Saved Places | `MapPin` | `nicknames/NicknamesScreen` | Manage location nicknames |
| Chat History | `ClockCounterClockwise` | (action) | Clear chat history (with confirmation) |
| Theme | `Moon` / `Sun` | (toggle) | Light / Dark / System |
| About Recall | `Info` | `AboutScreen` | Version, licenses |

### 13.2 Wake Word Settings — `settings/WakeWordSettingsScreen.kt`

| Element | Detail |
|---------|--------|
| Enable toggle | `Toggle` — starts/stops `WakeWordService` |
| Sensitivity | Slider: Low / Medium / High |
| Test button | "Test wake word" — same UI as onboarding test |
| Battery note | "Wake word uses less than 5% battery per day" |

### 13.3 Voice Settings — `settings/VoiceSettingsScreen.kt`

| Element | Detail |
|---------|--------|
| TTS enabled | `Toggle` — enable/disable Recall speaking responses |
| TTS speed | Slider: 0.75x / 1.0x / 1.25x |
| Speech language | Picker: English (Nigeria) / English (US) / English (UK) |

### 13.4 Notification Settings — `settings/NotificationSettingsScreen.kt`

| Element | Detail |
|---------|--------|
| Sound | `Toggle` |
| Vibration | `Toggle` |
| Heads-up | `Toggle` |
| Snooze duration | Picker: 5 / 10 / 15 / 30 min |
| Re-notify | `Toggle` — re-fire notification if not dismissed |
| Re-notify interval | Picker: 5 / 10 / 15 min (shown only if re-notify enabled) |

### 13.5 Location Settings — `settings/LocationSettingsScreen.kt`

| Element | Detail |
|---------|--------|
| GPS status | Shows current GPS state (On/Off) with link to system settings |
| Default geofence radius | Slider: 50m / 100m / 200m / 500m (default 100m) |
| Active geofences | Count of active location reminders (e.g. "3 of 100 geofence slots used") |
| Battery note | "Location reminders use GPS periodically. This may affect battery life." |

### 13.6 About Screen — `settings/AboutScreen.kt`

| Element | Detail |
|---------|--------|
| Version | App version + build number |
| Backend | "Powered by Cloudflare Workers" |
| Open-source licenses | Link to licenses list |
| Privacy note | "Recall processes your voice on-device. Your reminders sync with your personal Cloudflare Worker." |

---

## 14. Worker / Agent Architecture

> **Note:** This project uses a TypeScript backend on Cloudflare Workers (not a Rust NDK core). A Rust port is planned for a future milestone. The architecture below reflects the current TypeScript implementation.

### 14.1 Module Responsibilities

| File | Role | Key Exports |
|------|------|-------------|
| `src/index.ts` | Hono router — Worker entry point. Registers `/chat`, `/reminders`, `/nicknames`, `/health`. Exports `RecallAgent` so Cloudflare wires the Durable Object. | `default` (Hono app), `RecallAgent`, `Env` |
| `src/agent.ts` | `RecallAgent` Durable Object. Holds per-device conversation history in DO storage. Runs the Workers AI tool-use loop. Normalises tool-call shapes across Workers AI runtime variants. | `RecallAgent extends Agent<Env, AgentState>` |
| `src/tools/reminders.ts` | Reminder tool handlers and JSON schemas (`create_reminder`, `list_reminders`, `delete_reminder`, `update_reminder`, `pause_reminder`, `resume_reminder`, `resolve_time`). Single source of truth for reminder business logic — shared by the agent loop and the REST route handlers. | `reminderTools[]`, `dispatchReminderTool()`, `HandlerContext` |
| `src/lib/time.ts` | Pure, zero-I/O timezone-aware time and recurrence parser. All parsing happens in the user's IANA zone (default `Africa/Lagos`), then converts to UTC for storage. | `parseTimeExpression()`, `parseRecurrence()`, `nextOccurrence()` |
| `src/lib/types.ts` | Shared TypeScript types. Language-agnostic — a future Rust port maps 1:1. | `Reminder`, `Trigger`, `RecurrenceRule`, `ChatMessage`, `Nickname`, `ReminderStatus` |
| `src/lib/auth.ts` | Anonymous device-bound auth. Extracts `X-Recall-Device` from request headers; upserts `devices` row on every request. | `getDeviceId()`, `touchDevice()`, `MissingDeviceError` |
| `src/routes/reminders.ts` | Hono sub-router for REST reminder CRUD. Delegates to `tools/reminders.ts` handlers — no duplicated logic. | `reminders` (Hono router) |
| `src/routes/nicknames.ts` | Hono sub-router for nickname CRUD. | `nicknames` (Hono router) |
| `src/routes/health.ts` | Liveness probe — returns `{ ok: true }`. | `health` (Hono router) |
| `src/db/migrations/0001_init.sql` | D1 schema. Applied via `wrangler d1 migrations apply`. | — |

### 14.2 Agent Loop State Machine

```
┌─────────┐
│  Idle   │ ← Waiting for next HTTP POST /chat
└────┬────┘
     │ { message, tz, input_mode }
     │ deviceId from X-Recall-Device header
     ▼
┌─────────────────────────────────┐
│  Load DO state                  │
│  (conversation history array)   │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│  Append user turn to history    │
│  Build system prompt with tz    │
│  + current local time           │
└────────────────┬────────────────┘
                 │
    ┌────────────▼────────────┐
    │  Workers AI call        │  ← @cf/meta/llama-3.3-70b-instruct-fp8-fast
    │  (history + tools)      │     with all reminderTools schemas
    └────────────┬────────────┘
                 │
         ┌───────┴────────┐
         │  tool_calls?   │
        Yes               No
         │                │
         ▼                ▼
┌────────────────┐  ┌──────────────────────┐
│ Execute tools  │  │  Final text reply    │
│ dispatchReminder│  │  Append to history   │
│ Tool()         │  │  Save DO state       │
└───────┬────────┘  │  Return response     │
        │           └──────────────────────┘
        │ Append tool results to history
        └──── loop back (max 5 iterations)
```

Key properties:
- Max **5 tool iterations** per turn to prevent runaway loops.
- History capped at **40 messages** (sliding window) to stay within context budget.
- DO state persisted after every turn via `this.setState({ history })`.
- `normaliseToolCall()` handles three Workers AI output shapes (flat, OpenAI-nested, stringified args).

### 14.3 Worker HTTP API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | None | Liveness probe |
| `POST` | `/chat` | `X-Recall-Device` | Run one agent turn. Body: `{ message, tz?, input_mode?, client_now? }`. Response: `{ response_text, action, reminder?, reminders?, disambiguation }` |
| `GET` | `/reminders` | `X-Recall-Device` | List reminders. Query: `filter=active\|recurring\|history` |
| `POST` | `/reminders` | `X-Recall-Device` | Create reminder from manual form (no LLM). Body same shape as `create_reminder` tool input |
| `PATCH` | `/reminders/:id` | `X-Recall-Device` | Update reminder fields |
| `DELETE` | `/reminders/:id` | `X-Recall-Device` | Delete reminder |
| `POST` | `/reminders/:id/pause` | `X-Recall-Device` | Pause recurring reminder |
| `POST` | `/reminders/:id/resume` | `X-Recall-Device` | Resume paused reminder |
| `POST` | `/reminders/:id/snooze` | `X-Recall-Device` | Snooze reminder. Body: `{ snooze_minutes }` |
| `GET` | `/nicknames` | `X-Recall-Device` | List location nicknames |
| `POST` | `/nicknames` | `X-Recall-Device` | Save / update nickname |
| `DELETE` | `/nicknames/:id` | `X-Recall-Device` | Delete nickname |

Auth: `X-Recall-Device` is a per-install UUID generated client-side on first launch, persisted in Android DataStore, and sent on every request. The Worker upserts a `devices` row on every request via `touchDevice()`. No secrets, no user accounts in the MVP.

### 14.4 D1 Schema Overview

Four tables — see `src/db/migrations/0001_init.sql`. New migrations use sequential prefixes (`0002_`, …).

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `devices` | Tracks every install that has called the worker. | `id` (UUID, PK), `first_seen_at`, `last_seen_at` |
| `reminders` | System of record for all reminders. | `id`, `device_id` (FK→devices), `body`, `trigger_type` (`time`\|`location`), `trigger_data` (JSON), `recurrence_data` (JSON, nullable), `status`, `source`, `next_fire_at`, `snoozed_until` |
| `messages` | Persisted conversation turns. | `id`, `device_id`, `sender` (`user`\|`recall`), `text`, `timestamp`, `related_reminder_id`, `input_mode` |
| `nicknames` | User-defined location aliases. | `id`, `device_id`, `nickname`, `place_name`, `place_id`, `lat`, `lng` — UNIQUE on `(device_id, nickname)` |

Indices: `idx_reminders_device`, `idx_reminders_status`, `idx_reminders_next_fire`, `idx_messages_device_time`.

---

## 15. Data Models

> **Status: ✅ Done.** TypeScript types live in `worker/src/lib/types.ts`, typed D1 query helpers in `worker/src/db/queries.ts`, and Kotlin DTOs in `app/src/main/java/com/recall/network/ApiModels.kt`. The D1 schema is in `worker/src/db/migrations/0001_init.sql`. All tool handlers (`reminders.ts`, `nicknames.ts`) and `auth.ts` delegate to `queries.ts` — no inline SQL in business-logic modules.

### 15.1 Reminder

```typescript
// worker/src/lib/types.ts

type ReminderId = string;  // UUID v4
type DeviceId = string;    // per-install UUID from X-Recall-Device
type IsoTimestamp = string; // ISO 8601, always UTC

type ReminderStatus =
  | "pending"     // Awaiting trigger (one-shot or next occurrence of recurring)
  | "fired"       // One-shot fired and acknowledged. Recurring never reaches this.
  | "dismissed"   // User tapped "Done" on notification (one-shot)
  | "snoozed"     // User tapped "Snooze", new fire time set
  | "paused"      // Recurring reminder paused by user — won't fire until resumed
  | "expired";    // Time-based reminder never acknowledged (>24h past, one-shot only)

type ReminderSource =
  | "voice"        // Created via wake-word or mic-tap voice flow
  | "chat_text"    // Typed into the conversation input
  | "manual_form"; // Created via the "+ New Reminder" form

type Trigger =
  | {
      type: "time";
      fire_at: IsoTimestamp;      // Absolute UTC time for one-shot / first occurrence
      original_expr: string;     // "in 15 minutes" — what the user said
    }
  | {
      type: "location";
      place_name: string;        // "Faculty of Technology Lecture Theatre"
      place_id: string | null;   // Google Places ID
      lat: number;
      lng: number;
      radius_m: number;          // Default 100
      original_expr: string;     // "Faculty of Technology"
    };

interface RecurrenceRule {
  frequency: "daily" | "weekly" | "monthly";
  interval: number;                         // Every N units (e.g. every 2 weeks → 2)
  by_weekday?: Weekday[];                   // For Weekly: which days of week
  by_month_day?: number[];                  // For Monthly: which days (1–31)
  time_of_day: string;                      // "18:50" — local time, 24h
  starts_on: IsoTimestamp;                  // First valid occurrence
  ends_on?: IsoTimestamp | null;            // null = forever
  original_expr: string;                    // "every Tuesday at 6:50pm"
}

type Weekday = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";

interface Reminder {
  id: ReminderId;
  device_id: DeviceId;                      // Per-install device identifier
  body: string;                             // "Check the pot"
  trigger: Trigger;
  recurrence: RecurrenceRule | null;        // null = one-shot
  status: ReminderStatus;
  source: ReminderSource;
  created_at: IsoTimestamp;
  fired_at: IsoTimestamp | null;            // Set when last fired
  fire_count: number;                       // >1 for recurring
  next_fire_at: IsoTimestamp | null;        // Computed for recurring + snoozed
  snoozed_until: IsoTimestamp | null;       // Set when snoozed
}
```

### 15.2 Chat Message

```typescript
interface ChatMessage {
  id: string;                               // UUID v4
  device_id: DeviceId;
  sender: "user" | "recall";
  text: string;
  timestamp: IsoTimestamp;
  related_reminder_id: ReminderId | null;   // If this message created/modified a reminder
  input_mode: "text" | "voice";
}
```

### 15.3 Nickname

```typescript
interface Nickname {
  id: string;                               // UUID v4
  device_id: DeviceId;
  nickname: string;                         // "my hostel"
  place_name: string;                       // "Adekunle Fajuyi Hall of Residence"
  place_id: string | null;                  // Google Places ID
  lat: number;
  lng: number;
  created_at: IsoTimestamp;
  updated_at: IsoTimestamp;
}
```

### 15.4 Conversation Context

Conversation context (chat history, pending disambiguation state) is held per-user in the `RecallAgent` Durable Object via `this.setState({ history })`. There is no D1 table for it — DO storage provides the same single-threaded, per-device isolation with zero race conditions on multi-turn flows.

```typescript
// worker/src/agent.ts — stored in Durable Object state
interface AgentState {
  history: AiMessage[];  // Sliding window, max 40 messages
}
```

### 15.5 D1 Schema

Four tables — see `worker/src/db/migrations/0001_init.sql`. New migrations use sequential prefixes (`0002_`, …). Applied via `wrangler d1 migrations apply`.

```sql
-- Devices — tracks every install that has called the worker
CREATE TABLE IF NOT EXISTS devices (
    id              TEXT PRIMARY KEY,     -- per-install UUID from X-Recall-Device
    first_seen_at   TEXT NOT NULL,
    last_seen_at    TEXT NOT NULL
);

-- Reminders — system of record for all reminders
CREATE TABLE IF NOT EXISTS reminders (
    id              TEXT PRIMARY KEY,
    device_id       TEXT NOT NULL REFERENCES devices(id),
    body            TEXT NOT NULL,
    trigger_type    TEXT NOT NULL CHECK (trigger_type IN ('time', 'location')),
    trigger_data    TEXT NOT NULL,          -- JSON blob for Trigger discriminated union
    recurrence_data TEXT,                   -- JSON blob for RecurrenceRule (NULL = one-shot)
    status          TEXT NOT NULL DEFAULT 'pending',
    source          TEXT NOT NULL CHECK (source IN ('voice', 'chat_text', 'manual_form')),
    created_at      TEXT NOT NULL,
    fired_at        TEXT,
    fire_count      INTEGER NOT NULL DEFAULT 0,
    next_fire_at    TEXT,
    snoozed_until   TEXT
);

CREATE INDEX IF NOT EXISTS idx_reminders_device ON reminders(device_id);
CREATE INDEX IF NOT EXISTS idx_reminders_status ON reminders(status);
CREATE INDEX IF NOT EXISTS idx_reminders_next_fire ON reminders(next_fire_at);

-- Chat messages — persisted conversation turns
CREATE TABLE IF NOT EXISTS messages (
    id                  TEXT PRIMARY KEY,
    device_id           TEXT NOT NULL REFERENCES devices(id),
    sender              TEXT NOT NULL CHECK (sender IN ('user', 'recall')),
    text                TEXT NOT NULL,
    timestamp           TEXT NOT NULL,
    related_reminder_id TEXT REFERENCES reminders(id),
    input_mode          TEXT NOT NULL CHECK (input_mode IN ('text', 'voice'))
);

CREATE INDEX IF NOT EXISTS idx_messages_device_time ON messages(device_id, timestamp);

-- Location nicknames — user-defined place aliases
CREATE TABLE IF NOT EXISTS nicknames (
    id          TEXT PRIMARY KEY,
    device_id   TEXT NOT NULL REFERENCES devices(id),
    nickname    TEXT NOT NULL,
    place_name  TEXT NOT NULL,
    place_id    TEXT,
    lat         REAL NOT NULL,
    lng         REAL NOT NULL,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    UNIQUE(device_id, nickname)
);
```

### 15.6 Typed Query Helpers — `db/queries.ts`

All D1 SQL lives in `worker/src/db/queries.ts`. Tool handlers and route handlers import functions from this module instead of writing raw SQL inline. Key exports:

| Function | Description |
|----------|-------------|
| `touchDevice(db, deviceId)` | Upsert device row on every request |
| `insertReminder(db, params)` | Insert a new reminder |
| `selectReminderById(db, id, deviceId)` | Fetch by exact ID |
| `selectReminderByMatch(db, matchText, deviceId)` | Fuzzy body-text match |
| `selectReminders(db, deviceId, filter)` | List with active/recurring/history filter |
| `updateReminderFields(db, id, deviceId, fields)` | Patch any combination of reminder fields |
| `deleteReminder(db, id, deviceId)` | Hard delete |
| `upsertNickname(db, params)` | Insert or update by (device_id, nickname) |
| `selectNicknames(db, deviceId)` | List all nicknames for a device |
| `selectNicknameByText(db, deviceId, nickname)` | Case-insensitive lookup |
| `deleteNickname(db, id, deviceId)` | Hard delete |
| `insertMessage(db, params)` | Persist a chat message |
| `selectMessages(db, deviceId, limit?)` | Fetch recent messages |

---

## 16. Agent Tools & Intent Resolution

> **Status: ✅ Done.** The LLM is the primary intent-resolution path, and tools are the only way it can affect the world. This section documents the tool-calling agent implemented in `worker/src/agent.ts`.

### 16.1 Tool Inventory

The agent has 10 tools, grouped by domain. Each tool has a name, description, and JSON schema — the LLM decides which to call based on the user's message.

| # | Tool | Module | Description | Introduced |
|---|------|--------|-------------|------------|
| 1 | `create_reminder` | `tools/reminders.ts` | Create a time-based or location-based reminder. Accepts `time_expr` (natural language) or `fire_at` (ISO 8601). For locations, accepts resolved `lat`/`lng`/`place_name`/`place_id`. | §7 |
| 2 | `list_reminders` | `tools/reminders.ts` | List reminders with filter: `active`, `recurring`, or `history`. | §7 |
| 3 | `delete_reminder` | `tools/reminders.ts` | Delete by exact `reminder_id` or fuzzy `match_text`. | §7 |
| 4 | `update_reminder` | `tools/reminders.ts` | Edit body or time. Same matching as delete. | §7 |
| 5 | `pause_reminder` | `tools/reminders.ts` | Pause a recurring reminder. | §7 |
| 6 | `resume_reminder` | `tools/reminders.ts` | Resume a paused recurring reminder. Recomputes `next_fire_at`. | §7 |
| 7 | `resolve_time` | `tools/reminders.ts` | Resolve a time expression to an ISO 8601 timestamp without creating a reminder. Useful for confirmation. | §7 |
| 8 | `resolve_place` | `tools/places.ts` | Resolve a place name via Google Places Text Search (New) API. Returns 0–3 results with Haversine 50 m compaction. | §8 |
| 9 | `save_nickname` | `tools/nicknames.ts` | UPSERT a personal place nickname (e.g. "my hostel" → resolved place). | §10 |
| 10 | `get_nicknames` | `tools/nicknames.ts` | List all saved place nicknames for the current device. | §10 |

All tools share these properties:
- **Schemas** are in Anthropic tool_use format with Zod runtime validation.
- **Execution** happens via `dispatchReminderTool()`, `dispatchNicknameTool()`, or `handleResolvePlace()` in the agent loop.
- **State** is scoped to the device via the `deviceId` from the `X-Recall-Device` header.

### 16.2 Intent Resolution via Tool Calling

Intent resolution is implicit — the LLM reads the user's message and calls the appropriate tool(s):

| User Intent | Tool Call(s) | Example Input |
|-------------|-------------------|---------------|
| Create reminder (time) | `create_reminder` | "Remind me to call mum at 7pm" |
| Create reminder (location) | `resolve_place` → `create_reminder` | "Remind me to buy eggs when I get to Shoprite" |
| Delete reminder | `delete_reminder` | "Cancel the Shoprite reminder" |
| List reminders | `list_reminders` | "What reminders do I have?" |
| Edit reminder | `update_reminder` | "Change the call mum reminder to 8pm" |
| Pause reminder | `pause_reminder` | "Pause my Tuesday class reminder" |
| Resume reminder | `resume_reminder` | "Resume my Tuesday class reminder" |
| Answer disambiguation | LLM matches user pick → `create_reminder` | "Oja Oba" (after disambiguation) |
| Define nickname | `resolve_place` → `save_nickname` | "My hostel is Fajuyi Hall" |
| Greeting / chitchat | No tool call — LLM replies conversationally | "Hi", "Thanks" |

The tool-use loop runs up to **8 iterations** per turn. A single user message can trigger multiple tools (e.g. `get_nicknames` → `resolve_place` → `create_reminder`).

### 16.3 System Prompt Rules

The system prompt in `agent.ts` encodes the intent-resolution policy. Key rules:

| Rule | Detail |
|------|--------|
| **Tools before confirmation** | The LLM must call `create_reminder` before telling the user a reminder is set. A reply without a tool call is treated as a lie. |
| **Time delegation** | The LLM passes `time_expr` verbatim to `create_reminder` — it never computes ISO dates itself. `lib/time.ts` handles all time arithmetic server-side. |
| **Location flow** | Always call `resolve_place` before `create_reminder` for location reminders. If multiple results, ask the user to pick (no auto-create). |
| **Nickname flow** | When the user mentions a personal place ("my hostel"), call `get_nicknames` first. If found, skip `resolve_place`. If not found, resolve → create → offer to save nickname. |
| **Recurrence detection** | "Every \<something\>" is recurring. Pass the whole phrase as `time_expr` — the backend's recurrence parser decides whether it's daily, weekly, or monthly. |
| **Concise replies** | 1–2 sentences max. Confirm the specific time, place, or recurrence back to the user. |
| **Context** | System prompt includes the user's timezone (`X-Recall-Tz`), current local time, and input mode (`text`/`voice`). |

### 16.4 Body & Time Extraction

The LLM handles body/time extraction as part of its tool arguments:

| User Input | LLM Tool Call |
|------------|---------------|
| "Remind me to **call mum** at 7pm" | `create_reminder({ body: "call mum", time_expr: "at 7pm" })` |
| "Remind me to **check the pot** in 15 minutes" | `create_reminder({ body: "check the pot", time_expr: "in 15 minutes" })` |
| "Remind me to **give Sade her book** when I reach Faculty of Tech" | `resolve_place({ query: "Faculty of Tech" })` → `create_reminder({ body: "give Sade her book", ... })` |
| "**Submit the GEG assignment** before 11pm" | `create_reminder({ body: "Submit the GEG assignment", time_expr: "before 11pm" })` |

The `time_expr` is processed server-side by `parseTimeExpression()` and `parseRecurrence()` in `worker/src/lib/time.ts`. The body is stored verbatim as the notification text.

### 16.5 Fuzzy Matching for Management

When a user says "cancel the Shoprite reminder", the LLM calls `delete_reminder({ match_text: "Shoprite" })`. The tool handler in `tools/reminders.ts`:

1. Calls `selectReminderByMatch(db, "Shoprite", deviceId)` from `db/queries.ts`
2. This runs a case-insensitive `LIKE %shoprite%` query against active reminder bodies
3. Returns the first match ordered by `next_fire_at` ascending
4. If found → deletes and returns the reminder
5. If not found → returns `{ deleted: null }` and the LLM tells the user it couldn't find a match

For ambiguous matches, the LLM can call `list_reminders` first, present the options to the user, then call `delete_reminder` with the exact `reminder_id` from the user's selection.

---

## 17. Android-Specific Behaviors

### 17.1 Battery & Background Execution

| Concern | Strategy |
|---------|----------|
| Wake word service | Foreground service with persistent notification (required by Android for long-running background work) |
| Geofence monitoring | Handled by Google Play Services (system-level, efficient) |
| Alarm scheduling | `AlarmManager.setExactAndAllowWhileIdle()` survives Doze |
| Battery optimization | Request exemption during onboarding; show warning if denied |
| Doze mode | Exact alarms and geofence transitions still fire in Doze |
| App standby | Foreground service prevents app from being standby-bucketed |

### 17.2 Permissions Model

| Permission | When Requested | Fallback if Denied |
|-----------|---------------|-------------------|
| `RECORD_AUDIO` | Onboarding | Voice input unavailable; text-only mode |
| `POST_NOTIFICATIONS` | Onboarding | Reminders fire but no notification shown — defeats purpose, strongly warned |
| `ACCESS_FINE_LOCATION` | Onboarding | Location reminders unavailable |
| `ACCESS_BACKGROUND_LOCATION` | Onboarding (after fine location granted) | Location reminders only work while app is open |
| `SCHEDULE_EXACT_ALARM` | Automatically (Android 12+) | Inexact alarms used (may be delayed by minutes) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Onboarding | Alarms may be delayed in Doze |
| `RECEIVE_BOOT_COMPLETED` | Manifest (no runtime prompt) | Required to restart the wake word service on reboot. |
| `FOREGROUND_SERVICE` | Manifest (no runtime prompt) | Required for wake word service |
| `FOREGROUND_SERVICE_MICROPHONE` | Manifest (Android 14+) | Required for wake word foreground service |

### 17.3 Offline Behavior

| Scenario | Behavior |
|----------|----------|
| No internet + text input | Fails gracefully ("Recall needs an internet connection to think. Please check your network."). The LLM agent runs on Cloudflare Workers. |
| No internet + voice input | Wake word triggers (Vosk is fully offline) and orb activates, but dictation/parsing fails without network. |
| No internet + location query | Google Places API unavailable. Requests fail gracefully. |
| No internet + notification fire | Works fully — alarms and geofences are handled by Android OS (`AlarmManager` / Play Services) and don't require network to trigger. |

### 17.4 Data Privacy

| Aspect | Detail |
|--------|--------|
| Voice processing | Speech-to-text via Android's `SpeechRecognizer` (may use Google servers). No raw audio is stored by Recall. |
| Reminder storage | All reminders, messages, and nicknames are stored securely in Cloudflare D1. The Android app fetches state from the API. Future milestones will introduce a local cache. |
| Location data | Current location used only for Google Places bias and geofence registration. Not tracked or stored continuously. |
| Wake word | Vosk runs 100% on-device. No audio leaves the device for wake word detection. |
| Analytics | None in MVP. No telemetry, no crash reporting, no usage tracking. |

### 17.5 Device Reboot Handling

1. `BootReceiver` triggers on `BOOT_COMPLETED`
2. Restarts `WakeWordService` if the user has the wake word enabled.
3. *Limitation*: Because data lives in D1, pending alarms and geofences are cleared by Android on reboot and are not automatically re-registered by the BootReceiver. They are re-synced from the network the next time the user opens the app.

### 17.6 App Lifecycle

| Event | Behavior |
|-------|----------|
| App opened | Load last 50 messages, show chat screen, voice orb in `Idle` |
| App backgrounded | Chat state preserved, services continue |
| App killed (swipe away) | Services continue (foreground service for wake word, system for geofences/alarms) |
| App force-stopped | All services killed. Reminders still fire via AlarmManager. Wake word stops. Geofences may be cleared. |
| Low memory | Android may kill wake word service. Restarted via `START_STICKY` |

---

## 18. Third-Party SDK Integration

### 18.1 Vosk — Wake Word (Grammar-Mode Keyword Spotting)

| Aspect | Detail |
|--------|--------|
| Package | `com.alphacephei:vosk-android:0.3.75` |
| Keyword | Grammar-mode recognizer: `["hi recall", "hey recall", "[unk]"]` |
| Sensitivity | 0.3–0.7 (configurable) |
| Model | `vosk-model-small-en-us-0.15` (~40 MB) bundled in `assets/model-en-us/` |
| Runtime | Runs in `WakeWordService` foreground service via `VoskWakeWordEngine` |
| Permissions | `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE` |
| License | Apache 2.0 — fully free, no API key required |

### 18.2 Google Places REST API (via Cloudflare)

| Aspect | Detail |
|--------|--------|
| Integration | REST API called securely via Cloudflare Worker (`worker/src/tools/places.ts`) |
| API used | `https://places.googleapis.com/v1/places:searchText` (Text Search New) |
| API key | Required in `GOOGLE_PLACES_API_KEY` worker secret |
| Billing | Pay-per-use after free tier ($200/month credit) |
| Rate limit | Error responses are surfaced elegantly to the user through the LLM ("API error, try again later") |

### 18.3 Google Play Services — Location & Geofencing

| Aspect | Detail |
|--------|--------|
| Package | `com.google.android.gms:play-services-location` |
| Geofencing | `GeofencingClient` for registering/removing geofences |
| Location | `FusedLocationProviderClient` for current position (used as Places API bias) |
| Permissions | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` |

### 18.4 Android Speech APIs

| Aspect | Detail |
|--------|--------|
| Speech-to-Text | `android.speech.SpeechRecognizer` — system-provided, no additional SDK |
| Text-to-Speech | `android.speech.tts.TextToSpeech` — system-provided |
| No additional cost | Uses Android built-in engines |



## 19. Screen Inventory (Complete)

> **Status: ✅ Done.** All routes match `navigation/Routes.kt` and every screen composable exists in `ui/screens/`.

Total: **17 routes** across 5 screen groups + 4 background services.

### Onboarding — 4 screens

| # | Route | Screen Name | Composable |
|---|-------|-------------|------------|
| 1 | `onboarding/welcome` | Welcome | `WelcomeScreen` |
| 2 | `onboarding/permissions` | Permissions Setup | `PermissionsScreen` |
| 3 | `onboarding/wake-word-setup` | Wake Word Test | `WakeWordSetupScreen` |
| 4 | `onboarding/ready` | Ready | `ReadyScreen` |

### Chat — 1 screen (primary)

| # | Route | Screen Name | Composable |
|---|-------|-------------|------------|
| 5 | `chat/index` | Conversation (Home) | `ChatScreen` |

### Reminders — 4 routes (3 composables)

| # | Route | Screen Name | Composable |
|---|-------|-------------|------------|
| 6 | `reminders/index` | Reminder List (Active / Recurring / History) | `RemindersScreen` |
| 7 | `reminders/detail/{id}` | Reminder Detail | `ReminderDetailScreen` |
| 8 | `reminders/new` | New Reminder Form | `ReminderFormScreen` |
| 9 | `reminders/edit/{id}` | Edit Reminder Form | `ReminderFormScreen` (shared) |

### Nicknames — 2 screens

| # | Route | Screen Name | Composable |
|---|-------|-------------|------------|
| 10 | `nicknames/index` | Saved Places | `NicknamesScreen` |
| 11 | `nicknames/edit/{id}` | Edit Place | `NicknameEditScreen` |

### Settings — 6 screens

| # | Route | Screen Name | Composable |
|---|-------|-------------|------------|
| 12 | `settings/index` | Settings | `SettingsScreen` |
| 13 | `settings/wake-word` | Wake Word Settings | `WakeWordSettingsScreen` |
| 14 | `settings/voice` | Voice & Speech Settings | `VoiceSettingsScreen` |
| 15 | `settings/notifications` | Notification Settings | `NotificationSettingsScreen` |
| 16 | `settings/location` | Location Settings | `LocationSettingsScreen` |
| 17 | `settings/about` | About Recall | `AboutScreen` |

### Background Services (not screens, but user-visible)

| # | Component | Description |
|---|-----------|-------------|
| — | `WakeWordService` | Foreground service with persistent notification |
| — | `GeofenceBroadcastReceiver` | Receives geofence transition broadcasts |
| — | `ReminderReceiver` | Fires time-based reminder notifications |
| — | `BootReceiver` | Restarts the `WakeWordService` after device reboot |
