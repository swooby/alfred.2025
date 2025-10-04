package com.swooby.alfred.ui.permissions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.ui.events.EventListActivity
import com.swooby.alfred.ui.theme.AlfredTheme
import com.swooby.alfred.settings.ThemeMode

class PermissionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as AlfredApp

        setContent {
            val themeMode by app.settings.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            AlfredTheme(darkTheme = darkTheme) {
                val colorScheme = MaterialTheme.colorScheme
                val useLightStatusIcons = colorScheme.surface.luminance() > 0.5f

                SideEffect {
                    window.statusBarColor = colorScheme.surface.toArgb()
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = useLightStatusIcons
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surface
                ) {
                    PermissionsScreen(
                        onEssentialsGranted = {
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, PipelineService::class.java)
                            )
                            val intent = Intent(this, EventListActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
