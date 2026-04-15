-- Recall — initial D1 schema. Mirrors SPEC §15.5 with the addition of a `devices` table
-- (the v1.0 spec stored data per-device on the phone; we now store per-device in D1).

CREATE TABLE IF NOT EXISTS devices (
    id              TEXT PRIMARY KEY,         -- per-install UUID from X-Recall-Device
    first_seen_at   TEXT NOT NULL,
    last_seen_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reminders (
    id              TEXT PRIMARY KEY,
    device_id       TEXT NOT NULL REFERENCES devices(id),
    body            TEXT NOT NULL,
    trigger_type    TEXT NOT NULL CHECK (trigger_type IN ('time', 'location')),
    trigger_data    TEXT NOT NULL,            -- JSON blob
    recurrence_data TEXT,                     -- JSON blob (NULL = one-shot)
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
