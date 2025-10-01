package com.swooby.alfred.ui.permissions

import android.Manifest
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swooby.alfred.sources.NotifSvc
import com.swooby.alfred.util.*

@Composable
fun PermissionsScreen(
    onReadyToStart: () -> Unit
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // --- status state ---
    var notifGranted by remember {
        mutableStateOf(
            isNotificationPermissionGranted(ctx)
        )
    }
    var listenerGranted by remember {
        mutableStateOf(
            hasNotificationListenerAccess(
                ctx,
                NotifSvc::class.java
            )
        )
    }
    var ignoringDoze by remember { mutableStateOf(isIgnoringBatteryOptimizations(ctx)) }

    fun refreshAll() {
        notifGranted = isNotificationPermissionGranted(ctx)
        listenerGranted = hasNotificationListenerAccess(ctx, NotifSvc::class.java)
        ignoringDoze = isIgnoringBatteryOptimizations(ctx)
    }

    // --- runtime POST_NOTIFICATIONS launcher (unchanged) ---
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Some OEMs return false positives; re-check authoritative state.
        notifGranted = granted || isNotificationPermissionGranted(ctx)
    }

    // ✅ 1) Observe the secure setting for enabled notification listeners
    DisposableEffect(Unit) {
        val uri = Settings.Secure.getUriFor("enabled_notification_listeners")
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // this fires when user toggles access in Settings
                listenerGranted = hasNotificationListenerAccess(ctx, NotifSvc::class.java)
            }
        }
        ctx.contentResolver.registerContentObserver(uri, /*notifyForDescendants=*/false, observer)
        onDispose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    // ✅ 2) Also refresh on resume (covers other settings like battery optimization)
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAll()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // (UI below unchanged, except we removed the old LaunchedEffect(delay) block)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Alfred Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        PermissionCard(
            title = "Post Notifications",
            description = "Lets Alfred speak and show status; required on Android 13+.",
            granted = notifGranted,
            actionLabel = if (Build.VERSION.SDK_INT >= 33) "Grant" else "Open Settings",
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ctx.startActivity(intentAppNotificationSettings(ctx))
                }
            }
        )

        PermissionCard(
            title = "Notification Listener Access",
            description = "Allows Alfred to read media sessions and notifications for smarter announcements.",
            granted = listenerGranted,
            actionLabel = "Open Settings",
            onClick = { ctx.startActivity(intentOpenNotificationListenerSettings()) }
        )

        PermissionCard(
            title = "Ignore Battery Optimizations",
            description = "Keeps Alfred responsive in the background. Recommended.",
            granted = ignoringDoze,
            actionLabel = "Allow",
            onClick = { ctx.startActivity(intentRequestIgnoreBatteryOptimizations(ctx)) },
            optional = true
        )

        val essentialsGranted = notifGranted && listenerGranted
        Button(
            enabled = essentialsGranted,
            onClick = onReadyToStart,
            modifier = Modifier.align(Alignment.End)
        ) { Text(if (essentialsGranted) "Start Alfred" else "Complete required steps") }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    optional: Boolean = false
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                val status = when {
                    granted -> "Granted"
                    optional -> "Optional"
                    else -> "Missing"
                }
                AssistChip(onClick = {}, label = { Text(status) }, enabled = false)
            }
            Text(
                if (optional) "$description (This is optional; Alfred works without it.)"
                else description,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClick) { Text(actionLabel) }
            }
        }
    }
}
