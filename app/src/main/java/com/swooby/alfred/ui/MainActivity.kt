package com.swooby.alfred.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.SettingsScreen
import com.swooby.alfred.sources.NotifSvc
import com.swooby.alfred.ui.permissions.PermissionsScreen
import com.swooby.alfred.util.hasNotificationListenerAccess
import com.swooby.alfred.util.isNotificationPermissionGranted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlfredApp

        setContent {
            MaterialTheme {
                Surface {
                    val ctx = LocalContext.current
                    // Check if essentials are already granted at startup
                    val essentialsGranted = isNotificationPermissionGranted(ctx) && 
                                          hasNotificationListenerAccess(ctx, NotifSvc::class.java)
                    var started by remember { mutableStateOf(essentialsGranted) }

                    // Auto-start service if essentials already granted
                    if (essentialsGranted && started) {
                        LaunchedEffect(Unit) {
                            startForegroundService(Intent(this@MainActivity, PipelineService::class.java))
                        }
                    }

                    if (!started) {
                        PermissionsScreen(
                            onReadyToStart = {
                                // Start foreground pipeline after essentials granted
                                startForegroundService(Intent(this@MainActivity, PipelineService::class.java))
                                started = true
                            }
                        )
                    } else {
                        // Show Settings after starting (you can add tabs later)
                        SettingsScreen(app)
                    }
                }
            }
        }
    }
}
