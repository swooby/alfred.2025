package com.smartfoo.android.core.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.smartfoo.android.core.logging.FooLog

@Suppress("unused")
object FooNotificationListener {
    private val TAG = FooLog.TAG(FooNotificationListener::class.java)

    fun notificationCancelReasonToString(reason: Int): String =
        when (reason) {
            NotificationListenerService.REASON_CLICK -> "REASON_CLICK"
            NotificationListenerService.REASON_CANCEL -> "REASON_CANCEL"
            NotificationListenerService.REASON_CANCEL_ALL -> "REASON_CANCEL_ALL"
            NotificationListenerService.REASON_ERROR -> "REASON_ERROR"
            NotificationListenerService.REASON_PACKAGE_CHANGED -> "REASON_PACKAGE_CHANGED"
            NotificationListenerService.REASON_USER_STOPPED -> "REASON_USER_STOPPED"
            NotificationListenerService.REASON_PACKAGE_BANNED -> "REASON_PACKAGE_BANNED"
            NotificationListenerService.REASON_APP_CANCEL -> "REASON_APP_CANCEL"
            NotificationListenerService.REASON_APP_CANCEL_ALL -> "REASON_APP_CANCEL_ALL"
            NotificationListenerService.REASON_LISTENER_CANCEL -> "REASON_LISTENER_CANCEL"
            NotificationListenerService.REASON_LISTENER_CANCEL_ALL -> "REASON_LISTENER_CANCEL_ALL"
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> "REASON_GROUP_SUMMARY_CANCELED"
            NotificationListenerService.REASON_GROUP_OPTIMIZATION -> "REASON_GROUP_OPTIMIZATION"
            NotificationListenerService.REASON_PACKAGE_SUSPENDED -> "REASON_PACKAGE_SUSPENDED"
            NotificationListenerService.REASON_PROFILE_TURNED_OFF -> "REASON_PROFILE_TURNED_OFF"
            NotificationListenerService.REASON_UNAUTOBUNDLED -> "REASON_UNAUTOBUNDLED"
            NotificationListenerService.REASON_CHANNEL_BANNED -> "REASON_CHANNEL_BANNED"
            NotificationListenerService.REASON_SNOOZED -> "REASON_SNOOZED"
            NotificationListenerService.REASON_TIMEOUT -> "REASON_TIMEOUT"
            NotificationListenerService.REASON_CHANNEL_REMOVED -> "REASON_CHANNEL_REMOVED"
            NotificationListenerService.REASON_CLEAR_DATA -> "REASON_CLEAR_DATA"
            NotificationListenerService.REASON_ASSISTANT_CANCEL -> "REASON_ASSISTANT_CANCEL"
            NotificationListenerService.REASON_LOCKDOWN -> "REASON_LOCKDOWN"
            else -> "UNKNOWN"
        }.let { "$it($reason)" }

    /**
     * Needs to be reasonably longer than the app startup time.
     *
     * NOTE1 that the app startup time can be a few seconds when debugging.
     *
     * NOTE2 that this will time out if paused too long at a debug breakpoint while launching.
     */
    @Suppress("ClassName")
    object NOTIFICATION_LISTENER_SERVICE_CONNECTED_TIMEOUT_MILLIS {
        const val NORMAL: Int = 1500
        const val SLOW: Int = 6000

        fun getRecommendedTimeout(slow: Boolean): Int = if (slow) SLOW else NORMAL
    }

    /**
     * Per hidden field [android.provider.Settings.Secure] `ENABLED_NOTIFICATION_LISTENERS`
     */
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    @JvmStatic
    fun hasNotificationListenerAccess(
        context: Context,
        notificationListenerServiceClass: Class<out NotificationListenerService>,
    ): Boolean {
        val notificationListenerServiceLookingFor =
            ComponentName(context, notificationListenerServiceClass)
        FooLog.d(TAG, "hasNotificationListenerAccess: notificationListenerServiceLookingFor=$notificationListenerServiceLookingFor")

        val notificationListenersString =
            Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
        if (notificationListenersString != null) {
            val notificationListeners = notificationListenersString.split(':').dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in notificationListeners.indices) {
                val notificationListener = ComponentName.unflattenFromString(notificationListeners[i])
                FooLog.d(TAG, "hasNotificationListenerAccess: notificationListeners[$i]=$notificationListener")
                if (notificationListenerServiceLookingFor == notificationListener) {
                    FooLog.i(TAG, "hasNotificationListenerAccess: found match; return true")
                    return true
                }
            }
        }

        FooLog.w(TAG, "hasNotificationListenerAccess: found NO match; return false")
        return false
    }

    @JvmStatic
    val intentNotificationListenerSettings: Intent
        get() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    @JvmStatic
    fun startActivityNotificationListenerSettings(context: Context) {
        context.startActivity(intentNotificationListenerSettings)
    }

    fun requestNotificationListenerUnbind(
        context: Context,
        notificationListenerServiceClass: Class<out NotificationListenerService>,
    ) {
        runCatching {
            val componentName = ComponentName(context, notificationListenerServiceClass)
            FooLog.v(TAG, "requestNotificationListenerUnbind: +NotificationListenerService.requestUnbind($componentName)")
            NotificationListenerService.requestUnbind(componentName)
            FooLog.v(TAG, "requestNotificationListenerUnbind: -NotificationListenerService.requestUnbind($componentName)")
        }.onFailure { throwable ->
            FooLog.w(TAG, "requestNotificationListenerUnbind: failed", throwable)
        }
    }

    fun requestNotificationListenerRebind(
        context: Context,
        notificationListenerServiceClass: Class<out NotificationListenerService>,
    ) {
        runCatching {
            val componentName = ComponentName(context, notificationListenerServiceClass)
            FooLog.v(TAG, "requestNotificationListenerRebind: +NotificationListenerService.requestRebind($componentName)")
            NotificationListenerService.requestRebind(componentName)
            FooLog.v(TAG, "requestNotificationListenerRebind: -NotificationListenerService.requestRebind($componentName)")
        }.onFailure { throwable ->
            FooLog.w(TAG, "requestNotificationListenerRebind: failed", throwable)
        }
    }
}
