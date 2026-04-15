// Shared TypeScript types for the Recall backend.
// Kept language-agnostic on purpose so a future Rust port maps 1:1 (see SPEC §15).

export type ReminderId = string;
export type NicknameId = string;
export type DeviceId = string;
export type IsoTimestamp = string; // ISO 8601, always UTC

export type Frequency = "daily" | "weekly" | "monthly";
export type Weekday = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";

export type ReminderStatus =
  | "pending"
  | "fired"
  | "dismissed"
  | "snoozed"
  | "paused"
  | "expired";

export type ReminderSource = "voice" | "chat_text" | "manual_form";

export type Trigger =
  | {
      type: "time";
      fire_at: IsoTimestamp;
      original_expr: string;
    }
  | {
      type: "location";
      place_name: string;
      place_id: string | null;
      lat: number;
      lng: number;
      radius_m: number;
      original_expr: string;
    };

export interface RecurrenceRule {
  frequency: Frequency;
  interval: number;
  by_weekday?: Weekday[];
  by_month_day?: number[];
  time_of_day: string; // "HH:MM" 24h, local
  starts_on: IsoTimestamp;
  ends_on?: IsoTimestamp | null;
  original_expr: string;
}

export interface Reminder {
  id: ReminderId;
  device_id: DeviceId;
  body: string;
  trigger: Trigger;
  recurrence: RecurrenceRule | null;
  status: ReminderStatus;
  source: ReminderSource;
  created_at: IsoTimestamp;
  fired_at: IsoTimestamp | null;
  fire_count: number;
  next_fire_at: IsoTimestamp | null;
  snoozed_until: IsoTimestamp | null;
}

export interface Nickname {
  id: NicknameId;
  device_id: DeviceId;
  nickname: string;
  place_name: string;
  place_id: string | null;
  lat: number;
  lng: number;
  created_at: IsoTimestamp;
  updated_at: IsoTimestamp;
}

export interface ChatMessage {
  id: string;
  device_id: DeviceId;
  sender: "user" | "recall";
  text: string;
  timestamp: IsoTimestamp;
  related_reminder_id: ReminderId | null;
  input_mode: "text" | "voice";
}
