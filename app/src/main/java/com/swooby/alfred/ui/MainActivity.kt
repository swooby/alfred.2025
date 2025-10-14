package com.swooby.alfred.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.R
import com.swooby.alfred.settings.DefaultThemePreferences
import com.swooby.alfred.settings.SettingsScreen
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.settings.ThemePreferences
import com.swooby.alfred.ui.theme.AlfredTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
                val surfaceColor = colorScheme.surface
                val surfaceVariantColor = colorScheme.surfaceVariant
                val useLightSystemIcons = surfaceColor.luminance() > 0.5f

                SideEffect {
                    val transparent = Color.Transparent.toArgb()
                    val surfaceVariantArgb = surfaceVariantColor.toArgb()
                    val statusBarStyle =
                        if (useLightSystemIcons) {
                            SystemBarStyle.light(
                                scrim = transparent,
                                darkScrim = surfaceVariantArgb,
                            )
                        } else {
                            SystemBarStyle.dark(transparent)
                        }
                    val navigationBarStyle =
                        if (useLightSystemIcons) {
                            SystemBarStyle.light(
                                scrim = transparent,
                                darkScrim = surfaceVariantArgb,
                            )
                        } else {
                            SystemBarStyle.dark(transparent)
                        }
                    enableEdgeToEdge(
                        statusBarStyle = statusBarStyle,
                        navigationBarStyle = navigationBarStyle,
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.main_top_app_bar_title)) },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = colorScheme.primary,
                                    titleContentColor = colorScheme.onPrimary,
                                    navigationIconContentColor = colorScheme.onPrimary,
                                    actionIconContentColor = colorScheme.onPrimary,
                                ),
                        )
                    },
                    containerColor = colorScheme.surfaceVariant,
                ) { innerPadding ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        color = colorScheme.surfaceVariant,
                    ) {
                        SettingsScreen(app, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
