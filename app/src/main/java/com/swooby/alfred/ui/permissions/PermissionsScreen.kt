package com.swooby.alfred.ui.permissions

import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartfoo.android.core.notification.FooNotificationListener
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.R
import com.swooby.alfred.sources.NotificationsSource
import com.swooby.alfred.util.intentRequestIgnoreBatteryOptimizations
import com.swooby.alfred.util.isCallStatePermissionGranted
import com.swooby.alfred.util.isIgnoringBatteryOptimizations
import com.swooby.alfred.util.isNotificationPermissionGranted

@Composable
fun PermissionsScreen(onEssentialsGranted: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // --- status state ---
    var notificationPermissionGranted by remember {
        mutableStateOf(
            isNotificationPermissionGranted(ctx),
        )
    }
    var callPermissionGranted by remember {
        mutableStateOf(
            isCallStatePermissionGranted(ctx),
        )
    }
    var listenerGranted by remember {
        mutableStateOf(
            FooNotificationListener.hasNotificationListenerAccess(ctx, NotificationsSource::class.java),
        )
    }
    var ignoringDoze by remember { mutableStateOf(isIgnoringBatteryOptimizations(ctx)) }

    val app = remember(ctx) { ctx.applicationContext as AlfredApp }

    LaunchedEffect(callPermissionGranted) {
        if (callPermissionGranted) {
            app.systemEvents.refreshCallStateObserver()
        }
    }

    fun refreshAll() {
        notificationPermissionGranted = isNotificationPermissionGranted(ctx)
        listenerGranted = FooNotificationListener.hasNotificationListenerAccess(ctx, NotificationsSource::class.java)
        ignoringDoze = isIgnoringBatteryOptimizations(ctx)
        callPermissionGranted = isCallStatePermissionGranted(ctx)
    }

    // --- runtime POST_NOTIFICATIONS launcher (unchanged) ---
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            // Some OEMs return false positives; re-check authoritative state.
            notificationPermissionGranted = granted || isNotificationPermissionGranted(ctx)
        }
    val callPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            callPermissionGranted = granted || isCallStatePermissionGranted(ctx)
        }

    // ✅ 1) Observe the secure setting for enabled notification listeners
    DisposableEffect(Unit) {
        val uri = Settings.Secure.getUriFor("enabled_notification_listeners")
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    // this fires when user toggles access in Settings
                    listenerGranted = FooNotificationListener.hasNotificationListenerAccess(ctx, NotificationsSource::class.java)
                }
            }
        ctx.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    // ✅ 2) Also refresh on resume (covers other settings like battery optimization)
    DisposableEffect(lifecycle) {
        val obs =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshAll()
            }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // (UI below unchanged, except we removed the old LaunchedEffect(delay) block)
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.permissions_title_setup),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        PermissionCard(
            title = stringResource(R.string.permissions_post_notifications_title),
            description = stringResource(R.string.permissions_post_notifications_description),
            granted = notificationPermissionGranted,
            actionLabel = stringResource(R.string.permissions_action_grant),
            onClick = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )

        PermissionCard(
            title = stringResource(R.string.permissions_notification_listener_title),
            description = stringResource(R.string.permissions_notification_listener_description),
            granted = listenerGranted,
            actionLabel = stringResource(R.string.permissions_action_open_settings),
            onClick = { FooNotificationListener.startActivityNotificationListenerSettings(ctx) },
        )

        PermissionCard(
            title = stringResource(R.string.permissions_phone_state_title),
            description = stringResource(R.string.permissions_phone_state_description),
            granted = callPermissionGranted,
            actionLabel = stringResource(R.string.permissions_action_grant),
            onClick = { callPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
        )

        PermissionCard(
            title = stringResource(R.string.permissions_ignore_battery_title),
            description = stringResource(R.string.permissions_ignore_battery_description),
            granted = ignoringDoze,
            actionLabel = stringResource(R.string.permissions_action_allow),
            onClick = { ctx.startActivity(intentRequestIgnoreBatteryOptimizations(ctx)) },
            optional = true,
        )

        val essentialsGranted = notificationPermissionGranted && listenerGranted && callPermissionGranted
        Button(
            enabled = essentialsGranted,
            onClick = onEssentialsGranted,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                if (essentialsGranted) {
                    stringResource(R.string.permissions_start_button)
                } else {
                    stringResource(R.string.permissions_complete_steps)
                },
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    optional: Boolean = false,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val status =
                    when {
                        granted -> stringResource(R.string.permissions_status_granted)
                        optional -> stringResource(R.string.permissions_status_optional)
                        else -> stringResource(R.string.permissions_status_missing)
                    }
                AssistChip(onClick = {}, label = { Text(status) }, enabled = false)
            }
            Text(
                if (optional) {
                    stringResource(R.string.permissions_optional_description, description)
                } else {
                    description
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClick) { Text(actionLabel) }
            }
        }
    }
}
