package com.swooby.alfred.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.SettingsScreen
import com.swooby.alfred.sources.NotifSvc
import com.swooby.alfred.ui.permissions.PermissionsScreen
import com.swooby.alfred.util.hasNotificationListenerAccess
import com.swooby.alfred.util.isNotificationPermissionGranted
import com.swooby.alfred.R

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlfredApp

        setContent {
            MaterialTheme {
                val colorScheme = MaterialTheme.colorScheme

                SideEffect {
                    window.statusBarColor = colorScheme.primary.toArgb()
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        ?.isAppearanceLightStatusBars = false
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.main_top_app_bar_title)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.primary,
                                titleContentColor = colorScheme.onPrimary,
                                navigationIconContentColor = colorScheme.onPrimary,
                                actionIconContentColor = colorScheme.onPrimary
                            )
                        )
                    },
                    containerColor = colorScheme.surfaceVariant
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = colorScheme.surfaceVariant
                    ) {
                        val ctx = LocalContext.current

                        var essentialsGranted by remember {
                            mutableStateOf(
                                isNotificationPermissionGranted(ctx) &&
                                        hasNotificationListenerAccess(ctx, NotifSvc::class.java)
                            )
                        }
                        // Auto-start service when essentials are granted
                        if (essentialsGranted) {
                            LaunchedEffect(Unit) {
                                startForegroundService(Intent(this@MainActivity, PipelineService::class.java))
                            }
                            SettingsScreen(app, modifier = Modifier.fillMaxSize())
                        } else {
                            PermissionsScreen(
                                onEssentialsGranted = {
                                    essentialsGranted = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
