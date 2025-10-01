package com.swooby.alfred.sources

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.data.Sensitivity
import com.swooby.alfred.util.Ulids
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotifSvc : NotificationListenerService() {
    private val app get() = application as AlfredApp

    override fun onListenerConnected() {
        // Now we have access; start the media source if PipelineService is running.
        val app = application as AlfredApp
        try {
            app.mediaSource.start()
        } catch (_: SecurityException) {
            /* ignore */
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val pkg = sbn.packageName
        val title = n.extras?.getCharSequence("android.title")?.toString()
        val text = n.extras?.getCharSequence("android.text")?.toString()

        val now = Clock.System.now()
        val ev = EventEntity(
            eventId = Ulids.newUlid(),
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = "notif_listener",
            eventType = "notif.post",
            eventCategory = "notif",
            eventAction = "post",
            subjectEntity = "notification",
            subjectEntityId = "$pkg:${sbn.id}",
            tsStart = now,
            api = "NotificationListener",
            sensitivity = if (!text.isNullOrBlank()) Sensitivity.CONTENT else Sensitivity.METADATA,
            attributes = buildJsonObject {
                put("title", title ?: "")
                put("text", text ?: "")
                put("channel_id", n.channelId ?: "")
            }
        )
        app.ingest.submit(
            RawEvent(
                ev,
                fingerprint = "$pkg:${sbn.id}:${title}:${text}",
                coalesceKey = null
            )
        )
    }
}