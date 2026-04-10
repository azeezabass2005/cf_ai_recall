# Ranti — Prompts

A record of the prompts that drove each phase of this build, from blank repository to a fully working voice-first reminder app. Written in plain English, with enough context that each prompt stands on its own.

---

## Phase 1 — Architecture, Navigation & Project Scaffold (§1 · §2 · §3)

> **Covers:** Architecture Overview, Navigation Architecture, File Structure & Screen Map

---

I want to build an Android voice-first reminder app called **Ranti**. The backend should run on Cloudflare Workers using TypeScript and the Cloudflare Agents SDK, with a Durable Object per user for conversation state and D1 (SQLite) for persistent storage. The Android client is Kotlin with Jetpack Compose and Material3.

The core idea: the user speaks or types a reminder in natural language, the Worker runs an LLM tool-use loop to parse the intent, creates the reminder in D1, and returns a conversational confirmation. No regex parsers — the LLM calls tools to affect the world.

Here is what I need from this first prompt:

1. **Scaffold the Worker project** in a `worker/` directory with Hono as the HTTP router, the Cloudflare Agents SDK wired up, a `RantiAgent` Durable Object stub, and a `wrangler.toml` with a D1 binding and a DO binding. The router should expose `POST /chat` (routes to the agent), `GET /health`, and stubs for `GET /reminders`, `POST /reminders`, `PATCH /reminders/:id`, `DELETE /reminders/:id`.

2. **Scaffold the Android project** in an `app/` directory with the Gradle files, `AndroidManifest.xml`, `MainActivity.kt` (single-activity, hosts a `NavHost`), and the full directory structure from the spec: `navigation/`, `ui/theme/`, `ui/components/`, `ui/screens/`, `service/`, `network/`, `voice/`, `location/`, `data/`.

3. **Wire the navigation graph** (`NavGraph.kt` + `Routes.kt`) with all routes defined as constants — onboarding (welcome, permissions, wake-word-setup, ready), chat/index, reminders (index, detail, new, edit), nicknames, and settings. Only the onboarding routes need real composable stubs at this stage; everything else can be a `TODO()` placeholder.

4. **Set up the design system** — `Color.kt`, `Type.kt`, `Theme.kt`, `Spacing.kt`, and `Shape.kt` in `ui/theme/`. Use a warm, neutral dark palette that feels approachable and calm, not clinical.

Identity is anonymous: each device generates a UUID on first launch, persists it in DataStore, and sends it as `X-Ranti-Device` on every request. No accounts, no login.

---

## Phase 2 — Onboarding & Permissions (§4)

> **Covers:** Milestone 1 — Welcome screen, Permissions screen, Wake Word Setup screen, Ready screen

---

Implement the full four-screen onboarding flow for Ranti.

**WelcomeScreen** — hero layout with the app name, tagline ("Remember everything. Say it once."), and a single "Get Started" button that navigates to the permissions screen.

**PermissionsScreen** — shows a card for each Android permission the app needs: Microphone (required), Notifications (required), Fine Location (optional, needed for location reminders), Background Location (optional), and Battery Optimization Exemption (recommended). Each card shows the current grant state — granted, not yet granted, or permanently denied with an "Open Settings" deep-link. The "Continue" button stays disabled until both required permissions (microphone and notifications) are granted. Use the Activity Result API for requesting permissions, not deprecated `requestPermissions`.

**WakeWordSetupScreen** — integrates the real Vosk grammar-mode recognizer (`VoskWakeWordEngine`) to let the user test the wake phrase "Hi Ranti" live before moving on. Show a large `VoiceOrb` in Listening state, a sensitivity slider (Low / Medium / High), and a 30-second timeout with a "Try again" option. On successful detection, the orb transitions to Speaking state and Ranti says "I'm here! That worked perfectly." A "Skip" option disables the wake word service for users who prefer to open the app manually.

**ReadyScreen** — confirmation screen with three sample prompts shown as `ChatBubble` previews ("Remind me to call mum at 7pm", etc.), and a "Start Using Ranti" CTA that starts `WakeWordService` and navigates to `chat/index`, clearing the onboarding back stack so the back button exits the app from chat.

**First-launch routing** — `MainActivity` checks a DataStore flag on startup to decide whether to start at `onboarding/welcome` or `chat/index`. The flag is written when the user completes onboarding.

---

## Phase 3 — Conversation Interface (§5)

> **Covers:** Milestone 2 — Chat screen, ChatViewModel, ChatBubble, VoiceOrb, message history

---

Build the primary conversation screen for Ranti (`ChatScreen` + `ChatViewModel`).

The screen has three zones:

1. **Top bar** — "Ranti" title centered, with a reminders list icon on the right, a `+` icon for creating a reminder directly, and a settings gear icon. These buttons can show stub snackbars for now — they will be wired to real destinations in later milestones.

2. **Chat area** — a `LazyColumn` of `ChatBubble` components (user bubbles right-aligned, Ranti bubbles left-aligned), newest at the bottom. Auto-scroll to the latest message whenever the list grows. Show a `TypingIndicator` (animated ellipsis bubble) while a network request is in flight.

3. **Input bar** — a text field with a mic toggle icon on the left and a send button on the right. Tapping the mic icon replaces the input bar with a `VoiceOrb` UI (orb in Listening state, partial transcript text below it, keyboard icon to switch back to text mode).

`ChatViewModel` holds the message list as a `SnapshotStateList`, sends user messages to `RantiApi.chat()`, appends the response as a Ranti bubble, and exposes an `isProcessing` flag for the typing indicator. On first launch (empty message list), pre-populate with Ranti's welcome message.

The `VoiceOrb` component should support four states — Idle, Listening, Processing, Speaking — with a visual pulse animation in Listening state. Wire up the real `SpeechRecognizerManager` so voice input actually transcribes speech and feeds it into the same send path as typed text.

---

## Phase 4 — Voice Input & Wake Word (§6)

> **Covers:** Milestone 3 — SpeechRecognizerManager, TextToSpeechManager, WakeWordService, BootReceiver

---

Implement the full voice pipeline for Ranti.

**SpeechRecognizerManager** — wraps `android.speech.SpeechRecognizer` with a clean Kotlin API. Expose `startListening()` / `stopListening()`, a `partialResults` flow for live transcript display, and a `finalResult` flow that emits the full transcription when the user stops speaking. Handle all error codes gracefully, emitting the most user-friendly explanation as a string rather than a raw error int.

**TextToSpeechManager** — wraps `android.speech.tts.TextToSpeech` with a `speak(text)` suspend function that waits for the utterance to complete before returning. Choose a warm, slightly faster-than-default speech rate (0.95×). The `VoiceOrb` should enter `Speaking` state while TTS is active.

**WakeWordService** — a foreground service (`FOREGROUND_SERVICE_MICROPHONE`) that runs `VoskWakeWordEngine` continuously. When "Hi Ranti" is detected, it should behave exactly like Bixby or Google Assistant: if the app is in the foreground, bring it to the front immediately in listening mode. If the phone is locked or the app is in the background, launch the activity directly (using the foreground service BAL exemption) — do not just post a notification and wait for a tap. Post a persistent notification ("Say 'Hi Ranti' anytime") to satisfy the foreground service requirement, and a brief heads-up notification ("Ranti heard you") only as a last resort if the direct launch is blocked.

**BootReceiver** — restarts `WakeWordService` after a device reboot if the user had it enabled when they last used the app.

Wire wake word activations into `ChatViewModel` via a `wakeEvents` `StateFlow<Int>` — the service bumps a counter, the screen observes and enters voice mode.

---

## Phase 5 — Time-Based Reminders (§7)

> **Covers:** Milestone 4 — Worker agent tool-use loop, D1 schema, time parser, AlarmManager, ReminderReceiver

---

Implement end-to-end time-based reminders — from the user speaking "remind me to call mum at 7pm" to the phone notification firing at the right moment.

**Worker side:**

Set up the D1 schema (`reminders` table with id, device_id, body, status, trigger JSON, recurrence JSON, fire_count, next_fire_at, created_at, updated_at). Write typed D1 query helpers in `db/queries.ts`.

Build the `RantiAgent` LLM tool-use loop using the Cloudflare Agents SDK. The agent should have these tools: `create_reminder`, `list_reminders`, `update_reminder`, `delete_reminder`, `pause_reminder`, `resume_reminder`, and `resolve_time`. The LLM must call a tool — it must never confirm a reminder was set without actually calling `create_reminder` first.

Write a timezone-aware time parser in `worker/src/lib/time.ts` that accepts natural language expressions like "at 7pm", "in 15 minutes", "every weekday at 8am", "every month on the 1st", and "next Tuesday at noon". The parser should correctly handle bare hours ("3:45" with no AM/PM specified — pick the nearest future occurrence, not a hardcoded AM or PM assumption). All times stored in UTC, all user-facing times shown in the device's local timezone (sent as `X-Ranti-Tz` on every request).

**Android side:**

`ReminderScheduler` — wraps `AlarmManager.setExactAndAllowWhileIdle()`. Schedule alarms using `USE_EXACT_ALARM` (API 33+, auto-granted for reminder apps) with a fallback to `SCHEDULE_EXACT_ALARM` plus a settings deep-link for older API levels.

`ReminderReceiver` — a `BroadcastReceiver` that fires when an alarm goes off. Post a heads-up notification (channel: "Ranti Reminders", IMPORTANCE_HIGH, with sound and vibration). For recurring reminders, compute and schedule the next occurrence locally without needing the network — this keeps recurring reminders alive even if the phone was offline.

Wire an exact alarm permission card into the onboarding `PermissionsScreen` so users are guided to grant it before setting their first reminder.

---

## Phase 6 — Wake Word Reliability & Direct Launch (post-§6 refinement)

> **Covers:** Fixes after real-world testing — mic always active, sensitivity issues, Bixby-style direct launch

---

After testing Ranti on a Samsung device I noticed three issues that need to be fixed before this is production-ready.

**First,** the microphone is always open, which prevents Bixby and Google Assistant from working properly. Unlike those assistants, Ranti's `VoskWakeWordEngine` holds the `AudioRecord` open continuously with no duty cycling. I need the engine to listen continuously but release the mic if another app requests it — use `AudioRecord`'s conflict callback or a similar mechanism. Alternatively, if the current Vosk grammar-mode approach is simply too aggressive, let me know and propose a better free alternative.

**Second,** the wake phrase sensitivity is noticeably lower than Bixby or Google — I sometimes have to say "Hi Ranti" twice. The Vosk grammar model should match the phrase if any of the recognized words form a substring of the wake phrase (for medium and high sensitivity), rather than requiring an exact token match. For low sensitivity, keep exact matching. Also add negative phrase rejection so common filler words alone don't trigger a false positive.

**Third,** when Ranti detects the wake word it only shows a notification requiring a tap — it should launch directly into listening mode the way Bixby does, even from the lock screen. Use the foreground service BAL exemption to call `startActivity()` directly. If that is blocked (lock screen, no activity focus), post the heads-up notification as a fallback only, and auto-dismiss it after 10 seconds so it does not clutter the notification shade.

Please fix all three issues. Keep the duty-cycle removal simple — if the architecture needs to change significantly, tell me what the trade-off is before rewriting it.

---

## Phase 7 — Reminder Management Screens & Notification Actions (§11 · §12)

> **Covers:** Milestone 8 — in-app reminder list, detail, and form; Milestone 9 — Done/Snooze notification actions

---

We are going to skip Milestones 5, 6, and 7 (location-based reminders) for now — I will come back to those once I have the necessary API credentials. For now, implement Milestones 8 and 9.

**Milestone 8 — Reminder Management:**

The Worker's `/reminders` REST endpoints currently return 501. Replace the stubs with real handlers that reuse the existing tool functions in `worker/src/tools/reminders.ts`. Add a `POST /reminders/:id/snooze` endpoint that sets `snoozed_until = now + N minutes`, flips status to "snoozed", and recalculates `next_fire_at`. Each handler should extract the device ID from the `X-Ranti-Device` header and the timezone from `X-Ranti-Tz`.

Extend `RantiApi.kt` with the full CRUD method set: `listReminders(filter)`, `createReminder`, `updateReminder`, `deleteReminder`, `pauseReminder`, `resumeReminder`, and `snoozeReminder`. All methods send `X-Ranti-Device` and `X-Ranti-Tz` headers.

Build four screens and their supporting ViewModels:

- **RemindersScreen** — three tabs (Active / Recurring / History) with `TabRow`, a `LazyColumn` of `ReminderCard` items per tab, swipe-to-delete with a red delete background, pull-to-refresh, empty states per tab, a `+` FAB for creating a new reminder, and a long-press action menu (Pause/Resume for recurring reminders, Delete). Tapping a card navigates to the detail screen.

- **ReminderDetailScreen** — shows the full reminder: body text, trigger info (time with formatted date, or location name), recurrence summary, a live countdown for pending time-based reminders, and metadata (created date, fire count, source). Action row at the bottom: Edit button, Pause/Resume toggle (recurring only), Delete button with a confirmation dialog.

- **ReminderFormScreen** — shared screen for both creating and editing a reminder (the route parameter distinguishes the two modes). Sections: body text field (max 200 characters), date picker (Material3 `DatePickerDialog`), time picker (Material3 `TimePicker` in a dialog), a "Repeat this reminder" toggle, and — when repeat is on — frequency chips (daily / weekly / monthly), a weekday picker for weekly reminders, and an interval stepper. Show a live preview `ReminderCard` at the bottom of the form as the user fills it in. On submit, build a natural-language time expression and call `createReminder` or `updateReminder`.

- **ReminderCard** — reusable component used by the list, detail preview, and form live preview. Shows body text, a trigger pill (time or location), a recurrence pill if the reminder repeats, a status badge (Active / Paused / Snoozed / Fired / Done), and a countdown for pending time reminders.

Wire all four screens into `NavGraph.kt` and connect the chat screen's top-bar buttons to navigate to `RemindersIndex` and `RemindersNew` instead of showing stub snackbars.

**Milestone 9 — Notification & Firing Upgrade:**

Upgrade `ReminderReceiver.showNotification()` to include two action buttons on every reminder notification: **"Done"** and **"Snooze 10 min"**. Create a new `ReminderActionReceiver` `BroadcastReceiver` that handles both actions — Done dismisses the notification and cancels the alarm; Snooze reschedules the alarm for now plus 10 minutes, dismisses the current notification, and shows a brief "Snoozed until HH:MM" notification that auto-dismisses after 5 seconds. Register the new receiver in `AndroidManifest.xml`. Upgrade the notification channel to use alarm-category audio attributes and enable vibration.
