package com.swooby.alfred2017.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri

fun isNotificationPermissionGranted(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }
}

fun intentAppNotificationSettings(ctx: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    }

fun hasNotificationListenerAccess(context: Context, listenerClass: Class<*>): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val me = ComponentName(context, listenerClass).flattenToString()
    // The setting contains flattened component names separated by ':'
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

fun intentOpenNotificationListenerSettings(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

@SuppressLint("BatteryLife")
fun intentRequestIgnoreBatteryOptimizations(ctx: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${ctx.packageName}".toUri()
    }

fun isAccessibilityServiceEnabled(ctx: Context, serviceComponent: ComponentName): Boolean {
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = serviceComponent.flattenToString()
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

fun intentOpenAccessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
