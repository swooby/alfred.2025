package com.swooby.alfred.ui.events

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.DefaultThemePreferences
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.settings.ThemePreferences
import com.swooby.alfred.ui.MainActivity
import com.swooby.alfred.ui.theme.AlfredTheme
import com.swooby.alfred.ui.theme.ThemeSeedGenerator
import kotlinx.coroutines.launch

class EventListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as AlfredApp
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: DEFAULT_USER_ID
        val viewModelFactory = EventListViewModel.Factory(app.db.events(), userId)
        val initials = userId.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        ContextCompat.startForegroundService(
            this,
            Intent(this, PipelineService::class.java)
        )

        setContent {
            val themePreferences: ThemePreferences by app.settings.themePreferencesFlow
                .collectAsState(initial = DefaultThemePreferences)
            val themeMode = themePreferences.mode
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            val paletteSeed = themePreferences.seedArgb

            AlfredTheme(
                darkTheme = darkTheme,
                customSeedArgb = paletteSeed
            ) {
                val surfaceColor = MaterialTheme.colorScheme.surface
                val useLightSystemIcons = surfaceColor.luminance() > 0.5f

                SideEffect {
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        .apply {
                            isAppearanceLightStatusBars = useLightSystemIcons
                            isAppearanceLightNavigationBars = useLightSystemIcons
                        }
                }

                val viewModel: EventListViewModel = viewModel(factory = viewModelFactory)
                val uiState by viewModel.state.collectAsState()
                val settingsScope = rememberCoroutineScope()

                EventListScreen(
                    state = uiState,
                    userInitials = initials,
                    themeMode = themeMode,
                    onQueryChange = viewModel::onQueryChange,
                    onRefresh = viewModel::refresh,
                    onNavigateToSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onSelectionModeChange = viewModel::setSelectionMode,
                    onEventSelectionChange = viewModel::setEventSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onUnselectAll = viewModel::unselectAllVisible,
                    onDeleteSelected = viewModel::deleteSelected,
                    onThemeModeChange = { mode ->
                        settingsScope.launch { app.settings.setThemeMode(mode) }
                    },
                    onShuffleThemeRequest = {
                        settingsScope.launch {
                            val currentSeed = themePreferences.seedArgb
                            var newSeed = ThemeSeedGenerator.randomSeed()
                            var attempts = 0
                            while (currentSeed != null && newSeed == currentSeed && attempts < 4) {
                                newSeed = ThemeSeedGenerator.randomSeed()
                                attempts++
                            }
                            app.settings.setCustomThemeSeed(newSeed)
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID: String = "event_list_extra_user_id"
        private const val DEFAULT_USER_ID: String = "u_local"
    }
}
