package com.swooby.alfred.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.rules.Decision
import com.swooby.alfred.core.rules.DeviceState
import com.swooby.alfred.core.rules.RulesConfig
import com.swooby.alfred.sources.NotifSvc
import com.swooby.alfred.sources.SystemSources
import com.swooby.alfred.tts.Speaker
import com.swooby.alfred.tts.SpeakerImpl
import com.swooby.alfred.util.hasNotificationListenerAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class PipelineService : Service() {
    private val app by lazy { application as AlfredApp }
    private lateinit var speaker: Speaker
    private lateinit var sysSources: SystemSources
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tz = TimeZone.currentSystemDefault()
    private var cfg = RulesConfig()

    override fun onCreate() {
        super.onCreate()
        startForeground(42, buildOngoingNotification())

        speaker = SpeakerImpl(this)
        sysSources = SystemSources(this, app)
        sysSources.start()

        // 🔒 Only start media session source if we have notification-listener access
        val hasAccess = hasNotificationListenerAccess(this, NotifSvc::class.java)
        if (hasAccess) {
            try {
                app.mediaSource.start()
            } catch (se: SecurityException) {
                // Access might have been revoked between check and start; keep running without media source
            }
        } else {
            // Update foreground notification to include an action to enable access
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(42, buildOngoingNotification(promptEnable = true))
        }

        // Live settings → cfg
        scope.launch {
            app.settings.rulesConfigFlow.collectLatest { cfg = it }
        }

        // Ingest → rules → summary → TTS + Room
        scope.launch {
            app.ingest.out.collect { ev ->
                app.db.events().insert(ev)
                val decision = app.rules.decide(
                    e = ev,
                    state = DeviceState(
                        interactive = ev.userInteractive,
                        audioActive = null,
                        tz = tz
                    ),
                    cfg = cfg
                )
                if (decision is Decision.Speak) {
                    app.summarizer.livePhrase(ev)?.let { utter -> speaker.speak(utter.text) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() {
        app.mediaSource.stop(); speaker.shutdown(); scope.cancel(); super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun buildOngoingNotification(promptEnable: Boolean = false): Notification {
        val chId = "alfred_pipeline"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(chId, "Alfred", NotificationManager.IMPORTANCE_LOW)
        )

        val builder = NotificationCompat.Builder(this, chId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Alfred is listening")
            .setOngoing(true)

        if (promptEnable) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setContentText("Enable Notification access for media tracking")
                .addAction(0, "Enable", pi)
        }

        return builder.build()
    }
}