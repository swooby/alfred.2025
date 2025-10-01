package com.swooby.alfred2017.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.swooby.alfred2017.AlfredApp
import com.swooby.alfred2017.pipeline.PipelineService
import com.swooby.alfred2017.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start pipeline service
        startForegroundService(Intent(this, PipelineService::class.java))

        val app = application as AlfredApp
        setContent {
            MaterialTheme {
                Surface { SettingsScreen(app) }
            }
        }
    }
}