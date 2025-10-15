package com.swooby.alfred.ui.events

import android.Manifest
import android.app.ComponentCaller
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.notification.FooNotificationListener
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.smartfoo.android.core.texttospeech.FooTextToSpeechHelper
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.R
import com.swooby.alfred.core.profile.AudioProfileGateReason
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.settings.DefaultThemePreferences
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.settings.ThemePreferences
import com.swooby.alfred.support.AppShutdownManager
import com.swooby.alfred.ui.MainActivity
import com.swooby.alfred.ui.theme.AlfredTheme
import com.swooby.alfred.ui.theme.ThemeSeedGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventListActivity : ComponentActivity() {
    companion object {
        private val TAG = FooLog.TAG(EventListActivity::class.java)

        const val EXTRA_USER_ID = "event_list_extra_user_id"

        private const val DEFAULT_USER_ID = "u_local"

        private const val ACTION_PIN = "com.swooby.alfred.ui.events.EventListActivity.action.PIN"

        fun intentShow(context: Context): Intent =
            Intent(context, EventListActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun intentPin(context: Context): Intent =
            Intent(context, EventListActivity::class.java)
                .setAction(ACTION_PIN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private val persistentNotificationDialogVisible = MutableStateFlow(false)
    private lateinit var debugNotificationController: DebugNotificationController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as AlfredApp
        val activity = this
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: DEFAULT_USER_ID
        val bluetoothManager = ContextCompat.getSystemService(app, BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        val viewModelFactory =
            EventListViewModel.Factory(
                eventDao = app.db.events(),
                userId = userId,
                audioProfileController = app.audioProfiles,
            )
        val initials = userId.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        PipelineService.start(this)
        debugNotificationController = DebugNotificationController(this, lifecycleScope)

        setContent {
            val themePreferences: ThemePreferences by app.settings.themePreferencesFlow
                .collectAsState(initial = DefaultThemePreferences)
            val shouldShowPersistentNotificationDialog by persistentNotificationDialogVisible
                .collectAsState()
            val themeMode = themePreferences.mode
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                }

            val paletteSeed = themePreferences.seedArgb

            AlfredTheme(
                darkTheme = darkTheme,
                customSeedArgb = paletteSeed,
            ) {
                val colorScheme = MaterialTheme.colorScheme
                val surfaceColor = colorScheme.surface
                val surfaceVariantColor = colorScheme.surfaceVariant
                val useLightSystemIcons = surfaceColor.luminance() > 0.5f

                SideEffect {
                    val transparent = Color.Transparent.toArgb()
                    val surfaceVariantArgb = surfaceVariantColor.toArgb()
                    val statusBarStyle =
                        if (useLightSystemIcons) {
                            SystemBarStyle.light(
                                scrim = transparent,
                                darkScrim = surfaceVariantArgb,
                            )
                        } else {
                            SystemBarStyle.dark(transparent)
                        }
                    val navigationBarStyle =
                        if (useLightSystemIcons) {
                            SystemBarStyle.light(
                                scrim = transparent,
                                darkScrim = surfaceVariantArgb,
                            )
                        } else {
                            SystemBarStyle.dark(transparent)
                        }
                    enableEdgeToEdge(
                        statusBarStyle = statusBarStyle,
                        navigationBarStyle = navigationBarStyle,
                    )
                }

                val viewModel: EventListViewModel = viewModel(factory = viewModelFactory)
                val bluetoothPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        viewModel.refreshAudioProfilePermissions()
                        if (!granted) {
                            Toast
                                .makeText(
                                    activity,
                                    activity.getString(R.string.event_list_audio_profiles_permission_denied),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                val handleBluetoothPermissionRequest: () -> Unit = {
                    if (bluetoothAdapter == null) {
                        viewModel.refreshAudioProfilePermissions()
                    } else {
                        val permission = Manifest.permission.BLUETOOTH_CONNECT
                        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.refreshAudioProfilePermissions()
                        } else {
                            bluetoothPermissionLauncher.launch(permission)
                        }
                    }
                }
                val uiState by viewModel.state.collectAsState()
                val settingsScope = rememberCoroutineScope()
                var noisyDebugActive by remember { mutableStateOf(debugNotificationController.isNoisyActive()) }
                var progressDebugActive by remember { mutableStateOf(debugNotificationController.isProgressActive()) }

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
                    onTextToSpeechTestRequested = {
                        speakTextToSpeechTest()
                    },
                    debugNoisyNotificationActive = noisyDebugActive,
                    onToggleDebugNoisyNotification = {
                        noisyDebugActive = debugNotificationController.toggleNoisyNotification()
                    },
                    debugProgressNotificationActive = progressDebugActive,
                    onToggleDebugProgressNotification = {
                        progressDebugActive = debugNotificationController.toggleProgressNotification()
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
                    onAudioProfileSelect = viewModel::selectAudioProfile,
                    onEnsureBluetoothPermission = handleBluetoothPermissionRequest,
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
                    },
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
                        },
                    )
                }
            }
        }

        val action = intent.action
        if (action == ACTION_PIN) {
            showPersistentNotificationDialog()
        }
    }

    override fun onNewIntent(
        intent: Intent,
        caller: ComponentCaller,
    ) {
        super.onNewIntent(intent, caller)
        if (intent.action == ACTION_PIN) {
            showPersistentNotificationDialog()
        }
    }

    override fun onDestroy() {
        FooLog.v(TAG, "+onDestroy()")
        hidePersistentNotificationDialog()
        if (::debugNotificationController.isInitialized) {
            debugNotificationController.shutdown()
        }
        super.onDestroy()
        FooLog.v(TAG, "-onDestroy()")
    }

    private fun showPersistentNotificationDialog() {
        persistentNotificationDialogVisible.value = true
    }

    private fun hidePersistentNotificationDialog() {
        persistentNotificationDialogVisible.value = false
    }

    private fun speakTextToSpeechTest() {
        val app = application as AlfredApp
        val gate = app.audioProfiles.evaluateGate()
        if (!gate.allow) {
            val messageId =
                when (gate.reason) {
                    AudioProfileGateReason.PROFILE_DISABLED -> R.string.event_list_tts_test_blocked_disabled
                    AudioProfileGateReason.NO_ACTIVE_DEVICES -> R.string.event_list_tts_test_blocked_no_device
                    else -> R.string.event_list_tts_test_blocked_generic
                }
            Toast.makeText(this, getString(messageId), Toast.LENGTH_LONG).show()
            FooLog.i(TAG, "speakTextToSpeechTest: blocked reason=${gate.reason}")
            return
        }
        val phrase = getString(R.string.event_list_tts_test_phrase)
        FooLog.i(TAG, "speakTextToSpeechTest: speaking test phrase")
        FooTextToSpeech.speak(app, phrase)
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
                    command,
                ),
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

                    Toast
                        .makeText(
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
    val messageRes =
        if (isNotificationPinned) {
            R.string.alfred_notification_persistent_dialog_message_disable
        } else {
            R.string.alfred_notification_persistent_dialog_message
        }
    val commandRes =
        if (isNotificationPinned) {
            R.string.alfred_notification_persistent_command_disable
        } else {
            R.string.alfred_notification_persistent_command_enable
        }
    val message = stringResource(messageRes)
    val command = stringResource(commandRes, packageName)
    val copyLabel = stringResource(R.string.alfred_notification_persistent_copy_button)
    val ignoreLabel = stringResource(R.string.alfred_notification_persistent_ignore_button)

    AlertDialog(
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onCopyCommand(command) }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { onCopyCommand(command) },
                    modifier = Modifier.fillMaxWidth(),
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
        dismissButton =
            if (isNotificationPinned) {
                null
            } else {
                {
                    TextButton(onClick = { onDismiss(true) }) {
                        Text(text = ignoreLabel)
                    }
                }
            },
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
            onDismiss = {},
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
            onDismiss = {},
        )
    }
}

private class DebugNotificationController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private val TAG = FooLog.TAG(DebugNotificationController::class.java)

        private const val CHANNEL_ID = "alfred.debug.notifications"
        private const val NOISY_NOTIFICATION_ID = 0xD09
        private const val PROGRESS_NOTIFICATION_ID = 0xD0A
        private const val NOISY_PERIOD_MS = 10_000L
        private const val PROGRESS_RESET_DELAY_MS = 5_000L
        private const val PROGRESS_STEPS = 100
        private const val PROGRESS_TOTAL_MS = 60_000L
        private const val PROGRESS_STEP_DELAY_MS = PROGRESS_TOTAL_MS / PROGRESS_STEPS
    }

    private val notificationManager: NotificationManager =
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var noisyJob: Job? = null
    private var progressJob: Job? = null

    fun isNoisyActive(): Boolean =
        (
            @Suppress("MemberExtensionConflict")
            noisyJob?.isActive
                == true
        )

    fun isProgressActive(): Boolean =
        (
            @Suppress("MemberExtensionConflict")
            progressJob?.isActive
                == true
        )

    fun toggleNoisyNotification(): Boolean =
        if (isNoisyActive()) {
            stopNoisyNotification()
            false
        } else {
            startNoisyNotification()
            isNoisyActive()
        }

    fun toggleProgressNotification(): Boolean =
        if (isProgressActive()) {
            stopProgressNotification()
            false
        } else {
            startProgressNotification()
            isProgressActive()
        }

    fun shutdown() {
        stopNoisyNotification()
        stopProgressNotification()
    }

    private fun startNoisyNotification() {
        if (isNoisyActive()) return
        ensureChannel()
        if (!canPostNotifications()) {
            FooLog.w(TAG, "#DEBUG_NOTIFS noisy: POST_NOTIFICATIONS not granted")
            return
        }
        val title = context.getString(R.string.debug_notification_noisy_title)
        val text = context.getString(R.string.debug_notification_noisy_text)
        val job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val notification =
                        builder()
                            .setContentTitle(title)
                            .setContentText(text)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                            .setWhen(System.currentTimeMillis())
                            .build()
                    notifySafely(NOISY_NOTIFICATION_ID, notification)
                    delay(NOISY_PERIOD_MS)
                }
            }
        job.invokeOnCompletion {
            if (noisyJob == job) {
                noisyJob = null
            }
        }
        noisyJob = job
    }

    private fun stopNoisyNotification() {
        noisyJob?.cancel()
        noisyJob = null
        notificationManager.cancel(NOISY_NOTIFICATION_ID)
    }

    private fun startProgressNotification() {
        if (isProgressActive()) return
        ensureChannel()
        if (!canPostNotifications()) {
            FooLog.w(TAG, "#DEBUG_NOTIFS progress: POST_NOTIFICATIONS not granted")
            return
        }
        val title = context.getString(R.string.debug_notification_progress_title)
        val job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    for (progress in 0..PROGRESS_STEPS) {
                        if (!isActive) break
                        val text =
                            context.getString(
                                R.string.debug_notification_progress_text,
                                progress,
                            )
                        val notification =
                            builder()
                                .setContentTitle(title)
                                .setContentText(text)
                                .setOnlyAlertOnce(true)
                                .setOngoing(true)
                                .setProgress(PROGRESS_STEPS, progress, false)
                                .build()
                        notifySafely(PROGRESS_NOTIFICATION_ID, notification)
                        delay(PROGRESS_STEP_DELAY_MS)
                    }
                    if (!isActive) break
                    val completionNotification =
                        builder()
                            .setContentTitle(title)
                            .setContentText(context.getString(R.string.debug_notification_progress_complete))
                            .setOnlyAlertOnce(true)
                            .setOngoing(false)
                            .setProgress(0, 0, false)
                            .build()
                    notifySafely(PROGRESS_NOTIFICATION_ID, completionNotification)
                    delay(PROGRESS_RESET_DELAY_MS)
                }
            }
        job.invokeOnCompletion {
            if (progressJob == job) {
                progressJob = null
            }
        }
        progressJob = job
    }

    private fun stopProgressNotification() {
        progressJob?.cancel()
        progressJob = null
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    private fun notifySafely(
        id: Int,
        notification: android.app.Notification,
    ) {
        if (canPostNotifications()) {
            notificationManager.notify(id, notification)
        } else {
            FooLog.w(TAG, "#DEBUG_NOTIFS notifySafely: POST_NOTIFICATIONS missing; skipping id=$id")
        }
    }

    private fun ensureChannel() {
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.debug_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.debug_notification_channel_description)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

    private fun canPostNotifications(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
