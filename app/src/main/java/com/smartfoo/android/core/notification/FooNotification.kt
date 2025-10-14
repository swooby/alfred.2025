package com.smartfoo.android.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.smartfoo.android.core.logging.FooLog

/**
 * References:
 * http://developer.android.com/guide/topics/ui/notifiers/notifications.html
 * http://developer.android.com/design/patterns/notifications.html
 */
@Suppress("unused")
class FooNotification private constructor() {
    companion object {
        private val TAG = FooLog.TAG(FooNotification::class.java)

        /**
         * Non-hidden duplicate of [android.app.Notification.FLAG_NO_DISMISS]
         */
        @Suppress("KDocUnresolvedReference")
        const val FLAG_NO_DISMISS = 0x00002000

        @JvmStatic
        fun hasFlags(
            notification: Notification?,
            flags: Int,
        ): Boolean = notification != null && (notification.flags and flags) != 0

        /**
         * Similar to [androidx.core.app.NotificationCompat.getOngoing]
         */
        @JvmStatic
        fun getNoDismiss(notification: Notification?): Boolean = hasFlags(notification, FLAG_NO_DISMISS)

        @JvmStatic
        fun findCallingAppNotification(
            context: Context,
            notificationId: Int,
        ): Notification? {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (notificationManager != null) {
                val activeNotifications = notificationManager.activeNotifications
                if (activeNotifications != null) {
                    for (statusBarNotification in activeNotifications) {
                        if (statusBarNotification.id == notificationId) {
                            return statusBarNotification.notification
                        }
                    }
                }
            }
            return null
        }

        /**
         * NOTE: Since Android 14 (API34) [androidx.core.app.NotificationCompat.Builder.setOngoing]
         * notifications **CAN** be dismissed by the user...
         *
         * ...unless...
         *
         * [https://www.reddit.com/r/tasker/comments/1fv9ez4/how_to_enable_nondismissible_persistent/](https://www.reddit.com/r/tasker/comments/1fv9ez4/how_to_enable_nondismissible_persistent/)
         *
         * (There are lots of goodies in this article that might be of some help in the future.)
         *
         * To enable:
         *
         * `adb shell appops set --uid ${packageName} SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS allow`
         *
         * This will add a `android.app.Notification.FLAG_NO_DISMISS` to the notification that can be seen with:
         *
         * `adb shell dumpsys notification --noredact | grep ${packageName}`
         *
         * To disable:
         *
         * `adb shell appops set --uid ${packageName} SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS default`
         */
        @JvmStatic
        fun isCallingAppNotificationNoDismiss(
            context: Context,
            notificationId: Int,
        ): Boolean {
            val notification = findCallingAppNotification(context, notificationId)
            return getNoDismiss(notification)
        }
    }
}
