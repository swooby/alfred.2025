package com.swooby.alfred.util

import android.service.notification.NotificationListenerService

object FooNotificationListener {
    fun notificationCancelReasonToString(reason: Int): String {
        return when (reason) {
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
    }
}