package com.swooby.alfred.ui.permissions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.DefaultThemePreferences
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.settings.ThemePreferences
import com.swooby.alfred.ui.events.EventListActivity
import com.swooby.alfred.ui.theme.AlfredTheme

class PermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as AlfredApp

        setContent {
            val themePreferences: ThemePreferences by app.settings.themePreferencesFlow
                .collectAsState(initial = DefaultThemePreferences)
            val themeMode = themePreferences.mode
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                }

            val paletteSeed = themePreferences.seedArgb

            AlfredTheme(
                darkTheme = darkTheme,
                customSeedArgb = paletteSeed,
            ) {
                val colorScheme = MaterialTheme.colorScheme
                val useLightStatusIcons = colorScheme.surface.luminance() > 0.5f

                SideEffect {
                    val surfaceColor = colorScheme.surface.toArgb()
                    val surfaceFallbackColor = colorScheme.surfaceVariant.toArgb()
                    val statusBarStyle =
                        if (useLightStatusIcons) {
                            SystemBarStyle.light(
                                scrim = surfaceColor,
                                darkScrim = surfaceFallbackColor,
                            )
                        } else {
                            SystemBarStyle.dark(surfaceColor)
                        }
                    enableEdgeToEdge(
                        statusBarStyle = statusBarStyle,
                        navigationBarStyle = statusBarStyle,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surface,
                ) {
                    PermissionsScreen(
                        onEssentialsGranted = {
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, PipelineService::class.java),
                            )
                            val intent =
                                Intent(this, EventListActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            startActivity(intent)
                            finish()
                        },
                    )
                }
            }
        }
    }
}
