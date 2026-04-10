
## Milestone 3 (§6 — Voice Input & Wake Word)

### M3.5. en-NG speech recognizer / TTS voice may not be installed on device

**Where:** `SpeechRecognizerManager.kt` and `TextToSpeechManager.kt`.

**How to fix:** mostly user-side, but make the failure graceful:
1. On the device, install the Nigerian English language pack:
   - Recognizer: Settings → System → Languages & input → Voice input → Google
     → Languages → English (Nigeria) → Download.
   - TTS: Settings → System → Languages → Text-to-speech output → Google's
     speech engine → Install voice data → English (Nigeria).
2. The current code already falls back to whatever the platform serves if
   en-NG is missing — no code change required, this is just a setup note.

---

## How to track progress on this file

When you finish a fix:
1. Delete the section (or move it under a `## Resolved` heading at the bottom
   if you want a record).
2. Update the matching status note in `SPEC.md` if it referenced the caveat.
3. Commit with a message like `caveats: resolve M3.x (description)`.

Anything not on this list either (a) lives in a future milestone's SPEC
section and will be addressed there, or (b) is intentional and not a caveat —
ask before changing.
