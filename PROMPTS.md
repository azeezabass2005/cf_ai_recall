# Ranti — Prompts

A record of the prompts that drove each phase of this build, from blank repository to a fully working voice-first reminder app. Written in plain English, with enough context that each prompt stands on its own.

> **Note:** All prompts below were refined and expanded using an LLM before being fed to the coding agent. The raw intent was human; the final wording was LLM-assisted.

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

> **Covers:** Milestone 3 — SpeechRecognizerManager, TextToSpeechManager, in-app wake word detection

---

Implement the full voice pipeline for Ranti.

**SpeechRecognizerManager** — wraps `android.speech.SpeechRecognizer` with a clean Kotlin API. Expose `startListening()` / `stopListening()`, a `partialResults` flow for live transcript display, and a `finalResult` flow that emits the full transcription when the user stops speaking. Handle all error codes gracefully, emitting the most user-friendly explanation as a string rather than a raw error int.

**TextToSpeechManager** — wraps `android.speech.tts.TextToSpeech` with a `speak(text)` suspend function that waits for the utterance to complete before returning. Choose a warm, slightly faster-than-default speech rate (0.95×). The `VoiceOrb` should enter `Speaking` state while TTS is active.

**In-App Wake Word** — the wake word engine runs only while the app is open. When "Hi Recall" is detected on the test/settings screen, it triggers voice mode. No background service — the wake word is purely an in-app convenience for hands-free interaction while the app is visible.

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


## Phase 6 — Reminder Management Screens & Notification Actions (§11 · §12)

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

---

## Phase 7 — Settings & Preferences + Worker Architecture Docs (§13 · §14)

> **Covers:** Milestone 10 — all settings screens; §14 rewritten from Rust to TypeScript Worker architecture

---

Implement the full Settings & Preferences milestone (§13) and replace the now-outdated §14 Rust Core Architecture section with the real TypeScript Worker architecture documentation.

**§13 — Settings screens:**

Extend `OnboardingPrefs.kt` (the existing `ranti_onboarding` DataStore) with 11 new preference keys covering voice settings (TTS enabled, speed, language), notification settings (sound, vibration, heads-up, snooze duration, re-notify, re-notify interval), location settings (geofence radius), and theme (light/dark/system). Add a reactive `themeModeFlow()` returning `Flow<String>` so `MainActivity` can collect it and pass `darkTheme` to `RantiTheme` without restarting the activity.

Build six screens in `ui/screens/settings/`:

- **SettingsScreen** — menu with 8 rows: Wake Word, Voice & Speech, Notifications, Location, Saved Places (navigates to a placeholder since Nicknames are a later milestone), Chat History (confirmation dialog, local-only clear), Theme (inline Light/Dark/System cycle, no sub-screen), and About. Uses a shared `internal SettingsRow` composable.
- **WakeWordSettingsScreen** — enable/disable toggle (calls `WakeWordService.start/stop`), Low/Med/High sensitivity slider (3-position, `steps=1`), and a live test section using `VoskWakeWordEngine` (same logic as the onboarding screen) — only visible when the toggle is on.
- **VoiceSettingsScreen** — TTS enabled toggle, speed slider (Slow/Normal/Fast, `steps=1`), and a `SingleChoiceSegmentedButtonRow` for language (English Nigeria / US / UK).
- **NotificationSettingsScreen** — Sound, Vibration, Heads-up toggles; snooze duration segmented buttons (5/10/15/30 min); Re-notify toggle; and an `AnimatedVisibility` interval picker (5/10/15 min) that appears only when Re-notify is on.
- **LocationSettingsScreen** — live GPS status read from `LocationManager` (green GpsFixed / red GpsOff icon + "Open settings" button when off), geofence radius using an index-as-float slider (50/100/200/500m via `valueRange=0f..3f, steps=2`), and a hardcoded "0 / 100 active geofences" info row.
- **AboutScreen** — app version from `BuildConfig`, "Powered by Cloudflare Workers", open-source licenses placeholder, and a privacy card.

Wire all settings routes in `NavGraph.kt` and remove the `showSnackbar("Settings — milestone §13")` stub from `ChatScreen`'s settings icon — the button now navigates directly.

**§14 — SPEC rewrite:**

Replace the Rust JNI/module section entirely with four subsections: Module Responsibilities table (all TypeScript files in `worker/src/`), Agent Loop State Machine (ASCII diagram: idle → load DO state → append user turn → Workers AI call → tool loop max 5 iterations → save DO state → return), Worker HTTP API table (all 12 endpoints), and D1 Schema Overview (4 tables: devices, reminders, messages, nicknames with indices).

---

## Phase 8 — Location-Based Reminders (§8)

> **Covers:** Milestone 5 — Google Places integration, geofence registration, location-triggered reminders

---

Implement end-to-end location-based reminders for Ranti — from the user saying "remind me to buy eggs when I get to Shoprite" to the phone notification firing when they arrive.

**Worker side:**

Create a `resolve_place` tool in `worker/src/tools/places.ts` that calls the Google Places Text Search (New) API with the user's query and a location bias based on their current coordinates (sent via the request). Return up to 5 results with name, formatted address, lat/lng, and place_id. Extend `create_reminder` to accept optional `lat`, `lng`, `place_name`, and `place_id` fields — when these are present, the reminder's trigger type is Location instead of Time.

**Android side:**

Build `GeofenceManager` — wraps the Google Play Services `GeofencingClient`. Expose `registerGeofence(reminderId, lat, lng, radiusMetres)` and `unregisterGeofence(reminderId)`. Use `GEOFENCE_TRANSITION_ENTER` with a default 100 m radius and 30-second responsiveness.

Build `GeofenceBroadcastReceiver` — receives geofence transition events, looks up the reminder by ID, fires a notification via `NotificationHelper`, and removes the geofence.

Build `GeofencePrefs` for tracking registered geofence IDs locally, and `LocationHelper` for fetching the user's last known location to send as bias with chat requests.

Wire `play-services-location` in Gradle. When `ChatViewModel` receives a reminder with a location trigger from the agent, it calls `GeofenceManager.registerGeofence` to set it up on-device.

---

## Phase 9 — Location Disambiguation (§9)

> **Covers:** Milestone 6 — multi-result place resolution, disambiguation UI, Haversine compaction

---

Implement the location disambiguation flow for Ranti.

**Worker side:**

Update the `resolve_place` tool to apply Haversine distance compaction: when all returned places are within 50 m of each other, auto-select the highest-prominence result. When results are spread apart, return the top 3 as disambiguation options. Add a `disambiguation` field to the chat response payload containing the place options (name, address, lat, lng, place_id) when the agent cannot auto-resolve.

When the agent encounters 0 results, it should respond conversationally asking the user to be more specific. When it encounters 2+ spread-apart results, it should ask the user to pick one and include the disambiguation data for the Android UI.

**Android side:**

Build `DisambiguationSheet` — a Material3 `ModalBottomSheet` that shows the disambiguation options as tappable cards (place name + address). When the user taps an option, the selection is sent back as a regular chat message (e.g. "Oja Oba") so the agent can match it and complete the reminder. The user can also type or speak their choice instead of tapping.

Wire `DisambiguationSheet` into `ChatScreen` — it appears whenever `ChatViewModel` receives a response with a non-null `disambiguation` field.

---

## Phase 10 — Location Nicknames (§10)

> **Covers:** Milestone 7 — nickname CRUD, agent nickname flow, nickname management screens

---

Implement the location nicknames feature for Ranti — personal names like "my hostel" or "home" that map to real places.

**Worker side:**

Add three tool handlers to `worker/src/tools/nicknames.ts`: `save_nickname` (UPSERT a nickname → place mapping in D1), `get_nicknames` (list all nicknames for the device), and `delete_nickname` (remove by ID). Build REST routes in `worker/src/routes/nicknames.ts` for `GET /nicknames`, `POST /nicknames`, and `DELETE /nicknames/:id` — these are used by the in-app management screens and bypass the LLM.

Update the agent system prompt so that when the user mentions a possessive place phrase ("my hostel", "my house", "home") the agent first checks existing nicknames via `get_nicknames`. If found, it resolves to the saved location. If not found, the agent asks the user for the real place name, resolves it via `resolve_place`, saves the mapping with `save_nickname`, and then proceeds with the original reminder.

**Android side:**

Build `NicknamesScreen` — a list of all saved nicknames as cards with the nickname label and resolved place name, plus swipe-to-delete. Empty state explains how nicknames work.

Build `NicknameEditScreen` — for creating a new nickname. Includes a nickname text field and a place search field that calls `GET /resolve-place` to find the real location. On save, calls `POST /nicknames`.

Build `NicknamesViewModel` to manage the CRUD state. Wire both screens into `NavGraph` and connect the "Saved Places" row in Settings to navigate to `NicknamesScreen`.

Add `listNicknames`, `saveNickname`, and `deleteNickname` methods to `RantiApi.kt`.

---

## Phase 11 — Data Models & Typed Queries (§15)

> **Covers:** §15 rewritten from Rust to TypeScript, centralized `db/queries.ts` module, tool handler refactor

---

The data models in §15 of the SPEC are still written in Rust pseudo-code from the v1.0 spec, but the actual implementation is TypeScript on Cloudflare Workers. Additionally, the `db/queries.ts` typed query helper module listed in §3's file structure was never created — all D1 queries are written inline inside the tool handlers and `auth.ts`. Fix both of these issues.

**Create `worker/src/db/queries.ts`:**

Centralize every D1 query the worker executes into a single typed module. Export row interfaces (`ReminderRow`, `NicknameRow`, `MessageRow`, `DeviceRow`), row-to-domain converters (`rowToReminder`, `rowToNickname`), and CRUD functions for all four tables: `insertReminder`, `selectReminderById`, `selectReminderByMatch`, `selectReminders`, `updateReminderFields`, `deleteReminder`, `upsertNickname`, `selectNicknames`, `selectNicknameByText`, `deleteNickname`, `touchDevice`, `insertMessage`, `selectMessages`. The `updateReminderFields` function should accept a partial fields object so callers can patch any subset of columns without writing separate UPDATE statements for each use case.

**Refactor tool handlers:**

Update `worker/src/tools/reminders.ts`, `worker/src/tools/nicknames.ts`, and `worker/src/lib/auth.ts` to import from `db/queries.ts` instead of writing raw SQL inline. Remove the local `ReminderRow`, `NicknameRow`, `Nickname`, `rowToReminder`, and `rowToNickname` definitions from the tool files since they now live in `queries.ts`. No behavioral changes — the refactor is purely structural.

**Rewrite §15 in SPEC.md:**

Replace all Rust `struct`/`enum` definitions with TypeScript `interface`/`type` definitions that match `worker/src/lib/types.ts` exactly. Add `device_id` to all models (the Rust spec omitted it because data was local; the TypeScript implementation includes it because D1 is shared). Replace §15.4 `ConversationContext` (Rust struct with `WaitingForLocationChoice` etc.) with a note that conversation state is held in the `RantiAgent` Durable Object. Replace §15.5 schema with the actual `0001_init.sql` (which includes the `devices` table and different index names). Add a new §15.6 documenting the query helpers table. Remove the `conversation_context` and `migrations` tables that don't exist in the real schema.

---

## Phase 12 — Agent Tools & Intent Resolution Docs (§16)

> **Covers:** §16 rewritten from v1.0 regex pipeline to LLM tool-calling architecture

---

§16 in the SPEC is titled "NLP & Intent Parsing" and describes a v1.0 rule-based regex pipeline with slot extraction and LLM fallback. None of this exists — the v1.1 architecture uses an LLM-first tool-calling agent (documented in §14, implemented in `worker/src/agent.ts`). Rewrite §16 entirely.

**Rename** the section from "NLP & Intent Parsing" to "Agent Tools & Intent Resolution" and add a status banner.

**§16.1 Tool Inventory** — Create a numbered table of all 10 tools (`create_reminder`, `list_reminders`, `delete_reminder`, `update_reminder`, `pause_reminder`, `resume_reminder`, `resolve_time`, `resolve_place`, `save_nickname`, `get_nicknames`) with columns: tool name, module, one-line description, and which SPEC milestone introduced it.

**§16.2 Intent Resolution via Tool Calling** — Replace the regex pipeline flowchart with a mapping table showing how each v1.0 intent (CreateReminder, DeleteReminder, ListReminders, EditReminder, AnswerDisambiguation, DefineNickname, Greeting/Unknown) maps to one or more tool calls in v1.1. Include example user inputs. Document that the tool-use loop runs up to 8 iterations per turn.

**§16.3 System Prompt Rules** — Document the key rules from the agent system prompt: tools before confirmation, time delegation to `lib/time.ts`, location flow (resolve_place first), nickname flow (get_nicknames → resolve → save), recurrence detection, concise replies, and context injection (timezone, local time, input mode).

**§16.4 Body & Time Extraction** — Replace the regex body-extraction rules table with a table showing how the LLM extracts body and time as tool arguments. Reference `parseTimeExpression()` and `parseRecurrence()` in `lib/time.ts`.

**§16.5 Fuzzy Matching for Management** — Rewrite from "Rust core uses Levenshtein distance" to the actual implementation: `selectReminderByMatch()` in `db/queries.ts` using `LIKE %…%` substring match.

---

## Phase 13 — Android-Specific Behaviors Docs (§17)

> **Covers:** Updating §17 offline assumptions to match v1.1 Cloudflare design

---

Section §17 of SPEC.md contains outdated assumptions from the v1.0 "local-first Rust core" design (e.g. offline parsing, all data in local SQLite). 

**Scan the Android codebase**, specifically `service/` and `reminders/`, to document the actual implementation. Then update §17:
- **§17.2:** Change `RECEIVE_BOOT_COMPLETED` permission so it only mentions restarting WakeWordService.
- **§17.3:** Update Offline Behavior table. Note that "No internet + text/voice input" fails gracefully because parsing is done by Cloudflare LLM agent.
- **§17.4:** Update Data Privacy table. 'Reminder storage' is in Cloudflare D1, not local SQLite.
- **§17.5:** Update Device Reboot Handling. Explicitly state the limitation that because data is in D1, alarms and geofences are cleared on reboot and not rescheduled automatically by `BootReceiver` until the app is opened.

---

## Phase 14 — Third-Party SDK Docs (§18)

> **Covers:** Removing abandoned v1.0 Rust architecture and Google Places SDK references

---

Section §18 in the SPEC contains massive bleed-over from the v1.0 architecture. Do the following:
1. **Delete §18.5 Rust / NDK Toolchain**: Remove the section entirely. It was replaced by Cloudflare Workers in v1.1.
2. **Rewrite §18.2 Google Places SDK**: Change to "Google Places REST API (via Cloudflare)". The Android app does not include the Google Places SDK; instead, the Cloudflare Worker calls the REST API via `worker/src/tools/places.ts`. Update the description to match this reality.
3. **§19 Background Services**: Update the `BootReceiver` description to reflect that it restarts the `WakeWordService` after reboot, rather than blindly rescheduling reminders.

---

## Phase 15 — Screen Inventory Verification (§19)

> **Covers:** Verifying §19 Screen Inventory against actual `Routes.kt` and screen files

---

§19 Screen Inventory needs to be verified against the actual project code. Cross-reference every route in `navigation/Routes.kt` and every composable in `ui/screens/` against the §19 tables. Specifically:
- Add `{id}` path parameters to routes that use them (`reminders/detail/{id}`, `reminders/edit/{id}`, `nicknames/edit/{id}`).
- Add a "Composable" column to each table mapping routes to their actual `.kt` composable names.
- Note that `ReminderFormScreen` is shared between `reminders/new` and `reminders/edit/{id}`.
- Fix background service class names to match actual code (`GeofenceBroadcastReceiver`, `ReminderReceiver`).

---

## Phase 16 — UI Refinement

> **Covers:** Updating theme tokens, components, and PROMPTS.md for a clean, sleek, and mature aesthetic.

---

I want you to improve all the UI of this mobile app, to be clean, sleek and mature. 
Update the Jetpack Compose color tokens to reflect a modern premium dark and light mode. 
Deepen the dark theme backgrounds to OLED-friendly darks, change the light theme base to a crisper off-white, and tune the primary Indigo and accent Violet to pop elegantly.
Adjust the Typography to rely on SemiBold instead of Bold for headings, creating a calmer visual hierarchy.
Increase corner radii on panels and bubbles for a smoother squircle aesthetic, and increase inner paddings across components like the ReminderCard and ChatBubbles.
Enhance the VoiceOrb with more sophisticated idle and active gradient glow styling, giving it a subtle 3D depth instead of a flat look.
Finally, do not forget to update this `PROMPTS.md` document to record this phase.

---

## Phase 17 — Nominatim Places Migration

> **Covers:** Replacing Google Places API with OpenStreetMap Nominatim to remove API key restrictions.

---

The Google Places API requirement is causing friction because it requires setting up a credit card in Google Cloud. I want to replace the `handleResolvePlace` function in `worker/src/tools/places.ts` entirely.

Instead of Google, call the **OpenStreetMap Nominatim Search API** (`https://nominatim.openstreetmap.org/search`).
Pass the query via `q=...` and restrict output to JSON with `format=json`. Since Nominatim has strict usage policies, ensure there is a unique `User-Agent` header sent with the `fetch` request (e.g. `RantiWorker/1.0`). To replicate the proximity bias setting originally provided by Google Places, use the `viewbox` and `bounded=0` parameters based on the incoming `bias_lat` and `bias_lng`.

Map the returned generic JSON fields (`place_id`, `display_name`, `lat`, `lon`) into the app's `PlaceResult` interface. Please document this decision and update `PROMPTS.md`.

---

## Phase 18 — PocketSphinx Wake Word Migration

> **Covers:** Replacing the non-functional Vosk-based in-app wake word engine with CMU PocketSphinx for reliable keyword spotting.

---

The current Vosk-based wake word engine is not working — it never detects the phrase. Switch to **PocketSphinx** for the in-app wake word instead of Vosk.

There is an example app cloned from GitHub that uses PocketSphinx — go one level out from this project folder into `Desktop/frontend-hassle` and you'll find a folder called `wakewordapp`. Learn from it.

Download `pocketsphinx-android-5prealpha-release.aar` and bundle it in `app/libs/` — reference it as `implementation(files("libs/pocketsphinx-android-5prealpha-release.aar"))`. No JitPack or Maven Central needed, just a direct file reference.

Copy the acoustic model assets from the wakewordapp — the `en-us-ptm` folder and the `words.dic` dictionary — into `app/src/main/assets/sync/models/` following the same structure PocketSphinx's `Assets.syncAssets()` mechanism expects. It reads `assets/sync/assets.lst`, checks MD5 hashes, and syncs changed files to device storage on first run.

Create a `keywords.list` file with both `hi recall` and `hey recall` as detection phrases. Write the threshold values dynamically at runtime based on the user's sensitivity setting and put the file in `context.cacheDir` so it can be regenerated without touching the bundled assets.

Replace `VoskWakeWordEngine` entirely with a new `PocketSphinxWakeWordEngine` implementing the same `WakeWordEngine` interface. PocketSphinx manages its own internal audio thread so there is no manual `AudioRecord` loop — `start()` should sync assets on a background thread and call `SpeechRecognizerSetup.defaultSetup()` to build the recognizer, `pause()` calls `recognizer.cancel()` to cleanly release the mic, and `resume()` calls `startListening` again without rebuilding the model.

Delete `VoskWakeWordEngine.kt` and the 68 MB `model-en-us/` Vosk asset folder. Update the `createDefaultEngine` factory in `WakeWordEngine.kt` to return the new engine.

Also update this `PROMPTS.md`.

