package com.swooby.alfred.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(VERSION_CODES.TIRAMISU)
fun isNotificationPermissionGranted(ctx: Context): Boolean {
    return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

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
