package com.ranti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ranti.data.OnboardingPrefs
import com.ranti.navigation.NavGraph
import com.ranti.navigation.Routes
import com.ranti.ui.theme.RantiTheme

import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val themeMode by OnboardingPrefs.themeModeFlow(context)
                .collectAsStateWithLifecycle(initialValue = "system")
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            RantiTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val nav = rememberNavController()

                    var startDestination by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = if (OnboardingPrefs.isComplete(context)) {
                            Routes.Chat
                        } else {
                            Routes.OnboardingWelcome
                        }
                    }

                    startDestination?.let {
                        NavGraph(
                            navController = nav,
                            startDestination = it,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAppForeground = true
    }

    override fun onPause() {
        super.onPause()
        isAppForeground = false
    }

    companion object {
        @Volatile
        var isAppForeground: Boolean = false
    }
}
