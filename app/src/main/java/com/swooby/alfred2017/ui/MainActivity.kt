package com.swooby.alfred2017.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.swooby.alfred2017.AlfredApp
import com.swooby.alfred2017.pipeline.PipelineService
import com.swooby.alfred2017.settings.SettingsScreen
import com.swooby.alfred2017.ui.permissions.PermissionsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlfredApp

        setContent {
            MaterialTheme {
                Surface {
                    var started by remember { mutableStateOf(false) }

                    if (!started) {
                        PermissionsScreen(
                            onReadyToStart = {
                                // Start foreground pipeline after essentials granted
                                startForegroundService(Intent(this, PipelineService::class.java))
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
