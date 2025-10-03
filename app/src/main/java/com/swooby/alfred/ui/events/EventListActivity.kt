package com.swooby.alfred.ui.events

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.ui.MainActivity

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
            MaterialTheme {
                SideEffect {
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    WindowCompat
                        .getInsetsController(window, window.decorView)
                        .apply {
                            isAppearanceLightStatusBars = true
                            isAppearanceLightNavigationBars = true
                        }
                }

                val viewModel: EventListViewModel = viewModel(factory = viewModelFactory)
                val uiState by viewModel.state.collectAsState()

                EventListScreen(
                    state = uiState,
                    userInitials = initials,
                    onQueryChange = viewModel::onQueryChange,
                    onRefresh = viewModel::refresh,
                    onNavigateToSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onSelectionModeChange = viewModel::setSelectionMode,
                    onEventSelectionChange = viewModel::setEventSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onDeleteSelected = viewModel::deleteSelected
                )
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID: String = "event_list_extra_user_id"
        private const val DEFAULT_USER_ID: String = "u_local"
    }
}
