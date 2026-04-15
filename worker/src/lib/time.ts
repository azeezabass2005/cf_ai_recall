// SPEC §7.1 / §7.1.1 — time + recurrence parser.
//
// The LLM has access to this via the `resolve_time` tool, but we also expose
// the raw functions so the agent can call them deterministically when it
// already knows a `time_expr` (cheaper than a second round-trip through the
// model). Everything is pure TS — no LLM calls, no I/O.
//
// Timezone handling: the Android client sends its IANA zone (e.g. "Africa/Lagos")
// in the `tz` field of chat requests. All parsing is done *in that zone*, then
// the resolved instant is converted to a UTC ISO 8601 string for storage and
// AlarmManager. This is the whole ballgame for correctness — if we parsed
// "tomorrow at 8am" against the worker's own clock the user would get reminders
// at the wrong wall-clock time.

import type { RecurrenceRule, Weekday } from "./types";

const WEEKDAYS: Weekday[] = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
const WEEKDAY_NAMES: Record<string, Weekday> = {
  sunday: "sun", sun: "sun",
  monday: "mon", mon: "mon",
  tuesday: "tue", tue: "tue", tues: "tue",
  wednesday: "wed", wed: "wed", weds: "wed",
  thursday: "thu", thu: "thu", thurs: "thu",
  friday: "fri", fri: "fri",
  saturday: "sat", sat: "sat",
};

export interface ParsedTime {
  /** Resolved instant as UTC ISO 8601. */
  fire_at: string;
  /** The original expression the user typed, for logging / response text. */
  original_expr: string;
  /** Optional human-readable note, e.g. "I'll remind you 15 min before 11pm so you have time". */
  note?: string;
}

export interface ParseContext {
  /** "Now" as a UTC ISO timestamp — the worker's clock, NOT the client's. */
  now: string;
  /** IANA zone from the client, e.g. "Africa/Lagos". Defaults to UTC. */
  tz: string;
}

// ─── Timezone-aware helpers ──────────────────────────────────────────────────
//
// Workers don't ship a full Temporal API yet, so we use Intl.DateTimeFormat to
// read the wall-clock parts of a Date *as seen in a specific zone*, and a tiny
// utility to compose a Date from those parts. Good enough for parsing — we
// never need to format output in a non-UTC zone, only to reason about "is it
// past 7pm local time yet".

interface ZonedParts {
  year: number;
  month: number; // 1-12
  day: number;   // 1-31
  hour: number;  // 0-23
  minute: number;
  second: number;
  /** 0-6 (Sun-Sat) */
  weekday: number;
}

function zonedPartsOf(instant: Date, tz: string): ZonedParts {
  const fmt = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    weekday: "short",
  });
  const parts: Record<string, string> = {};
  for (const p of fmt.formatToParts(instant)) {
    if (p.type !== "literal") parts[p.type] = p.value;
  }
  const weekdayMap: Record<string, number> = {
    Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6,
  };
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second),
    weekday: (parts.weekday ? weekdayMap[parts.weekday] : undefined) ?? 0,
  };
}

/**
 * Given wall-clock parts in a zone, return the corresponding UTC instant.
 * We solve it by taking the UTC representation of the same numeric parts,
 * then correcting for the zone's offset *at that instant*. One extra round
 * handles the DST boundary edge case where the first guess sits on the
 * wrong side of the transition.
 */
function instantFromZonedParts(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  tz: string,
  second = 0,
): Date {
  const naiveUtc = Date.UTC(year, month - 1, day, hour, minute, second);
  // First guess: what does the zone think those UTC millis look like?
  const observed = zonedPartsOf(new Date(naiveUtc), tz);
  const observedUtc = Date.UTC(
    observed.year, observed.month - 1, observed.day,
    observed.hour, observed.minute, observed.second,
  );
  const offset = naiveUtc - observedUtc;
  const firstGuess = new Date(naiveUtc + offset);
  // Verify — if the wall-clock now matches, we're done. If not (DST), adjust
  // one more time with the newly-observed offset.
  const verify = zonedPartsOf(firstGuess, tz);
  if (
    verify.year === year && verify.month === month && verify.day === day &&
    verify.hour === hour && verify.minute === minute
  ) {
    return firstGuess;
  }
  const verifyUtc = Date.UTC(
    verify.year, verify.month - 1, verify.day,
    verify.hour, verify.minute, verify.second,
  );
  const correction = naiveUtc - verifyUtc;
  return new Date(naiveUtc + correction);
}

// ─── Token helpers ──────────────────────────────────────────────────────────

function normalise(expr: string): string {
  return expr
    .toLowerCase()
    .trim()
    .replace(/[,\.](?=\s|$)/g, " ")
    .replace(/\beveryday\b/g, "every day")
    .replace(/\s+/g, " ");
}

interface ParsedClockTime {
  hour: number;
  minute: number;
  /** True when the user explicitly said "am"/"pm" — caller can trust the hour as-is. */
  meridiemSpecified: boolean;
}

/** "7", "7pm", "7:30", "19:00", "7 pm", "7:30 am" → {hour, minute, meridiemSpecified} or null. */
function parseClockTime(raw: string): ParsedClockTime | null {
  const m = raw.match(/^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$/i);
  if (!m) return null;
  let hour = Number(m[1]);
  const minute = m[2] ? Number(m[2]) : 0;
  const mer = m[3]?.toLowerCase();
  if (hour > 23 || minute > 59) return null;
  if (mer === "pm" && hour < 12) hour += 12;
  if (mer === "am" && hour === 12) hour = 0;
  // NOTE: we *used to* unconditionally map bare hours 1-7 to PM here, which
  // broke "at 5:03" when the user meant 5:03 AM (they were awake at 4 AM and
  // wanted a 1-hour nudge). The new policy: leave bare hours alone and let
  // the caller choose the nearest future occurrence (see the bare-clock-time
  // branch in parseTimeExpression).
  return { hour, minute, meridiemSpecified: Boolean(mer) };
}

// Matches clock-time-ish tokens including optional "at" / "by".
const CLOCK_RE = /(?:at|by|before)?\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)/i;

// ─── Named periods ──────────────────────────────────────────────────────────

const NAMED_PERIODS: Record<string, { hour: number; minute: number; tag: "today" | "tomorrow" }> = {
  "this morning":   { hour: 8,  minute: 0, tag: "today" },
  "morning":        { hour: 8,  minute: 0, tag: "today" },
  "this afternoon": { hour: 14, minute: 0, tag: "today" },
  "afternoon":      { hour: 14, minute: 0, tag: "today" },
  "this evening":   { hour: 18, minute: 0, tag: "today" },
  "evening":        { hour: 18, minute: 0, tag: "today" },
  "tonight":        { hour: 21, minute: 0, tag: "today" },
  "tomorrow":                     { hour: 8,  minute: 0, tag: "tomorrow" },
  "tomorrow morning":             { hour: 8,  minute: 0, tag: "tomorrow" },
  "tomorrow afternoon":           { hour: 14, minute: 0, tag: "tomorrow" },
  "tomorrow evening":             { hour: 18, minute: 0, tag: "tomorrow" },
  "tomorrow night":               { hour: 21, minute: 0, tag: "tomorrow" },
};

// ─── The parser ─────────────────────────────────────────────────────────────

export function parseTimeExpression(
  expr: string,
  ctx: ParseContext,
): ParsedTime | null {
  const originalExpr = expr.trim();
  const text = normalise(expr);
  const nowDate = new Date(ctx.now);
  const nowParts = zonedPartsOf(nowDate, ctx.tz);

  const toFireAt = (
    y: number, mo: number, d: number, h: number, mi: number,
    note?: string,
  ): ParsedTime => ({
    fire_at: instantFromZonedParts(y, mo, d, h, mi, ctx.tz).toISOString(),
    original_expr: originalExpr,
    ...(note ? { note } : {}),
  });

  // 1) Pure relative — "in N minutes/hours", possibly combined.
  // "in 1 hour 30 minutes", "in 2 hrs", "in 15 min", "in half an hour".
  if (/^in\b/.test(text) || /^after\b/.test(text)) {
    let totalMs = 0;
    let matched = false;
    for (const m of text.matchAll(/(\d+(?:\.\d+)?)\s*(minute|minutes|min|mins|m|hour|hours|hr|hrs|h|second|seconds|sec|secs|s|day|days|d|week|weeks|wk|wks)\b/g)) {
      const n = Number(m[1]);
      const unit = m[2] ?? "";
      if (unit.startsWith("w")) totalMs += n * 7 * 24 * 3600_000;
      else if (unit.startsWith("d")) totalMs += n * 24 * 3600_000;
      else if (unit.startsWith("h")) totalMs += n * 3600_000;
      else if (unit.startsWith("s")) totalMs += n * 1000;
      else totalMs += n * 60_000;
      matched = true;
    }
    if (/half an? hour/.test(text))          { totalMs += 30 * 60_000; matched = true; }
    if (/quarter of an? hour|quarter hour/.test(text)) { totalMs += 15 * 60_000; matched = true; }
    if (/an? hour\b/.test(text) && !/\d+\s*hour/.test(text)) { totalMs += 3600_000; matched = true; }
    if (/a (?:few|couple of?) minutes?/.test(text)) { totalMs += 3 * 60_000; matched = true; }
    if (matched && totalMs > 0) {
      const fireAt = new Date(nowDate.getTime() + totalMs).toISOString();
      return { fire_at: fireAt, original_expr: originalExpr };
    }
  }

  // 2) Named period — "tonight", "this evening", "tomorrow morning".
  //    Longest-match first so "tomorrow evening" wins over "tomorrow".
  const sortedNames = Object.keys(NAMED_PERIODS).sort((a, b) => b.length - a.length);
  for (const name of sortedNames) {
    if (text.includes(name)) {
      const entry = NAMED_PERIODS[name];
      if (!entry) continue;
      const { hour, minute, tag } = entry;
      // If there's also an explicit clock time ("tonight at 10pm"), let the
      // clock-time path below override us.
      const clockMatch = text.replace(name, "").match(CLOCK_RE);
      if (clockMatch && clockMatch[1]) {
        const ct = parseClockTime(clockMatch[1].trim());
        if (ct) {
          const dayOffset = tag === "tomorrow" ? 1 : 0;
          const fire = new Date(nowDate.getTime() + dayOffset * 24 * 3600_000);
          const p = zonedPartsOf(fire, ctx.tz);
          return toFireAt(p.year, p.month, p.day, ct.hour, ct.minute);
        }
      }
      const dayOffset = tag === "tomorrow" ? 1 : 0;
      const fire = new Date(nowDate.getTime() + dayOffset * 24 * 3600_000);
      const p = zonedPartsOf(fire, ctx.tz);
      return toFireAt(p.year, p.month, p.day, hour, minute);
    }
  }

  // 3) "on <weekday> at <time>" or "<weekday> at <time>".
  for (const [name, wd] of Object.entries(WEEKDAY_NAMES)) {
    // Whole-word match so "mon" doesn't match "month" / "monday".
    const re = new RegExp(`\\b${name}\\b`);
    if (re.test(text)) {
      // Next occurrence of that weekday (including today if the clock time is later).
      const targetIdx = WEEKDAYS.indexOf(wd);
      let daysUntil = (targetIdx - nowParts.weekday + 7) % 7;
      const clockMatch = text.match(CLOCK_RE);
      let ct: ParsedClockTime | null = clockMatch && clockMatch[1] ? parseClockTime(clockMatch[1].trim()) : null;
      if (!ct) ct = { hour: 9, minute: 0, meridiemSpecified: true }; // sensible default
      if (daysUntil === 0) {
        const pastNow = ct.hour < nowParts.hour ||
          (ct.hour === nowParts.hour && ct.minute <= nowParts.minute);
        if (pastNow) daysUntil = 7;
      }
      const fire = new Date(nowDate.getTime() + daysUntil * 24 * 3600_000);
      const p = zonedPartsOf(fire, ctx.tz);
      return toFireAt(p.year, p.month, p.day, ct.hour, ct.minute);
    }
  }

  // 4) Absolute date — "on April 30th at 2:30pm" / "April 30 at 14:30".
  const monthRe = /\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\b/;
  const monthMatch = text.match(monthRe);
  if (monthMatch) {
    const months: Record<string, number> = {
      january: 1, jan: 1, february: 2, feb: 2, march: 3, mar: 3, april: 4, apr: 4,
      may: 5, june: 6, jun: 6, july: 7, jul: 7, august: 8, aug: 8,
      september: 9, sep: 9, sept: 9, october: 10, oct: 10, november: 11, nov: 11,
      december: 12, dec: 12,
    };
    const monthKey = monthMatch[1] ?? "";
    const month = months[monthKey] ?? 1;
    const dayMatch = text.match(/\b(\d{1,2})(?:st|nd|rd|th)?\b/);
    const day = dayMatch ? Number(dayMatch[1]) : 1;
    const clockMatch = text.match(CLOCK_RE);
    const ct = clockMatch && clockMatch[1] ? parseClockTime(clockMatch[1].trim()) : null;
    const hour = ct?.hour ?? 9;
    const minute = ct?.minute ?? 0;
    // Pick this year unless that date is already behind us, in which case next year.
    let year = nowParts.year;
    const guess = instantFromZonedParts(year, month, day, hour, minute, ctx.tz);
    if (guess.getTime() <= nowDate.getTime()) year += 1;
    return toFireAt(year, month, day, hour, minute);
  }

  // 5) "before X" — fire 15 minutes early. Matches "before 11pm".
  const beforeMatch = text.match(/^before\s+(.+)$/);
  if (beforeMatch && beforeMatch[1]) {
    const inner = parseTimeExpression(beforeMatch[1], ctx);
    if (inner) {
      const early = new Date(new Date(inner.fire_at).getTime() - 15 * 60_000);
      return {
        fire_at: early.toISOString(),
        original_expr: originalExpr,
        note: "I'll remind you 15 minutes early so you have time.",
      };
    }
  }

  // 6) Bare clock time — "at 7pm", "7pm", "19:00".
  const clockMatch = text.match(CLOCK_RE);
  if (clockMatch && clockMatch[1]) {
    const ct = parseClockTime(clockMatch[1].trim());
    if (ct) {
      const nowMs = nowDate.getTime();
      // Build the "as given" candidate for today in the user's zone.
      const asGivenToday = instantFromZonedParts(
        nowParts.year, nowParts.month, nowParts.day, ct.hour, ct.minute, ctx.tz,
      );

      // If the user explicitly said am/pm (or used 24h like 19:00 / 13:00),
      // trust it verbatim — today if still ahead, otherwise tomorrow.
      if (ct.meridiemSpecified || ct.hour >= 13) {
        let y = nowParts.year, mo = nowParts.month, d = nowParts.day;
        if (asGivenToday.getTime() <= nowMs) {
          const tomorrow = new Date(nowMs + 24 * 3600_000);
          const p = zonedPartsOf(tomorrow, ctx.tz);
          y = p.year; mo = p.month; d = p.day;
        }
        return toFireAt(y, mo, d, ct.hour, ct.minute);
      }

      // Ambiguous bare hour (1-12 with no am/pm). Pick the nearest FUTURE
      // occurrence by trying both the as-given hour and the +12h twin,
      // rolling to tomorrow if both have already passed today.
      const altHour = (ct.hour + 12) % 24;
      const altToday = instantFromZonedParts(
        nowParts.year, nowParts.month, nowParts.day, altHour, ct.minute, ctx.tz,
      );
      const tomorrow = new Date(nowMs + 24 * 3600_000);
      const pt = zonedPartsOf(tomorrow, ctx.tz);
      const asGivenTomorrow = instantFromZonedParts(
        pt.year, pt.month, pt.day, ct.hour, ct.minute, ctx.tz,
      );
      const altTomorrow = instantFromZonedParts(
        pt.year, pt.month, pt.day, altHour, ct.minute, ctx.tz,
      );
      // Require at least a 60-second gap so "remind me at 5" right at 5:00:30
      // doesn't immediately fire.
      const minFuture = nowMs + 60_000;
      const candidates = [
        { t: asGivenToday, hour: ct.hour, parts: nowParts },
        { t: altToday,     hour: altHour, parts: nowParts },
        { t: asGivenTomorrow, hour: ct.hour, parts: pt },
        { t: altTomorrow,     hour: altHour, parts: pt },
      ]
        .filter((c) => c.t.getTime() >= minFuture)
        .sort((a, b) => a.t.getTime() - b.t.getTime());
      const pick = candidates[0];
      if (pick) {
        return toFireAt(pick.parts.year, pick.parts.month, pick.parts.day, pick.hour, ct.minute);
      }
      // Fallback — shouldn't normally happen, but if it does, just use as-given tomorrow.
      return toFireAt(pt.year, pt.month, pt.day, ct.hour, ct.minute);
    }
  }

  return null;
}

// ─── Recurrence ─────────────────────────────────────────────────────────────

export interface ParsedRecurrence {
  rule: RecurrenceRule;
  /** The first `next_fire_at` for the rule, as UTC ISO. */
  next_fire_at: string;
}

/**
 * Tries to parse a recurring expression. Runs *before* parseTimeExpression —
 * if it succeeds, the caller should use the result instead of the one-shot
 * path. Returns null if the expression isn't recurring.
 */
export function parseRecurrence(
  expr: string,
  ctx: ParseContext,
): ParsedRecurrence | null {
  const originalExpr = expr.trim();
  const text = normalise(expr);
  if (!/\bevery\b/.test(text)) return null;

  const nowDate = new Date(ctx.now);

  // Time of day — reuse the clock parser.
  const clockMatch = text.match(CLOCK_RE);
  const ct = clockMatch && clockMatch[1] ? parseClockTime(clockMatch[1].trim()) : null;
  const hour = ct?.hour ?? 9;
  const minute = ct?.minute ?? 0;
  const timeOfDay = `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;

  // Interval — "every two weeks", "every 3 days".
  let interval = 1;
  const wordInterval: Record<string, number> = { two: 2, three: 3, four: 4, five: 5, six: 6 };
  const intervalMatch = text.match(/every\s+(\d+|two|three|four|five|six)\s+(day|days|week|weeks|month|months)/);
  if (intervalMatch && intervalMatch[1]) {
    const raw = intervalMatch[1];
    interval = Number.isNaN(Number(raw)) ? (wordInterval[raw] ?? 1) : Number(raw);
  }

  const makeRule = (
    frequency: RecurrenceRule["frequency"],
    extras: Partial<RecurrenceRule>,
  ): RecurrenceRule => ({
    frequency,
    interval,
    time_of_day: timeOfDay,
    starts_on: ctx.now,
    ends_on: null,
    original_expr: originalExpr,
    ...extras,
  });

  // Weekly — weekday-based.
  const weekdayTokens: Weekday[] = [];
  if (/every weekday\b/.test(text)) {
    weekdayTokens.push("mon", "tue", "wed", "thu", "fri");
  } else if (/every weekend\b/.test(text)) {
    weekdayTokens.push("sat", "sun");
  } else {
    for (const [name, wd] of Object.entries(WEEKDAY_NAMES)) {
      if (new RegExp(`\\b${name}\\b`).test(text) && !weekdayTokens.includes(wd)) {
        weekdayTokens.push(wd);
      }
    }
  }

  if (weekdayTokens.length > 0) {
    const rule = makeRule("weekly", { by_weekday: weekdayTokens });
    const next = nextOccurrence(rule, nowDate, ctx.tz);
    return { rule, next_fire_at: next.toISOString() };
  }

  // Monthly by day-of-month.
  const monthDayMatch = text.match(/every month on the (\d{1,2})(?:st|nd|rd|th)?/);
  if (monthDayMatch || /\bevery month\b/.test(text)) {
    const day = monthDayMatch ? Number(monthDayMatch[1]) : 1;
    const rule = makeRule("monthly", { by_month_day: [day] });
    const next = nextOccurrence(rule, nowDate, ctx.tz);
    return { rule, next_fire_at: next.toISOString() };
  }

  // Daily — default bucket. Catches "every day at 8am", "every morning", …
  if (/every (day|morning|afternoon|evening|night)/.test(text)) {
    let defaultHour = hour, defaultMinute = minute;
    if (!ct) {
      if (/morning/.test(text))   { defaultHour = 8;  defaultMinute = 0; }
      if (/afternoon/.test(text)) { defaultHour = 14; defaultMinute = 0; }
      if (/evening/.test(text))   { defaultHour = 18; defaultMinute = 0; }
      if (/night/.test(text))     { defaultHour = 21; defaultMinute = 0; }
    }
    const rule = makeRule("daily", {
      time_of_day: `${String(defaultHour).padStart(2, "0")}:${String(defaultMinute).padStart(2, "0")}`,
    });
    const next = nextOccurrence(rule, nowDate, ctx.tz);
    return { rule, next_fire_at: next.toISOString() };
  }

  return null;
}

/**
 * Compute the next firing instant for a recurrence rule, strictly after
 * `after`. Exported so the Android-side scheduler can ask the worker to
 * compute the next one after a firing event too.
 */
export function nextOccurrence(
  rule: RecurrenceRule,
  after: Date,
  tz: string,
): Date {
  const [hStr, mStr] = rule.time_of_day.split(":");
  const hour = Number(hStr);
  const minute = Number(mStr);
  const afterParts = zonedPartsOf(after, tz);

  const todayAtRuleTime = instantFromZonedParts(
    afterParts.year, afterParts.month, afterParts.day, hour, minute, tz,
  );

  if (rule.frequency === "daily") {
    if (todayAtRuleTime.getTime() > after.getTime()) return todayAtRuleTime;
    const next = new Date(after.getTime() + rule.interval * 24 * 3600_000);
    const np = zonedPartsOf(next, tz);
    return instantFromZonedParts(np.year, np.month, np.day, hour, minute, tz);
  }

  if (rule.frequency === "weekly") {
    const wanted = new Set((rule.by_weekday ?? []).map((w) => WEEKDAYS.indexOf(w)));
    if (wanted.size === 0) {
      // Fallback: same weekday as starts_on.
      const startParts = zonedPartsOf(new Date(rule.starts_on), tz);
      wanted.add(startParts.weekday);
    }
    for (let offset = 0; offset < 7 * rule.interval + 7; offset++) {
      const candidate = new Date(after.getTime() + offset * 24 * 3600_000);
      const p = zonedPartsOf(candidate, tz);
      if (!wanted.has(p.weekday)) continue;
      const inst = instantFromZonedParts(p.year, p.month, p.day, hour, minute, tz);
      if (inst.getTime() > after.getTime()) return inst;
    }
  }

  if (rule.frequency === "monthly") {
    const days = rule.by_month_day ?? [1];
    // Try this month, then roll forward by `interval` months repeatedly.
    for (let monthOffset = 0; monthOffset < 24; monthOffset += rule.interval) {
      const baseMonth = afterParts.month + monthOffset;
      const year = afterParts.year + Math.floor((baseMonth - 1) / 12);
      const month = ((baseMonth - 1) % 12) + 1;
      for (const d of days.slice().sort((a, b) => a - b)) {
        const inst = instantFromZonedParts(year, month, d, hour, minute, tz);
        if (inst.getTime() > after.getTime()) return inst;
      }
    }
  }

  // Shouldn't happen — return a sentinel far future.
  return new Date(after.getTime() + 365 * 24 * 3600_000);
}
