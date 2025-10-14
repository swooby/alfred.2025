package com.swooby.alfred.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.notification.FooNotificationListener
import com.swooby.alfred.pipeline.PipelineService
import com.swooby.alfred.sources.NotificationsSource
import com.swooby.alfred.ui.events.EventListActivity
import com.swooby.alfred.ui.permissions.PermissionsActivity
import com.swooby.alfred.util.isNotificationPermissionGranted

/**
 * Lightweight entry point that evaluates runtime permissions and forwards the
 * user to the appropriate experience. This keeps the launcher intent fast and
 * avoids inflating Compose just to redirect into the main UI.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasEssentials =
            isNotificationPermissionGranted(this) &&
                FooNotificationListener.hasNotificationListenerAccess(this, NotificationsSource::class.java)

        val nextIntent =
            if (hasEssentials) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, PipelineService::class.java),
                )
                Intent(this, EventListActivity::class.java)
            } else {
                Intent(this, PermissionsActivity::class.java)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        startActivity(nextIntent)
        finish()
    }
}
