package com.ranti.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ranti.data.OnboardingPrefs
import com.ranti.ui.theme.LocalRantiColors
import com.ranti.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * SPEC §13.5 — Location settings.
 *
 * Shows live GPS status, a geofence radius slider, and an active-geofences count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val ranti = LocalRantiColors.current
    val scope = rememberCoroutineScope()

    // Radius stored as an index (0–3) mapping to [50, 100, 200, 500] metres.
    val radiusOptions = listOf(50, 100, 200, 500)

    var gpsEnabled by remember { mutableStateOf(false) }
    var radiusIndex by remember { mutableIntStateOf(1) } // default 100m
    var activeGeofences by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val storedRadius = OnboardingPrefs.getGeofenceRadius(context)
        radiusIndex = radiusOptions.indexOf(storedRadius).coerceAtLeast(1)
        activeGeofences = com.ranti.location.GeofencePrefs.getActiveCount(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Location", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.base),
        ) {
            // GPS status
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (gpsEnabled) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, tint = ranti.success)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("GPS: On", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi, modifier = Modifier.weight(1f))
                } else {
                    Icon(Icons.Default.GpsOff, contentDescription = null, tint = ranti.error)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("GPS: Off", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text("Open settings")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = ranti.borderSubtle)

            // Geofence radius slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = ranti.accent)
                Spacer(Modifier.width(Spacing.sm))
                Text("Geofence radius", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi)
                Spacer(Modifier.weight(1f))
                Text("${radiusOptions[radiusIndex]}m", style = MaterialTheme.typography.bodyMedium, color = ranti.textMid)
            }
            Spacer(Modifier.height(Spacing.xs))
            // index 0..3, steps=2 gives 4 snap positions
            Slider(
                value = radiusIndex.toFloat(),
                onValueChange = { radiusIndex = it.toInt() },
                onValueChangeFinished = {
                    scope.launch {
                        OnboardingPrefs.setGeofenceRadius(context, radiusOptions[radiusIndex])
                    }
                },
                valueRange = 0f..3f,
                steps = 2,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                radiusOptions.forEach { r ->
                    Text("${r}m", style = MaterialTheme.typography.labelSmall, color = ranti.textLo)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = ranti.borderSubtle)

            // Active geofences count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = ranti.textMid)
                Spacer(Modifier.width(Spacing.sm))
                Text("Active geofences", style = MaterialTheme.typography.bodyLarge, color = ranti.textHi, modifier = Modifier.weight(1f))
                Text("$activeGeofences / 100", style = MaterialTheme.typography.bodyMedium, color = ranti.textMid)
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Location reminders use GPS periodically. This may affect battery life.",
                style = MaterialTheme.typography.bodySmall,
                color = ranti.textLo,
            )
        }
    }
}
