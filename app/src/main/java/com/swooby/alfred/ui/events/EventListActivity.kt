package com.swooby.alfred.ui.events

import android.app.ComponentCaller
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.notification.FooNotificationListener
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeechHelper
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.R
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.DefaultThemePreferences
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.settings.ThemePreferences
import com.swooby.alfred.support.AppShutdownManager
import com.swooby.alfred.ui.MainActivity
import com.swooby.alfred.ui.theme.AlfredTheme
import com.swooby.alfred.ui.theme.ThemeSeedGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

class EventListActivity : ComponentActivity() {
    companion object {
        private val TAG = FooLog.TAG(EventListActivity::class.java)

        const val EXTRA_USER_ID = "event_list_extra_user_id"

        private const val DEFAULT_USER_ID = "u_local"

        private const val ACTION_PIN = "com.swooby.alfred.ui.events.EventListActivity.action.PIN"

        fun pinIntent(context: Context): Intent =
            Intent(context, EventListActivity::class.java)
                .setAction(ACTION_PIN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private val persistentNotificationDialogVisible = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as AlfredApp
        val activity = this
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
            val shouldShowPersistentNotificationDialog by persistentNotificationDialogVisible
                .collectAsState()
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
                val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                val useLightSystemIcons = surfaceColor.luminance() > 0.5f

                SideEffect {
                    val transparent = Color.Transparent.toArgb()
                    val surfaceVariantArgb = surfaceVariantColor.toArgb()
                    val statusBarStyle = if (useLightSystemIcons) {
                        SystemBarStyle.light(
                            scrim = transparent,
                            darkScrim = surfaceVariantArgb
                        )
                    } else {
                        SystemBarStyle.dark(transparent)
                    }
                    val navigationBarStyle = if (useLightSystemIcons) {
                        SystemBarStyle.light(
                            scrim = transparent,
                            darkScrim = surfaceVariantArgb
                        )
                    } else {
                        SystemBarStyle.dark(transparent)
                    }
                    enableEdgeToEdge(
                        statusBarStyle = statusBarStyle,
                        navigationBarStyle = navigationBarStyle
                    )
                }

                val viewModel: EventListViewModel = viewModel(factory = viewModelFactory)
                val uiState by viewModel.state.collectAsState()
                val settingsScope = rememberCoroutineScope()

                EventListScreen(
                    state = uiState,
                    userInitials = initials,
                    themeMode = themeMode,
                    onQueryChange = viewModel::onQueryChange,
                    onNavigateToSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onNotificationAccessRequested = {
                        FooNotificationListener.startActivityNotificationListenerSettings(activity)
                    },
                    onApplicationInfoRequested = {
                        FooPlatformUtils.showAppSettings(activity)
                    },
                    onDeveloperOptionsRequested = {
                        FooPlatformUtils.showDevelopmentSettings(activity)
                    },
                    onAdbWirelessRequested = {
                        FooPlatformUtils.showAdbWirelessSettings(activity)
                    },
                    onTextToSpeechSettingsRequested = {
                        FooTextToSpeechHelper.showTextToSpeechSettings(activity)
                    },
                    onPersistentNotification = {
                        showPersistentNotificationDialog()
                    },
                    onQuitRequested = {
                        AppShutdownManager.requestQuit(activity)
                        activity.finishAffinity()
                        activity.finishAndRemoveTask()
                    },
                    onSelectionModeChange = viewModel::setSelectionMode,
                    onEventSelectionChange = viewModel::setEventSelection,
                    onSelectAll = viewModel::selectAll,
                    onUnselectAll = viewModel::unselectAll,
                    onDeleteSelected = viewModel::deleteSelected,
                    onLoadMore = viewModel::loadMore,
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

                if (shouldShowPersistentNotificationDialog) {
                    val notificationPinned = PipelineService.isOngoingNotificationNoDismiss(activity)
                    PersistentNotificationDialog(
                        packageName = packageName,
                        isNotificationPinned = notificationPinned,
                        onCopyCommand = { command -> copyCommandToClipboard(command) },
                        onDismiss = { ignoreChoice ->
                            hidePersistentNotificationDialog()
                            handlePersistentNotificationResult(ignoreChoice)
                        }
                    )
                }
            }
        }

        val action = intent.action
        if (action == ACTION_PIN) {
            showPersistentNotificationDialog()
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        if (intent.action == ACTION_PIN) {
            showPersistentNotificationDialog()
        }
    }

    override fun onDestroy() {
        FooLog.v(TAG, "+onDestroy()")
        hidePersistentNotificationDialog()
        super.onDestroy()
        FooLog.v(TAG, "-onDestroy()")
    }

    private fun showPersistentNotificationDialog() {
        persistentNotificationDialogVisible.value = true
    }

    private fun hidePersistentNotificationDialog() {
        persistentNotificationDialogVisible.value = false
    }

    private fun copyCommandToClipboard(command: String) {
        FooLog.i(TAG, "copyCommandToClipboard: command=${FooString.quote(command)}")
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            FooLog.w(TAG, "copyCommandToClipboard: Clipboard manager not available")
        } else {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "Persistent notification command",
                    command
                )
            )
        }
    }

    private fun handlePersistentNotificationResult(ignore: Boolean?) {
        FooLog.i(TAG, "handlePersistentNotificationResult(ignore=$ignore)")
        if (ignore == null) {
            FooLog.i(TAG, "handlePersistentNotificationResult: dismissed without explicit choice; leaving everything unchanged")
            return
        }

        val app = application as AlfredApp
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {

                app.settings.setPersistentNotificationActionIgnored(ignore)

                withContext(Dispatchers.Main) {

                    // Refresh the Notification to remove the "Pin" action
                    PipelineService.refreshNotification(this@EventListActivity)

                    Toast.makeText(
                        this@EventListActivity,
                        R.string.alfred_notification_persistent_confirmation,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}

@Composable
private fun PersistentNotificationDialog(
    packageName: String,
    isNotificationPinned: Boolean,
    onCopyCommand: (String) -> Unit,
    onDismiss: (Boolean?) -> Unit,
) {
    val title = stringResource(R.string.alfred_notification_persistent_dialog_title)
    val messageRes = if (isNotificationPinned) {
        R.string.alfred_notification_persistent_dialog_message_disable
    } else {
        R.string.alfred_notification_persistent_dialog_message
    }
    val commandRes = if (isNotificationPinned) {
        R.string.alfred_notification_persistent_command_disable
    } else {
        R.string.alfred_notification_persistent_command_enable
    }
    val message = stringResource(messageRes)
    val command = stringResource(commandRes, packageName)
    val copyLabel = stringResource(R.string.alfred_notification_persistent_copy_button)
    val ignoreLabel = stringResource(R.string.alfred_notification_persistent_ignore_button)

    AlertDialog(
        properties = DialogProperties(
            dismissOnClickOutside = false
        ),
        onDismissRequest = { onDismiss(null) },
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                SelectionContainer {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onCopyCommand(command) }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { onCopyCommand(command) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = copyLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(false) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = if (isNotificationPinned) null else { {
                TextButton(onClick = { onDismiss(true) }) {
                    Text(text = ignoreLabel)
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PersistentNotificationDialogNotPinnedPreview() {
    AlfredTheme {
        PersistentNotificationDialog(
            packageName = BuildConfig.PACKAGE_NAME,
            isNotificationPinned = false,
            onCopyCommand = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PersistentNotificationDialogPinnedPreview() {
    AlfredTheme {
        PersistentNotificationDialog(
            packageName = BuildConfig.PACKAGE_NAME,
            isNotificationPinned = true,
            onCopyCommand = {},
            onDismiss = {}
        )
    }
}
