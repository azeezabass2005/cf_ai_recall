# Ranti — User Story

## What is Ranti?

Most reminder apps assume you'll remember to open them. Ranti doesn't.

Ranti is a voice-first reminder assistant that lives in the background of your phone. You talk to it the same way you'd talk to a person — no rigid commands, no specific syntax. You just say what you need, and Ranti figures out the rest. It understands time, it understands place, and it asks when it doesn't.

Voice is the *primary* way in, but it isn't the *only* way in. Ranti is also a real app you can open. When you can't speak — in a lecture, on a bus, in a meeting — you type. When you need to set up something that recurs every week, or browse what you already have, or tweak a reminder you set yesterday, you open the app and use it like any other app. Voice collapses the gap between thought and action; the app is for everything that needs more than a sentence.

It's built for everyone, but it's particularly useful if your brain has a habit of dropping things the moment something else comes up.

---

## The Problem

You're leaving your room in a hurry and you suddenly remember — "I need to return Temi's charger when I see her at the faculty". By the time you get to the faculty, that thought is gone. You only remember again when you're back in bed.

Or you put eggs on the gas and sit down to watch something, fully intending to check in 10 minutes. You don't.

The problem isn't laziness. It's that the moment between *thinking a thing* and *doing the thing to not forget it* is too long. By the time you open an app, navigate to "new reminder", type it out, set a time or location — you've either forgotten the details or just given up.

Ranti collapses that gap.

---

## Core User Stories

---

### 1. Wake word activation

**As a user**, I want to trigger Ranti without touching my phone, so that I can set reminders the moment I think of them — even mid-task.

**Scenario:**
Bolu is washing dishes. She suddenly remembers she needs to submit an assignment before 11pm.

She says: *"Hi Ranti"*

Ranti responds and pops up — even though the app wasn't open.

She says: *"Remind me to submit the GEG assignment before 11pm"*

Ranti confirms: *"Got it. I'll remind you at 10:45pm so you have time."*

**Acceptance criteria:**
- Wake word works when screen is on and app is in the background
- Response latency from wake word to active state is under 2 seconds
- User does not need to unlock the phone first

---

### 2. Location-based reminder with natural language

**As a user**, I want to set a reminder that triggers when I arrive at a specific place — using the name of that place, not coordinates.

**Scenario:**
Femi is at his hostel. A classmate left her book at his place.

He says: *"Hi Ranti, remind me to give Sade her book when I reach Faculty of Technology Lecture Theatre"*

Ranti looks up the location, finds it, sets a geofence, and confirms: *"Done. I'll remind you once you're within 100 metres of Faculty of Technology Lecture Theatre."*

Later, when Femi walks into the area — his phone buzzes: *"Give Sade her book."*

**Acceptance criteria:**
- Ranti resolves place names via Google Places API
- If one strong match is found, it confirms and sets the reminder
- If no match is found, it tells the user clearly
- Reminder fires when the user enters the geofenced area, even if the app is closed

---

### 3. Disambiguation — multiple location matches

**As a user**, I want Ranti to handle ambiguous location names gracefully instead of silently picking the wrong place or failing.

**Scenario:**
Amaka says: *"Remind me to buy data when I get to the plaza"*

Ranti finds three places matching "plaza" nearby.

If two of them are within 50 metres of each other, Ranti picks the more prominent one and confirms which it chose.

If they're spread across different areas, Ranti asks: *"I found a few places that could match — University Shopping Plaza, Bola Plaza on Ile-Ife Road, or OAU Main Gate Plaza. Which one did you mean?"*

Amaka says: *"The one on Ile-Ife Road"*

Ranti sets the reminder.

**Acceptance criteria:**
- Proximity threshold determines whether to auto-pick or ask
- Disambiguation prompt lists options clearly, max 3
- User can respond naturally — no need to say option numbers
- Ranti confirms the final choice before saving

---

### 4. User-defined location nicknames

**As a user**, I want to refer to places by my own names so I don't have to say the full official name every time.

**Scenario:**
Ranti asks: *"Where is your hostel?"*

Kemi says: *"Adekunle Fajuyi Hall of Residence"*

Ranti saves this. Next time Kemi says "my hostel", Ranti knows exactly where she means.

**Acceptance criteria:**
- Ranti detects when a place reference is ambiguous or personal ("my hostel", "my department", "home")
- It asks once, saves the mapping
- Future references to that nickname resolve without asking again
- User can update or delete saved nicknames

---

### 5. Time-based reminder

**As a user**, I want to set a reminder for a specific duration from now or a specific clock time, using plain language.

**Scenario A — duration:**
Dele puts water on the gas.

*"Hi Ranti, remind me to check the pot in 15 minutes"*

Ranti: *"Sure, I'll remind you at 3:42pm."*

At 3:42pm: *"Check the pot."*

**Scenario B — specific time:**
*"Remind me to call my mum at 7pm"*

Ranti: *"Okay, reminder set for 7pm — Call your mum."*

**Acceptance criteria:**
- Ranti understands relative time ("in 20 minutes", "in an hour") and absolute time ("at 6pm", "by 9am")
- Reminder fires on time, even if app is in background or screen is off
- Notification text matches what the user said, not a generic label

---

### 6. In-app manual entry

**As a user**, I want to type reminders directly in the app when I'm somewhere I can't speak out loud.

**Scenario:**
Tunde is in a lecture. He types: *"remind me to check if my SIWES form was signed when I get to the department office"*

The app processes this the same way it would a voice input and confirms.

**Acceptance criteria:**
- Text input is processed through the same agent pipeline as voice
- Response appears in a chat-like interface
- All features (location, time, disambiguation) work the same way over text

---

### 7. Recurring reminders

**As a user**, I want to set reminders that repeat on a schedule, so that I don't have to recreate the same reminder every week.

**Scenario A — voice:**
Halima has a class every Tuesday at 7pm. She says: *"Hi Ranti, every Tuesday remind me about my class at 6:50pm"*

Ranti confirms: *"Got it. Every Tuesday at 6:50 PM — your class. I'll keep reminding you each week until you tell me to stop."*

Every Tuesday at 6:50 PM, her phone buzzes with the reminder. The reminder isn't deleted after firing — it stays active for next Tuesday.

**Scenario B — in-app form:**
Halima opens Ranti and taps the "+" button to create a reminder manually. A form appears: she types "Class", picks "Time", picks "Repeats weekly", taps "Tuesday", sets the time to 6:50 PM, and saves.

**Scenario C — managing recurrence:**
At the end of the semester, Halima says: *"Cancel my Tuesday class reminder"* — and Ranti stops the recurrence entirely.

**Acceptance criteria:**
- Ranti understands recurrence patterns: "every day", "every Tuesday", "every weekday", "every week", "every month on the 1st", "every morning at 8"
- Recurring reminders can be created via voice OR via the in-app form
- A recurring reminder fires on schedule and re-arms itself for the next occurrence automatically
- The user can pause, resume, or stop a recurrence
- The reminder list clearly shows recurring reminders with a recurrence label (e.g. "Every Tuesday · 6:50 PM")
- Recurring reminders survive device reboots

---

### 8. In-app reminder builder

**As a user**, I want to create and edit reminders inside the app using a form, so that I can fine-tune the details when a one-line sentence isn't enough.

**Scenario:**
Bisi wants to set a reminder for her dentist appointment in three weeks at 2:30 PM at a specific clinic she's never been to. She opens Ranti, taps "+ New Reminder", picks "Time", selects the date from a calendar, picks the time, types the body ("Dentist appointment"), and optionally adds a location by searching for the clinic — saves.

She could have done this with voice ("remind me about my dentist appointment on April 30th at 2:30 PM at Smile Dental Clinic"), but the form makes it easier to pick an exact future date and verify the right clinic.

**Acceptance criteria:**
- The app has a "+ New Reminder" entry point reachable from the home screen and the reminder list
- The form supports: body text, trigger type (time / location / both), one-time vs recurring, date/time pickers, location search, and optional notes
- The same form is used for editing existing reminders
- Submitting the form creates a reminder via the same Rust core path as voice input

---

### 9. Reminder list and management

**As a user**, I want to see all my pending reminders and be able to delete or edit them.

**Scenario:**
Lara opens the app and sees her 4 upcoming reminders. She realises one of them — "buy glucose biscuits when I pass Shoprite" — is no longer needed.

She says: *"Hi Ranti, cancel the Shoprite reminder"*

Or she opens the list, swipes, deletes it.

**Acceptance criteria:**
- All active reminders are visible in a list with their trigger (time or location) clearly shown
- User can delete via voice command or in-app gesture
- Completed/fired reminders are moved to a history section, not deleted immediately

---

## What Ranti is not

- It's not a calendar app. It doesn't manage events or invites — though it can hold recurring time reminders.
- It's not a to-do list. It doesn't track tasks without triggers.
- It's not always listening to your conversations. The mic only activates on the wake word.
- It's not voice-only. There is a real app with screens, forms, and a reminder list. Voice is the fastest way in, but it's never the only way.

---

## Platform

- **Android first** (Kotlin UI, Rust core via NDK)
- **iOS later** (Swift UI, same Rust core)
- No web app

---

## Open questions (to resolve during build)

- What happens to location reminders when the user's GPS is off?
- How does Ranti behave when two reminders have the same or overlapping triggers?
- Should fired reminders re-notify if the user doesn't dismiss them?
- Battery optimisation — how aggressive should geofence polling be?