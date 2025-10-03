package com.swooby.alfred.ui

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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.R
import com.swooby.alfred.settings.SettingsScreen
import com.swooby.alfred.ui.theme.AlfredTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlfredApp

        setContent {
            AlfredTheme {
                val colorScheme = MaterialTheme.colorScheme
                val useLightStatusIcons = colorScheme.primary.luminance() > 0.5f

                SideEffect {
                    window.statusBarColor = colorScheme.primary.toArgb()
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = useLightStatusIcons
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
                        SettingsScreen(app, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
