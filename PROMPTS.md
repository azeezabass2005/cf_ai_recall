# Recall, Build Prompts

A record of the prompts I used to build this project, phase by phase. Written in plain English, no padding, no over-explanation.

---

## Phase 1, Architecture, Navigation & Project Scaffold

I'm building a voice-first reminder app called Recall. The backend runs on Cloudflare Workers using TypeScript and the Cloudflare Agents SDK, one Durable Object per user for conversation state, D1 for persistent storage. The Android client is Kotlin with Jetpack Compose and Material3.

The core idea: user speaks or types a reminder in natural language, the Worker runs an LLM tool-use loop to parse intent and create the reminder in D1, then returns a conversational reply. No regex parsers, the LLM calls tools to affect the world.

Set up the full project scaffold:

- `worker/`, Hono router, `RecallAgent` Durable Object stub, `wrangler.toml` with D1 and DO bindings, stubs for `POST /chat`, `GET /health`, and the `/reminders` REST routes
- `app/`, Gradle files, `AndroidManifest.xml`, `MainActivity.kt` (single-activity `NavHost`), and the full directory structure: `navigation/`, `ui/theme/`, `ui/components/`, `ui/screens/`, `service/`, `network/`, `voice/`, `location/`, `data/`
- `NavGraph.kt` + `Routes.kt` with all routes defined as constants, onboarding, chat, reminders, nicknames, settings. Only onboarding composables need real stubs; everything else can be a `TODO()` placeholder
- Design system, `Color.kt`, `Type.kt`, `Theme.kt`, `Spacing.kt`, `Shape.kt` in `ui/theme/`. Warm, neutral dark palette, approachable, not clinical

Auth is anonymous: each device generates a UUID on first launch, persists it in DataStore, and sends it as `X-Recall-Device` on every request.

Also check the SPEC and confirm whether the AI agent and tool-calling architecture is accounted for. We originally planned a Rust backend but I'm switching to TypeScript on Cloudflare Workers so we can ship the MVP faster, Cloudflare has a TS Agents SDK, not a Rust one. I'll port to Rust later myself. Update the SPEC to reflect this.

---

## Phase 2, Onboarding & Permissions

Implement the four-screen onboarding flow.

**WelcomeScreen**, hero layout, app name, tagline, single "Get Started" CTA.

**PermissionsScreen**, a card for each permission the app needs: Microphone (required), Notifications (required), Fine Location (optional), Background Location (optional), Battery Optimization Exemption (recommended). Each card shows grant state and offers an "Open Settings" deep-link if permanently denied. The Continue button stays disabled until both required permissions are granted. Use the Activity Result API.

**WakeWordSetupScreen**, integrates the real Vosk grammar-mode recognizer so the user can test "Hi Recall" live before continuing. Large `VoiceOrb` in Listening state, sensitivity slider (Low/Medium/High), 30-second timeout with a retry option. On successful detection, the orb transitions to Speaking state and Recall says "I'm here! That worked perfectly." Include a Skip option for users who don't want wake word.

**ReadyScreen**, confirmation screen with three sample prompt bubbles and a "Start Using Recall" CTA that starts `WakeWordService`, navigates to `chat/index`, and clears the onboarding back stack.

**First-launch routing**, `MainActivity` checks a DataStore flag on startup to decide between `onboarding/welcome` and `chat/index`. Flag is written when onboarding completes.

---

## Phase 3, Conversation Interface

Build `ChatScreen` and `ChatViewModel`.

Three zones:

1. **Top bar**, "Recall" title centered, reminders list icon on the right, `+` icon for creating a reminder, settings gear. Stub snackbars for now, wired to real destinations in later phases.
2. **Chat area**, `LazyColumn` of `ChatBubble` components (user right-aligned, Recall left-aligned), newest at the bottom, auto-scrolls on new messages. `TypingIndicator` while a request is in flight.
3. **Input bar**, text field with a mic toggle on the left and send on the right. Tapping mic replaces the bar with a `VoiceOrb` UI in Listening state, with partial transcript below and a keyboard icon to switch back.

`ChatViewModel` holds the message list as `SnapshotStateList`, sends to `RecallApi.chat()`, appends responses, and exposes `isProcessing`. Pre-populate with Recall's welcome message on first launch.

`VoiceOrb` supports four states, Idle, Listening, Processing, Speaking, with a pulse animation in Listening state. Wire the real `SpeechRecognizerManager` so voice input actually transcribes and feeds into the same send path as text.

---

## Phase 4, Voice Input & Wake Word

Implement the voice pipeline.

**SpeechRecognizerManager**, wraps `android.speech.SpeechRecognizer` with a clean Kotlin API. `startListening()` / `stopListening()`, a `partialResults` flow for live transcript display, a `finalResult` flow for the completed transcription. Handle all error codes gracefully, emit a user-readable string, not a raw int.

**TextToSpeechManager**, wraps `TextToSpeech` with a `speak(text)` suspend function that waits for the utterance to finish. Slightly faster than default speech rate (0.95×). `VoiceOrb` enters Speaking state while TTS is active.

**In-app wake word**, runs only while the app is open. When "Hi Recall" is detected, it triggers voice mode. No background service, purely an in-app convenience for hands-free interaction while the app is visible.

---

## Phase 5, Time-Based Reminders

Implement end-to-end time-based reminders, from "remind me to call mum at 7pm" to the notification firing at the right time.

**Worker:**

D1 schema, `reminders` table with id, device_id, body, status, trigger JSON, recurrence JSON, fire_count, next_fire_at, created_at, updated_at. Typed D1 query helpers in `db/queries.ts`.

`RecallAgent` LLM tool-use loop using the Agents SDK. Tools: `create_reminder`, `list_reminders`, `update_reminder`, `delete_reminder`, `pause_reminder`, `resume_reminder`, `resolve_time`. The LLM must never confirm a reminder was set without calling `create_reminder` first.

Timezone-aware time parser in `worker/src/lib/time.ts`, handles expressions like "at 7pm", "in 15 minutes", "every weekday at 8am", "next Tuesday at noon". Bare hours (e.g. "3:45" with no AM/PM) pick the nearest future occurrence. All times stored in UTC, displayed in the device timezone via `X-Recall-Tz` header.

**Android:**

`ReminderScheduler`, wraps `AlarmManager.setExactAndAllowWhileIdle()`. Uses `USE_EXACT_ALARM` on API 33+ with a fallback to `SCHEDULE_EXACT_ALARM` plus a settings deep-link on older versions.

`ReminderReceiver`, fires when an alarm goes off. Posts a heads-up notification (IMPORTANCE_HIGH, sound, vibration). For recurring reminders, computes and schedules the next occurrence locally, no network needed to keep recurring reminders alive.

Wire an exact alarm permission card into the onboarding `PermissionsScreen`.

---

## Phase 6, Reminder Management Screens & Notification Actions

Skipping Milestones 5, 6, and 7 (location) for now, I'll come back once I have the API credentials. Do Milestones 8 and 9.

**Milestone 8, Reminder management screens:**

The `/reminders` REST endpoints currently return 501. Replace them with real handlers that reuse the existing functions in `worker/src/tools/reminders.ts`. Add `POST /reminders/:id/snooze`, sets `snoozed_until = now + N minutes`, flips status to "snoozed", recalculates `next_fire_at`. Each handler reads device ID from `X-Recall-Device` and timezone from `X-Recall-Tz`.

Extend `RecallApi.kt` with the full CRUD set: `listReminders(filter)`, `createReminder`, `updateReminder`, `deleteReminder`, `pauseReminder`, `resumeReminder`, `snoozeReminder`. All send `X-Recall-Device` and `X-Recall-Tz`.

Four screens:

- **RemindersScreen**, three tabs (Active / Recurring / History) with `TabRow`, `LazyColumn` of `ReminderCard` per tab, swipe-to-delete with undo snackbar, pull-to-refresh, empty states, `+` FAB, long-press action menu. Card tap → detail screen.
- **ReminderDetailScreen**, full detail: body, trigger, recurrence, countdown, metadata (created, fire count, source). Action row: Edit, Pause/Resume, Delete with confirmation.
- **ReminderFormScreen**, shared create/edit screen distinguished by route param. Body field (max 200 chars), date picker, time picker, repeat toggle, frequency chips (daily/weekly/monthly), weekday picker for weekly, interval stepper. Live preview card at the bottom. Submits via `createReminder` or `updateReminder`.
- **ReminderCard**, reusable component showing body, trigger pill, recurrence pill, status badge, countdown.

Wire into `NavGraph.kt`. Connect the chat top-bar buttons to navigate to `RemindersIndex` and `RemindersNew` instead of the stub snackbars.

**Milestone 9, Notification actions:**

Add **Done** and **Snooze 10 min** action buttons to every reminder notification. Create `ReminderActionReceiver`, Done dismisses the notification and cancels the alarm; Snooze reschedules the alarm for now + 10 minutes and shows a brief "Snoozed until HH:MM" notification that auto-dismisses after 5 seconds. Register the new receiver in `AndroidManifest.xml`. Upgrade the notification channel to use alarm-category audio attributes and vibration.

---

## Phase 7, Settings & Preferences + Worker Architecture Docs

**Settings screens:**

Extend `OnboardingPrefs.kt` with preference keys for: TTS enabled/speed/language, notification sound/vibration/heads-up/snooze duration/re-notify/re-notify interval, geofence radius, and theme (light/dark/system). Add a `themeModeFlow()` returning `Flow<String>` so `MainActivity` can collect it and pass `darkTheme` to `RecallTheme` reactively.

Six settings screens:

- **SettingsScreen**, menu with rows for Wake Word, Voice & Speech, Notifications, Location, Saved Places, Chat History, Theme, About
- **WakeWordSettingsScreen**, enable/disable toggle, sensitivity slider (Low/Med/High), live test section using the wake word engine (only visible when enabled)
- **VoiceSettingsScreen**, TTS toggle, speed slider, language segmented buttons (English Nigeria / US / UK)
- **NotificationSettingsScreen**, Sound/Vibration/Heads-up toggles, snooze duration segments (5/10/15/30 min), re-notify toggle, interval picker that shows only when re-notify is on
- **LocationSettingsScreen**, live GPS status from `LocationManager`, geofence radius slider (50/100/200/500m), active geofence count info row
- **AboutScreen**, app version from `BuildConfig`, "Powered by Cloudflare Workers", licenses placeholder, privacy card

Wire all settings routes in `NavGraph.kt` and replace the stub snackbar on the chat settings icon with actual navigation.

**SPEC §14 rewrite:**

Replace the Rust/JNI section with the actual TypeScript Worker architecture: module responsibilities table, agent loop state machine (ASCII diagram), Worker HTTP API table, D1 schema overview.

---

## Phase 8, Location-Based Reminders

Implement end-to-end location-based reminders.

**Worker:**

`resolve_place` tool in `worker/src/tools/places.ts`, calls the Google Places Text Search (New) API, returns up to 5 results with name, address, lat/lng, place_id. Extend `create_reminder` to accept optional `lat`, `lng`, `place_name`, `place_id`, when present, the reminder trigger type is Location.

**Android:**

`GeofenceManager`, wraps `GeofencingClient`. `registerGeofence(reminderId, lat, lng, radiusMetres)` and `unregisterGeofence(reminderId)`. `GEOFENCE_TRANSITION_ENTER`, 100m default radius, 30-second responsiveness.

`GeofenceBroadcastReceiver`, receives transition events, fires a notification, removes the geofence.

`GeofencePrefs` for tracking registered geofence IDs locally. `LocationHelper` for fetching last known location to send as bias with chat requests.

Wire `play-services-location` in Gradle. When `ChatViewModel` receives a location-triggered reminder from the agent, call `GeofenceManager.registerGeofence` to set it up on-device.

---

## Phase 9, Location Disambiguation

**Worker:**

Update `resolve_place` to apply Haversine compaction, when all results are within 50m of each other, auto-select the highest prominence one. Otherwise return the top 3 as disambiguation options. Add a `disambiguation` field to the chat response payload when the agent can't auto-resolve. Zero results → agent asks user to be more specific.

**Android:**

`DisambiguationSheet`, Material3 `ModalBottomSheet` with tappable place cards (name + address). Tapping an option sends the selection back as a regular chat message. Wire it into `ChatScreen`, it appears whenever the response has a non-null `disambiguation` field.

---

## Phase 10, Location Nicknames

**Worker:**

Three tool handlers in `worker/src/tools/nicknames.ts`: `save_nickname` (UPSERT to D1), `get_nicknames` (list all for device), `delete_nickname` (by ID). REST routes in `worker/src/routes/nicknames.ts` for `GET /nicknames`, `POST /nicknames`, `DELETE /nicknames/:id`.

Update the agent system prompt: when the user mentions a possessive place ("my hostel", "home"), check existing nicknames first. If found, use the saved location. If not, ask for the real place name, resolve it, save it, then continue with the original reminder.

**Android:**

`NicknamesScreen`, list of saved nicknames with swipe-to-delete and an empty state explaining how nicknames work.

`NicknameEditScreen`, nickname text field + place search that calls `GET /resolve-place`. Saves via `POST /nicknames`.

`NicknamesViewModel` for CRUD state. Wire both screens into `NavGraph`. Connect "Saved Places" in Settings to navigate to `NicknamesScreen`.

Add `listNicknames`, `saveNickname`, `deleteNickname` to `RecallApi.kt`.

---

## Phase 11, Data Models & Typed Queries

Two things to fix: §15 in the SPEC is still in Rust pseudo-code from v1.0, and `db/queries.ts` was never created, all D1 queries are written inline.

**Create `worker/src/db/queries.ts`:**

Centralize every D1 query into one typed module. Export row interfaces (`ReminderRow`, `NicknameRow`, `MessageRow`, `DeviceRow`), row-to-domain converters, and CRUD functions for all four tables. `updateReminderFields` should accept a partial fields object so callers can patch any subset of columns with one function.

**Refactor tool handlers:**

Update `reminders.ts`, `nicknames.ts`, and `auth.ts` to import from `db/queries.ts` instead of writing raw SQL inline. No behavioral changes, purely structural.

**Rewrite SPEC §15:**

Replace Rust structs with the actual TypeScript interfaces from `worker/src/lib/types.ts`. Add `device_id` to all models. Replace the `ConversationContext` Rust struct with a note that conversation state lives in the `RecallAgent` Durable Object. Replace the schema section with the actual `0001_init.sql`. Add a §15.6 documenting the query helpers. Remove tables that don't exist in the real schema.

---

## Phase 12, Agent Tools & Intent Resolution Docs

§16 is titled "NLP & Intent Parsing" and describes a v1.0 regex pipeline. None of that exists, we use an LLM-first tool-calling agent. Rewrite §16 entirely.

Rename it to "Agent Tools & Intent Resolution". Four subsections: tool inventory table (all 10 tools with name, module, description, milestone), intent-to-tool mapping table (v1.0 intent → v1.1 tool calls with example inputs), system prompt rules, and body/time extraction. Replace the "Rust uses Levenshtein" section with the actual implementation: `selectReminderByMatch()` using `LIKE %…%` in `db/queries.ts`.

---

## Phase 13, Android-Specific Behaviors Docs

§17 has outdated assumptions from v1.0, offline parsing, all data in local SQLite. Scan the actual Android `service/` and `reminders/` code then update:

- §17.2, `RECEIVE_BOOT_COMPLETED` only restarts `WakeWordService`, not reminders
- §17.3, Offline behavior: text/voice input fails gracefully because parsing happens on the Cloudflare LLM, not on-device
- §17.4, Data privacy: reminder storage is Cloudflare D1, not local SQLite
- §17.5, Reboot handling: alarms and geofences are cleared on reboot and only rescheduled when the user opens the app, because the data source is D1 not local Room

---

## Phase 14, Third-Party SDK Docs

§18 has bleed-over from the v1.0 architecture:

1. Delete §18.5 Rust/NDK Toolchain entirely
2. Rewrite §18.2 Google Places SDK to "Google Places REST API (via Cloudflare)", the Android app has no Places SDK dependency, the Worker calls the REST API
3. Update §19 `BootReceiver` description, it restarts `WakeWordService` after reboot, not reminders

---

## Phase 15, Screen Inventory Verification

Cross-reference every route in `navigation/Routes.kt` and every composable in `ui/screens/` against the §19 tables:

- Add `{id}` path params to routes that use them
- Add a "Composable" column mapping routes to actual `.kt` names
- Note that `ReminderFormScreen` is shared between `reminders/new` and `reminders/edit/{id}`
- Fix background service class names to match actual code

---

## Phase 16, UI Refinement

I want the UI to feel clean, sleek, and mature, not like a generic Compose template.

Update the color tokens: OLED-friendly dark backgrounds for dark mode, crisper off-white for light mode, tuned Indigo primary and Violet accent that pop without being loud. Switch headings from Bold to SemiBold for a calmer visual hierarchy. Increase corner radii on panels and bubbles for a squircle feel, and increase inner padding on `ReminderCard` and `ChatBubble` components. Rework the `VoiceOrb` gradient so it has subtle 3D depth in both idle and active states rather than looking flat.

Update `PROMPTS.md` to record this phase.

---

## Phase 17, Nominatim Places Migration

The Google Places API requires a credit card on Google Cloud, too much friction. Replace the `handleResolvePlace` function in `worker/src/tools/places.ts` with the OpenStreetMap Nominatim Search API (`https://nominatim.openstreetmap.org/search`).

Pass the query via `q=...` and `format=json`. Nominatim has strict usage policies so include a unique `User-Agent` header (`RecallWorker/1.0`). Use `viewbox` and `bounded=0` for proximity bias from the incoming `bias_lat`/`bias_lng`. Map `place_id`, `display_name`, `lat`, `lon` to the existing `PlaceResult` interface.

Update `PROMPTS.md`.

---

## Phase 18, PocketSphinx Wake Word Migration

The Vosk wake word engine doesn't work, it never detects the phrase. Switch to PocketSphinx.

There's an example app in `Desktop/frontend-hassle/wakewordapp`, reference it for the integration pattern.

Bundle `pocketsphinx-android-5prealpha-release.aar` in `app/libs/` and reference it as a direct file dependency. Copy the acoustic model assets from the example app, the `en-us-ptm` folder and `words.dic`, into `app/src/main/assets/sync/models/` following the structure PocketSphinx's `Assets.syncAssets()` expects (it reads `assets.lst`, checks MD5s, and syncs to device storage on first run).

Create a `keywords.list` with both `hi recall` and `hey recall`. Write thresholds dynamically at runtime based on the user's sensitivity setting, output the file to `context.cacheDir` so it can be regenerated without touching bundled assets.

Replace `VoskWakeWordEngine` with `PocketSphinxWakeWordEngine` implementing the same `WakeWordEngine` interface. PocketSphinx manages its own audio thread, `start()` syncs assets and builds the recognizer on a background thread, `pause()` calls `recognizer.cancel()`, `resume()` restarts listening without rebuilding the model.

Delete `VoskWakeWordEngine.kt` and the Vosk model folder. Update the `createDefaultEngine` factory in `WakeWordEngine.kt`.

Update `PROMPTS.md`.
