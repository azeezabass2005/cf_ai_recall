# Ranti Design System

Comprehensive guide to Ranti's visual language, design tokens, and component library. This document is the single source of truth for building consistent, high-quality screens across the app.

---

## Table of Contents

1. [Design Philosophy](#design-philosophy)
2. [Color System](#color-system)
3. [Typography](#typography)
4. [Spacing](#spacing)
5. [Border Radius](#border-radius)
6. [Shadows](#shadows)
7. [Iconography](#iconography)
8. [Theming](#theming)
9. [Components](#components)
   - [Text](#text)
   - [Button](#button)
   - [Card](#card)
   - [Badge](#badge)
   - [Avatar](#avatar)
   - [Input](#input)
   - [ChatBubble](#chatbubble)
   - [VoiceOrb](#voiceorb)
   - [ReminderCard](#remindercard)
   - [TimePill](#timepill)
   - [LocationPill](#locationpill)
   - [DisambiguationSheet](#disambiguationsheet)
   - [CountdownRing](#countdownring)
   - [Toggle](#toggle)
   - [Divider](#divider)
   - [Toast](#toast)
10. [Animation](#animation)
11. [Z-Index](#z-index)
12. [Usage Guidelines](#usage-guidelines)

---

## Design Philosophy

Ranti is a **calm, invisible assistant** that only demands attention when it matters. The design language reflects this: soft, breathable, and warm. Nothing screams. Nothing competes for your eye. The interface stays out of the way until a reminder is due, then it speaks clearly and leaves.

**Three design principles:**

1. **Conversational, not transactional.** The UI feels like a chat with a thoughtful friend, not a settings panel. Rounded shapes, natural spacing, and a voice-first layout reinforce this.
2. **Warm and grounded.** Deep indigo and soft violet anchor the brand. Backgrounds are muted, never clinical white or pitch black. The palette evokes late evening — the time you're most likely to forget things.
3. **One typeface, one voice.** Plus Jakarta Sans across all weights. Clean, geometric, friendly. No decorative fonts, no serif fallbacks.

**Font:** Plus Jakarta Sans across all weights (400 Regular through 800 ExtraBold). One family, no exceptions.

---

## Color System

All color values are defined in `core/design/tokens.kt` (Kotlin) and mirrored in the Rust core as constants for any shared logic that references color semantics.

### Backgrounds

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| base | `#F4F1EB` | `#0E0D12` | Page background |
| surface | `#FFFDF7` | `#16141E` | Card, sheet, chat backgrounds |
| elevated | `#FFFFFF` | `#1E1B28` | Modals, bottom sheets, popovers |
| input | `#F0EDE5` | `#1A1824` | Text input field backgrounds |
| border | `#E0DAC9` | `#2A2636` | Standard borders |
| borderSubtle | `#EAE5D6` | `#211E2C` | Subtle dividers |

### Text

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| hi | `#1A1724` | `#F4F1EB` | Primary text, headings, reminder body |
| mid | `#6B6380` | `#9B93B0` | Secondary text, labels, timestamps |
| lo | `#A59DB8` | `#4E4760` | Muted text, hints, disabled |

### Brand: Indigo (Primary)

Ranti's primary brand color. Used for the voice orb, primary actions, and active states.

| Token | Value | Usage |
|-------|-------|-------|
| primary | `#5B4CDB` | Voice orb resting state, primary CTA, links |
| primaryDeep | `#4A3BBF` | Pressed / active state |
| primarySoft | `#ECEAFC` | Light mode ghost backgrounds |
| primarySoftDk | `#1C1935` | Dark mode ghost backgrounds |

### Brand: Violet (Accent)

Used sparingly for active/listening states, highlights, and premium touches.

| Token | Value | Usage |
|-------|-------|-------|
| accent | `#8B5CF6` | Voice orb active/listening ring, accents |
| accentDeep | `#7C3AED` | Pressed / active state |
| accentSoft | `#F0ECFF` | Light mode accent backgrounds |
| accentSoftDk | `#1E1538` | Dark mode accent backgrounds |

### Semantic: Reminder Triggers

| Token | Value | Usage |
|-------|-------|-------|
| timeTrigger | `#E08A3E` | Time-based reminder icon, pills, accents |
| timeTriggerSoft | `#FDF3E7` | Time pill background (light) |
| timeTriggerSoftDk | `#1E1610` | Time pill background (dark) |
| locationTrigger | `#2EA87A` | Location-based reminder icon, pills, accents |
| locationTriggerSoft | `#E8F8F1` | Location pill background (light) |
| locationTriggerSoftDk | `#0E1E18` | Location pill background (dark) |

### Status

| Token | Value | Usage |
|-------|-------|-------|
| success | `#22C55E` | Reminder delivered, confirmed |
| successBg | `#F0FDF4` / `#0D2018` | Success backgrounds |
| warning | `#F59E0B` | Reminder expiring soon |
| warningBg | `#FFFBEB` / `#201A08` | Warning backgrounds |
| error | `#EF4444` | Failed delivery, missed reminder |
| errorBg | `#FEF2F2` / `#200F0F` | Error backgrounds |

### Overlays

| Token | Value | Usage |
|-------|-------|-------|
| overlay.light | `rgba(244, 241, 235, 0.85)` | Light mode backdrop |
| overlay.dark | `rgba(14, 13, 18, 0.85)` | Dark mode backdrop |
| overlay.scrim | `rgba(0, 0, 0, 0.5)` | Universal scrim |

---

## Typography

**Font Family:** Plus Jakarta Sans (Google Fonts)

### Weights

| Weight | Font Name | Token |
|--------|-----------|-------|
| 400 Regular | `PlusJakartaSans-Regular` | `fontWeight.regular` |
| 500 Medium | `PlusJakartaSans-Medium` | `fontWeight.medium` |
| 600 SemiBold | `PlusJakartaSans-SemiBold` | `fontWeight.semibold` |
| 700 Bold | `PlusJakartaSans-Bold` | `fontWeight.bold` |
| 800 ExtraBold | `PlusJakartaSans-ExtraBold` | `fontWeight.extrabold` |

### Type Scale

| Variant | Size | Line Height | Default Weight | Usage |
|---------|------|-------------|----------------|-------|
| `display` | 36px | 44px | ExtraBold | Splash, onboarding hero |
| `h1` | 28px | 36px | Bold | Screen titles |
| `h2` | 24px | 32px | Bold | Section headers |
| `h3` | 20px | 28px | SemiBold | Card titles, dialog headers |
| `body-lg` | 18px | 28px | Regular | Chat bubble (Ranti's replies) |
| `body-md` | 16px | 24px | Regular | Default body text, user input |
| `body-sm` | 14px | 22px | Regular | Secondary text, reminder details |
| `caption` | 12px | 18px | Medium | Timestamps, trigger labels, pill text |
| `label` | 11px | 16px | SemiBold | Badges, tiny labels |

---

## Spacing

Consistent spacing scale used across padding, margins, and gaps.

| Token | Value |
|-------|-------|
| `xxs` | 2px |
| `xs` | 4px |
| `sm` | 8px |
| `md` | 12px |
| `base` | 16px |
| `lg` | 20px |
| `xl` | 24px |
| `xxl` | 32px |
| `xxxl` | 40px |
| `huge` | 48px |
| `giant` | 64px |

---

## Border Radius

Rounded, approachable corners throughout. The conversational aesthetic demands soft shapes.

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 4px | Tiny elements |
| `sm` | 8px | Badges, pills |
| `md` | 12px | Input fields, small cards |
| `lg` | 16px | Cards, sheets |
| `xl` | 20px | Chat bubbles |
| `xxl` | 24px | Bottom sheets, modals |
| `pill` | 999px | Trigger pills, time/location badges |
| `circle` | 50% | Voice orb, avatar |

---

## Shadows

| Level | Offset | Opacity | Radius | Elevation | Usage |
|-------|--------|---------|--------|-----------|-------|
| `sm` | (0, 1) | 0.06 | 4 | 2 | Cards at rest |
| `md` | (0, 4) | 0.10 | 12 | 6 | Elevated cards, sheets |
| `lg` | (0, 8) | 0.14 | 24 | 12 | Modals, bottom sheets |
| `orb` | (0, 0) | 0.30 | 32 | — | Voice orb glow (uses `primary` color) |
| `orbActive` | (0, 0) | 0.50 | 48 | — | Voice orb pulsing glow (uses `accent` color) |

---

## Iconography

Ranti uses **Phosphor Icons** (regular weight, 24px default) for a friendly, rounded icon style that matches the conversational tone.

| Context | Icon | Size |
|---------|------|------|
| Time trigger | `Clock` | 16px (in pills), 20px (in cards) |
| Location trigger | `MapPin` | 16px (in pills), 20px (in cards) |
| Microphone | `Microphone` | 24px |
| Send message | `PaperPlaneRight` | 24px |
| Back / navigate | `CaretLeft` | 24px |
| Settings | `GearSix` | 24px |
| Delete / dismiss | `X` | 20px |
| Reminder active | `Bell` | 20px |
| Reminder fired | `BellRinging` | 20px |
| Reminder done | `CheckCircle` | 20px |
| History | `ClockCounterClockwise` | 20px |
| Keyboard | `Keyboard` | 24px |
| Voice | `Waveform` | 24px |

---

## Theming

The app supports **light** and **dark** modes.

### How it works (Android / Kotlin)

1. `RantiTheme` composable wraps the entire app, providing `MaterialTheme` with Ranti's custom `ColorScheme` and `Typography`.
2. Theme state follows system preference by default, with manual override stored in DataStore.
3. The Rust core does not handle theming — it operates on semantic concepts (`TimeTrigger`, `LocationTrigger`) that the Kotlin UI layer maps to the correct colors.

### In Compose (preferred)

```kotlin
Text(
    text = "Check the pot",
    style = RantiTheme.typography.bodyLg,
    color = RantiTheme.colors.textHi
)
```

### Accessing tokens programmatically

```kotlin
val colors = RantiTheme.colors
// colors.base, colors.surface, colors.elevated
// colors.textHi, colors.textMid, colors.textLo
// colors.primary, colors.accent
// colors.timeTrigger, colors.locationTrigger
```

---

## Components

All components are implemented as Jetpack Compose composables under `ui/components/`.

```kotlin
import com.ranti.ui.components.*
```

---

### Text

Themed text component wrapping Material3 `Text` with Ranti's type scale and color semantics.

```kotlin
RText(variant = TextVariant.H2, text = "Your Reminders")
RText(variant = TextVariant.BodySm, color = TextColor.Muted, text = "Set 3 hours ago")
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `variant` | `TextVariant` | `BodyMd` | Controls font size, line height, weight |
| `color` | `TextColor` | `Primary` | Semantic text color: `Primary`, `Secondary`, `Muted`, `Indigo`, `Accent`, `Time`, `Location`, `Success`, `Error`, `Inverse` |
| `weight` | `FontWeight?` | per variant | Override default weight |
| `modifier` | `Modifier` | — | Standard Compose modifier |

---

### Button

Pressable button with multiple variants and sizes.

```kotlin
RButton(variant = ButtonVariant.Primary, label = "Got it", onClick = {})
RButton(variant = ButtonVariant.Ghost, label = "Cancel", onClick = {})
RButton(variant = ButtonVariant.Outline, label = "View All", onClick = {}, size = ButtonSize.Sm)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `variant` | `ButtonVariant` | `Primary` | `Primary`, `Secondary`, `Ghost`, `Danger`, `Outline` |
| `size` | `ButtonSize` | `Md` | `Sm`, `Md`, `Lg` |
| `label` | `String` | *required* | Button text |
| `loading` | `Boolean` | `false` | Shows spinner, disables press |
| `disabled` | `Boolean` | `false` | Reduces opacity, disables press |
| `fullWidth` | `Boolean` | `false` | Stretches to container width |
| `leadingIcon` | `@Composable (() -> Unit)?` | `null` | Icon before label |
| `onClick` | `() -> Unit` | *required* | Click handler |

**Variant details:**

| Variant | Background | Text Color | Pressed State |
|---------|-----------|------------|---------------|
| `Primary` | `primary` | inverse (cream/dark) | `primaryDeep` |
| `Secondary` | `surface` + border | `textHi` | opacity 0.8 |
| `Ghost` | `primarySoft` / `primarySoftDk` | `primary` | opacity 0.8 |
| `Danger` | `error` | inverse | opacity 0.8 |
| `Outline` | transparent + `primary` border | `primary` | `primarySoft` fill |

**Size details:**

| Size | Padding | Radius | Text Variant |
|------|---------|--------|-------------|
| `Sm` | `px:12 py:8` | `sm` (8px) | `body-sm` |
| `Md` | `px:16 py:12` | `md` (12px) | `body-md` |
| `Lg` | `px:20 py:16` | `lg` (16px) | `body-lg` |

---

### Card

Surface container with configurable elevation and padding.

```kotlin
RCard(elevation = CardElevation.Flat) {
    RText(text = "Reminder content here")
}

RCard(elevation = CardElevation.Floating, padding = CardPadding.Lg) {
    // Modal-like content
}
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `elevation` | `CardElevation` | `Raised` | `Flat`, `Raised`, `Floating` |
| `padding` | `CardPadding` | `Md` | `None`, `Sm`, `Md`, `Lg` |
| `radius` | `CardRadius` | `Lg` | `Sm`, `Md`, `Lg`, `Xl` |

**Elevation details:**

| Level | Border | Shadow | Use for |
|-------|--------|--------|---------|
| `Flat` | Yes (`border` token) | None | Inline sections, list items |
| `Raised` | Yes | `shadow.sm` | Default cards, reminder cards |
| `Floating` | No | `shadow.md` | Modals, bottom sheets |

Background is always `surface` (theme-aware).

---

### Badge

Small labeled indicator with color options.

```kotlin
RBadge(label = "Active", color = BadgeColor.Success)
RBadge(label = "Missed", color = BadgeColor.Error)
RBadge(label = "Pending", color = BadgeColor.Warning)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `label` | `String` | *required* | Badge text |
| `color` | `BadgeColor` | `Neutral` | `Primary`, `Success`, `Warning`, `Error`, `Neutral` |

Styling: `px:8 py:2`, `pill` radius, `label` variant, `semibold` weight.

---

### Avatar

User avatar with image or initials fallback.

```kotlin
RAvatar(name = "Bolu A", size = AvatarSize.Md)
RAvatar(imageUrl = "https://...", name = "Femi", size = AvatarSize.Lg)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `imageUrl` | `String?` | `null` | Image URL. Falls back to initials if absent |
| `name` | `String` | `"U"` | Used for initials (first letters of first two words) |
| `size` | `AvatarSize` | `Md` | `Xs` (24px), `Sm` (32px), `Md` (40px), `Lg` (56px) |

Initials fallback uses a deterministic background rotating through `primarySoft`, `accentSoft`, `timeTriggerSoft`, `locationTriggerSoft`.

---

### Input

Text input with label, hint/error, and icon support.

```kotlin
RInput(
    label = "What should I remind you?",
    placeholder = "e.g. Call mum at 7pm",
    value = text,
    onValueChange = { text = it }
)

RInput(
    error = "I didn't understand that",
    value = text,
    onValueChange = { text = it }
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `label` | `String?` | `null` | Label text above the field |
| `hint` | `String?` | `null` | Helper text below the field |
| `error` | `String?` | `null` | Error text (overrides hint, turns border red) |
| `placeholder` | `String?` | `null` | Placeholder text |
| `leadingIcon` | `@Composable?` | `null` | Icon inside field, left side |
| `trailingIcon` | `@Composable?` | `null` | Icon inside field, right side |
| `singleLine` | `Boolean` | `true` | Single vs multi-line input |

**States:**
- Default: `border` token border
- Focused: `primary` border
- Error: `error` border

Background: `input` token. Radius: `md` (12px).

---

### ChatBubble

Conversational message bubble for the chat-like interface between the user and Ranti.

```kotlin
ChatBubble(
    message = "Remind me to check the pot in 15 minutes",
    sender = BubbleSender.User,
    timestamp = "3:27 PM"
)

ChatBubble(
    message = "Sure, I'll remind you at 3:42 PM.",
    sender = BubbleSender.Ranti,
    timestamp = "3:27 PM"
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `message` | `String` | *required* | Message text |
| `sender` | `BubbleSender` | *required* | `User` or `Ranti` |
| `timestamp` | `String?` | `null` | Time label below bubble |

**Sender details:**

| Sender | Alignment | Background | Text Color | Radius |
|--------|-----------|-----------|------------|--------|
| `User` | End (right) | `primary` | inverse | 20px top-left, 20px top-right, 4px bottom-right, 20px bottom-left |
| `Ranti` | Start (left) | `surface` + `border` | `textHi` | 20px top-left, 20px top-right, 20px bottom-right, 4px bottom-left |

Text variant: `body-md` for user, `body-lg` for Ranti. Max width: 85% of container.

---

### VoiceOrb

The central voice interaction element. A pulsing circular button that represents Ranti's listening state.

```kotlin
VoiceOrb(
    state = OrbState.Idle,
    onTap = { startListening() },
    onLongPress = { /* no-op or haptic */ }
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `state` | `OrbState` | `Idle` | `Idle`, `Listening`, `Processing`, `Speaking` |
| `onTap` | `() -> Unit` | *required* | Tap to start listening |
| `size` | `OrbSize` | `Lg` | `Sm` (48px), `Md` (64px), `Lg` (80px) |

**State details:**

| State | Visual | Color | Animation |
|-------|--------|-------|-----------|
| `Idle` | Solid circle with mic icon | `primary` with `shadow.orb` | Subtle slow breathing (scale 1.0 → 1.03, 3s loop) |
| `Listening` | Pulsing ring around orb | `accent` ring, `primary` fill | Ring pulses outward (scale 1.0 → 1.4, opacity 1 → 0, 1.5s loop) + waveform icon |
| `Processing` | Rotating dots around orb | `accent` dots | 3 dots orbiting (1.2s rotation) |
| `Speaking` | Animated waveform bars | `primary` fill | 4 bars animating height to audio output amplitude |

Orb diameter: 80px (Lg). Ring extends to 120px during `Listening`. Background glow uses `shadow.orbActive`.

---

### ReminderCard

Displays a single reminder with its trigger type, body text, status, and actions.

```kotlin
ReminderCard(
    reminder = Reminder(
        body = "Submit the GEG assignment",
        trigger = TimeTrigger(time = "10:45 PM"),
        status = ReminderStatus.Pending
    ),
    onTap = { navigateToDetail(it) },
    onDismiss = { deleteReminder(it) }
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `reminder` | `Reminder` | *required* | Reminder data object |
| `onTap` | `(Reminder) -> Unit` | *required* | Navigate to detail |
| `onDismiss` | `(Reminder) -> Unit` | `null` | Swipe-to-dismiss callback |

**Layout:**

```
+------------------------------------------+
|  [Clock] 10:45 PM              [Pending] |  <- TimePill + Badge
|                                          |
|  Submit the GEG assignment               |  <- body-md, semibold
|                                          |
|  Set 2 hours ago                         |  <- caption, muted
+------------------------------------------+
```

For location triggers, `Clock` is replaced with `MapPin` and the pill shows the location name.

Card uses `elevation: Raised`, `padding: Md`, `radius: Lg`. Swipe-to-dismiss animates the card off-screen with a red `Trash` icon revealed underneath.

---

### TimePill

Compact display for a time-based trigger.

```kotlin
TimePill(time = "10:45 PM")
TimePill(time = "in 15 minutes", relative = true)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `time` | `String` | *required* | Formatted time string |
| `relative` | `Boolean` | `false` | If true, shows as relative ("in 15 min") |

Styling: `Clock` icon (16px) + `caption` text, `timeTrigger` color, `timeTriggerSoft` / `timeTriggerSoftDk` background, `pill` radius, `px:8 py:4`.

---

### LocationPill

Compact display for a location-based trigger.

```kotlin
LocationPill(name = "Faculty of Technology")
LocationPill(name = "My Hostel", isNickname = true)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `name` | `String` | *required* | Location name or nickname |
| `isNickname` | `Boolean` | `false` | Adds a small star indicator for user-defined names |

Styling: `MapPin` icon (16px) + `caption` text, `locationTrigger` color, `locationTriggerSoft` / `locationTriggerSoftDk` background, `pill` radius, `px:8 py:4`.

---

### DisambiguationSheet

Bottom sheet that presents multiple location options when Ranti finds ambiguous matches.

```kotlin
DisambiguationSheet(
    query = "the plaza",
    options = listOf(
        LocationOption("University Shopping Plaza", "OAU Campus", 0.3),
        LocationOption("Bola Plaza", "Ile-Ife Road", 2.1),
        LocationOption("OAU Main Gate Plaza", "Main Gate", 1.5)
    ),
    onSelect = { selected -> resolveLocation(selected) },
    onDismiss = { cancelReminder() }
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `query` | `String` | *required* | What the user originally said |
| `options` | `List<LocationOption>` | *required* | Max 3 location matches |
| `onSelect` | `(LocationOption) -> Unit` | *required* | User picks one |
| `onDismiss` | `() -> Unit` | *required* | User cancels |

**Layout:**

```
+------------------------------------------+
|  I found a few places matching           |
|  "the plaza"                             |
|                                          |
|  +--------------------------------------+|
|  | [MapPin] University Shopping Plaza   ||  <- tappable row
|  |          OAU Campus · 0.3 km         ||
|  +--------------------------------------+|
|  +--------------------------------------+|
|  | [MapPin] Bola Plaza                  ||
|  |          Ile-Ife Road · 2.1 km       ||
|  +--------------------------------------+|
|  +--------------------------------------+|
|  | [MapPin] OAU Main Gate Plaza         ||
|  |          Main Gate · 1.5 km          ||
|  +--------------------------------------+|
|                                          |
|  [Cancel]                                |
+------------------------------------------+
```

Sheet uses `elevation: Floating`, `radius: Xxl` (top corners only). Each option row is a `Card` with `elevation: Flat`.

---

### CountdownRing

Circular countdown indicator used on reminder detail and pending notifications.

```kotlin
CountdownRing(
    remainingSeconds = 542,
    totalSeconds = 900,
    label = "8 min left"
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `remainingSeconds` | `Int` | *required* | Seconds remaining |
| `totalSeconds` | `Int` | *required* | Original total duration |
| `label` | `String?` | auto-formatted | Center label text |
| `size` | `RingSize` | `Md` | `Sm` (40px), `Md` (64px), `Lg` (96px) |
| `onExpire` | `() -> Unit?` | `null` | Callback when countdown reaches zero |

**Visual:**
- Track: `border` token, 4px stroke
- Fill: `primary` when > 25% remaining, `warning` when 10–25%, `error` when < 10%
- Center text: `body-sm` or `h3` depending on size
- Animation: smooth arc reduction, 1-second tick

---

### Toggle

Simple on/off toggle switch for settings.

```kotlin
RToggle(checked = isEnabled, onCheckedChange = { isEnabled = it })
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `checked` | `Boolean` | *required* | Current state |
| `onCheckedChange` | `(Boolean) -> Unit` | *required* | State change callback |
| `disabled` | `Boolean` | `false` | Disables interaction |

Track: 48x28px. Thumb: 24px circle. Active color: `primary`. Inactive: `border`. Animated thumb translation with spring physics.

---

### Divider

Horizontal rule with optional centered label.

```kotlin
RDivider()
RDivider(label = "Today")
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `label` | `String?` | `null` | Centered text within the divider |

Without label: 1px line in `border` token color.
With label: two lines with `caption` muted text centered between them, `gap: md`.

---

### Toast

Transient feedback message shown at the top of the screen.

```kotlin
showToast(
    message = "Reminder set for 10:45 PM",
    type = ToastType.Success
)
```

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `message` | `String` | *required* | Toast text |
| `type` | `ToastType` | `Info` | `Success`, `Warning`, `Error`, `Info` |
| `duration` | `Long` | 3000ms | Auto-dismiss duration |

**Type details:**

| Type | Leading Icon | Background |
|------|-------------|-----------|
| `Success` | `CheckCircle` | `successBg` |
| `Warning` | `Warning` | `warningBg` |
| `Error` | `XCircle` | `errorBg` |
| `Info` | `Info` | `surface` + border |

Animation: slide down from top + fade in (250ms), slide up + fade out on dismiss.
Position: below status bar, horizontally centered, max-width 90%.

---

## Animation

Duration tokens for consistent motion across the app. Defined in `core/design/tokens.kt`.

| Token | Duration | Usage |
|-------|----------|-------|
| `instant` | 80ms | Tap feedback, toggle snaps |
| `fast` | 150ms | Badge appear, pill transitions |
| `normal` | 250ms | Screen transitions, toast enter/exit |
| `slow` | 400ms | Orb state changes, card expand |
| `breath` | 3000ms | Orb idle breathing loop |
| `pulse` | 1500ms | Orb listening pulse loop |

**Easing:**
- UI transitions: `FastOutSlowIn` (Material standard)
- Orb animations: `LinearOutSlowIn` for organic feel
- Spring physics: damping 0.7, stiffness 300 (for toggle, swipe-to-dismiss)

---

## Z-Index

Layering scale for overlapping elements.

| Token | Value | Usage |
|-------|-------|-------|
| `base` | 0 | Default content |
| `fab` | 5 | Voice orb floating button |
| `raised` | 10 | Sticky headers |
| `overlay` | 20 | Disambiguation sheet, side panels |
| `modal` | 30 | Bottom sheets, dialogs |
| `toast` | 40 | Toast notifications |

---

## Usage Guidelines

### Color selection rules

1. **Primary actions and voice UI** use indigo (`primary` button, orb idle)
2. **Active/listening states** use violet (`accent` ring, waveform)
3. **Time-based triggers** always use amber/orange (`timeTrigger`)
4. **Location-based triggers** always use teal/green (`locationTrigger`)
5. **System feedback** uses status colors (success/warning/error)
6. **Never mix** `timeTrigger` and `locationTrigger` on the same element — a reminder has one trigger type

### Dark mode checklist

Every screen must look correct in both themes. Quick checks:
- Backgrounds: use themed `base`, `surface`, `elevated` tokens (never hardcode)
- Text: use `textHi`, `textMid`, `textLo` tokens (never hardcode)
- Pill backgrounds: use the `*Soft` / `*SoftDk` variants
- Orb glow: verify shadow renders correctly on dark backgrounds

### Component composition pattern

Compose complex UI from primitives. Example — a reminder in the list:

```kotlin
RCard(elevation = CardElevation.Raised, padding = CardPadding.Md) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimePill(time = "10:45 PM")
        RBadge(label = "Pending", color = BadgeColor.Warning)
    }
    Spacer(modifier = Modifier.height(spacing.sm))
    RText(
        variant = TextVariant.BodyMd,
        weight = FontWeight.SemiBold,
        text = "Submit the GEG assignment"
    )
    Spacer(modifier = Modifier.height(spacing.xs))
    RText(
        variant = TextVariant.Caption,
        color = TextColor.Muted,
        text = "Set 2 hours ago"
    )
}
```

### File structure

```
core/                          ← Rust shared core (compiled via NDK)
  design/
    tokens.rs                  ← Raw design token constants (semantic names, not colors)

app/                           ← Kotlin Android app
  ui/
    theme/
      Color.kt                ← Color definitions (light + dark)
      Type.kt                 ← Typography definitions
      Theme.kt                ← RantiTheme composable + tokens
      Spacing.kt              ← Spacing scale
      Shape.kt                ← Border radius scale
    components/
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
      DisambiguationSheet.kt
      CountdownRing.kt
      Toggle.kt
      Divider.kt
      Toast.kt
```
