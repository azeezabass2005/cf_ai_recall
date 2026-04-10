package com.ranti.navigation

/**
 * Route constants for Jetpack Navigation Compose. Mirrors SPEC §2.
 *
 * Only `Chat` is wired up in the scaffold — the rest land in their respective
 * milestones (onboarding §4, reminders §11, nicknames §10, settings §13).
 */
object Routes {
    const val OnboardingWelcome = "onboarding/welcome"
    const val OnboardingPermissions = "onboarding/permissions"
    const val OnboardingWakeWord = "onboarding/wake-word-setup"
    const val OnboardingReady = "onboarding/ready"

    const val Chat = "chat/index"

    const val RemindersIndex = "reminders/index"
    const val RemindersDetail = "reminders/detail/{id}"
    const val RemindersNew = "reminders/new"
    const val RemindersEdit = "reminders/edit/{id}"

    const val NicknamesIndex = "nicknames/index"
    const val NicknamesEdit = "nicknames/edit/{id}"

    const val SettingsIndex = "settings/index"
    const val SettingsWakeWord = "settings/wake-word"
    const val SettingsNotifications = "settings/notifications"
    const val SettingsLocation = "settings/location"
    const val SettingsVoice = "settings/voice"
    const val SettingsAbout = "settings/about"
}
