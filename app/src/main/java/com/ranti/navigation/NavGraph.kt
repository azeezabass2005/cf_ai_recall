package com.ranti.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ranti.ui.screens.chat.ChatScreen
import com.ranti.ui.screens.onboarding.PermissionsScreen
import com.ranti.ui.screens.onboarding.ReadyScreen
import com.ranti.ui.screens.onboarding.WakeWordSetupScreen
import com.ranti.ui.screens.onboarding.WelcomeScreen
import com.ranti.ui.screens.reminders.ReminderDetailScreen
import com.ranti.ui.screens.reminders.ReminderFormScreen
import com.ranti.ui.screens.reminders.RemindersScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Top-level navigation graph. Onboarding (§4) is wired up; reminders, nicknames,
 * and settings come online in their respective milestones. The constants in
 * [Routes] mirror the full SPEC §2 graph so future additions are pure additions.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    wakeEvents: StateFlow<Int> = MutableStateFlow(0),
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
                onOpenReminders = { navController.navigate(Routes.RemindersIndex) },
                onCreateReminder = { navController.navigate(Routes.RemindersNew) },
                wakeEvents = wakeEvents,
            )
        }

        composable(Routes.RemindersIndex) {
            RemindersScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateReminder = { navController.navigate(Routes.RemindersNew) },
                onReminderClick = { id ->
                    navController.navigate(Routes.RemindersDetail.replace("{id}", id))
                },
            )
        }
        composable(
            route = Routes.RemindersDetail,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            ReminderDetailScreen(
                reminderId = id,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { editId ->
                    navController.navigate(Routes.RemindersEdit.replace("{id}", editId))
                },
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
}
