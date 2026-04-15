package com.recall.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.recall.ui.screens.chat.ChatScreen
import com.recall.ui.screens.onboarding.PermissionsScreen
import com.recall.ui.screens.onboarding.ReadyScreen
import com.recall.ui.screens.onboarding.WakeWordSetupScreen
import com.recall.ui.screens.onboarding.WelcomeScreen
import com.recall.ui.screens.reminders.ReminderDetailScreen
import com.recall.ui.screens.reminders.ReminderFormScreen
import com.recall.ui.screens.reminders.RemindersScreen
import com.recall.ui.screens.reminders.RemindersViewModel
import com.recall.ui.screens.settings.AboutScreen
import com.recall.ui.screens.settings.LocationSettingsScreen
import com.recall.ui.screens.nicknames.NicknameEditScreen
import com.recall.ui.screens.nicknames.NicknamesScreen
import com.recall.ui.screens.settings.NotificationSettingsScreen
import com.recall.ui.screens.settings.SettingsScreen
import com.recall.ui.screens.settings.VoiceSettingsScreen
import com.recall.ui.screens.settings.WakeWordSettingsScreen


/**
 * Top-level navigation graph. Onboarding (§4) is wired up; reminders, nicknames,
 * and settings come online in their respective milestones. The constants in
 * [Routes] mirror the full SPEC §2 graph so future additions are pure additions.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.OnboardingWelcome) {
            WelcomeScreen(onContinue = { navController.navigate(Routes.OnboardingPermissions) })
        }
        composable(Routes.OnboardingPermissions) {
            PermissionsScreen(onContinue = { navController.navigate(Routes.OnboardingWakeWord) })
        }
        composable(Routes.OnboardingWakeWord) {
            WakeWordSetupScreen(onContinue = { navController.navigate(Routes.OnboardingReady) })
        }
        composable(Routes.OnboardingReady) {
            ReadyScreen(
                onFinish = {
                    navController.navigate(Routes.Chat) {
                        // Drop the entire onboarding stack — back from chat exits the app.
                        popUpTo(Routes.OnboardingWelcome) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Chat) {
            ChatScreen(
                onOpenReminders = { navController.navigate("reminders_graph") },
                onCreateReminder = { navController.navigate(Routes.RemindersNew) },
                onOpenSettings = { navController.navigate(Routes.SettingsIndex) },
            )
        }

        // Nested nav graph for reminders — scopes the ViewModel so list + detail share state.
        navigation(startDestination = Routes.RemindersIndex, route = "reminders_graph") {
            composable(Routes.RemindersIndex) {
                val parentEntry = remember(it) { navController.getBackStackEntry("reminders_graph") }
                val vm: RemindersViewModel = viewModel(parentEntry)
                RemindersScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreateReminder = { navController.navigate(Routes.RemindersNew) },
                    onReminderClick = { id ->
                        navController.navigate(Routes.RemindersDetail.replace("{id}", id))
                    },
                    vm = vm,
                )
            }
            composable(
                route = Routes.RemindersDetail,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("reminders_graph") }
                val vm: RemindersViewModel = viewModel(parentEntry)
                ReminderDetailScreen(
                    reminderId = id,
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { editId ->
                        navController.navigate(Routes.RemindersEdit.replace("{id}", editId))
                    },
                    vm = vm,
                )
            }
            composable(Routes.RemindersNew) {
                ReminderFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.RemindersEdit,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                ReminderFormScreen(
                    editReminderId = id,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(Routes.SettingsIndex) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWakeWord = { navController.navigate(Routes.SettingsWakeWord) },
                onNavigateToVoice = { navController.navigate(Routes.SettingsVoice) },
                onNavigateToNotifications = { navController.navigate(Routes.SettingsNotifications) },
                onNavigateToLocation = { navController.navigate(Routes.SettingsLocation) },
                onNavigateToSavedPlaces = { navController.navigate(Routes.NicknamesIndex) },
                onNavigateToAbout = { navController.navigate(Routes.SettingsAbout) },
            )
        }
        composable(Routes.SettingsWakeWord) {
            WakeWordSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SettingsVoice) {
            VoiceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SettingsNotifications) {
            NotificationSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SettingsLocation) {
            LocationSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SettingsAbout) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.NicknamesIndex) {
            NicknamesScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateNickname = {
                    navController.navigate(Routes.NicknamesEdit.replace("{id}", "new"))
                },
                onEditNickname = { id ->
                    navController.navigate(Routes.NicknamesEdit.replace("{id}", id))
                },
            )
        }
        composable(
            route = Routes.NicknamesEdit,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            NicknameEditScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
