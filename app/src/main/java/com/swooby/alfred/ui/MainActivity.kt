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

                    val notifGranted = isNotificationPermissionGranted(ctx)
                    val listenerGranted = hasNotificationListenerAccess(ctx, NotifSvc::class.java)
                    var essentialsGranted by remember { mutableStateOf(notifGranted && listenerGranted) }

                    // Auto-start service when essentials are granted
                    if (essentialsGranted) {
                        LaunchedEffect(Unit) {
                            startForegroundService(Intent(this@MainActivity, PipelineService::class.java))
                        }
                        SettingsScreen(app)
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
