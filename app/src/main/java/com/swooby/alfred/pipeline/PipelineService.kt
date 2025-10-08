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
import com.swooby.alfred.R
import com.swooby.alfred.core.rules.Decision
import com.swooby.alfred.core.rules.DeviceState
import com.swooby.alfred.core.rules.RulesConfig
import com.swooby.alfred.sources.NotificationsSource
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SystemSources
import com.swooby.alfred.support.AppShutdownManager
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.notification.FooNotificationListener
import com.smartfoo.android.core.platform.FooPlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class PipelineService : Service() {
    companion object {
        private val TAG = FooLog.TAG(PipelineService::class.java)
        private const val NOTIFICATION_ID = 42
        const val ACTION_QUIT: String = "com.swooby.alfred.pipeline.action.QUIT"

        fun quitIntent(context: Context): Intent =
            Intent(context, PipelineService::class.java).setAction(ACTION_QUIT)
    }

    private val app by lazy { application as AlfredApp }
    private lateinit var tts: FooTextToSpeech
    private lateinit var sysSources: SystemSources
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tz = TimeZone.currentSystemDefault()
    private var cfg = RulesConfig()

    override fun onCreate() {
        FooLog.v(TAG, "+onCreate()")
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildOngoingNotification())
        AppShutdownManager.onPipelineServiceStarted(this)

        tts = FooTextToSpeech.instance.start(app)
        sysSources = SystemSources(this, app)
        sysSources.start()

        // 🔒 Only start media session source if we have notification-listener access
        val hasAccess = FooNotificationListener.hasNotificationListenerAccess(this, NotificationsSource::class.java)
        if (hasAccess) {
            try {
                FooNotificationListener.requestNotificationListenerRebind(this, NotificationsSource::class.java)
                app.mediaSource.start("$TAG.onCreate")
            } catch (se: SecurityException) {
                // Access might have been revoked between check and start; keep running without media source
                FooLog.w(TAG, "onCreate: SecurityException", se)
            }
        } else {
            // Update foreground notification to include an action to enable access
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildOngoingNotification(promptEnable = true))
        }

        // Live settings → cfg
        scope.launch {
            app.settings.rulesConfigFlow.collectLatest { cfg = it }
        }

        // Ingest → rules → summary → TTS + Room
        scope.launch {
            app.ingest.out.collect { ev ->
                val component = ev.component
                if (component == SourceComponentIds.NOTIFICATION_SOURCE && ev.subjectEntityId != null) {
                    val alreadyStored = app.db.events().existsNotification(
                        userId = ev.userId,
                        component = component,
                        subjectEntityId = ev.subjectEntityId,
                        tsStart = ev.tsStart
                    )
                    if (alreadyStored) {
                        FooLog.d(TAG, "#PIPELINE skip duplicate ${SourceComponentIds.NOTIFICATION_SOURCE} event subjectId=${ev.subjectEntityId} tsStart=${ev.tsStart}")
                        return@collect
                    }
                }

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
                    app.summarizer.livePhrase(ev)?.let { utter -> tts.speak(utter.text) }
                }
            }
        }
        FooLog.v(TAG, "-onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FooLog.v(TAG, "onStartCommand(intent=${FooPlatformUtils.toString(intent)}, flags=$flags, startId=$startId)")
        if (intent?.action == ACTION_QUIT) {
            AppShutdownManager.markQuitRequested(this)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        FooLog.v(TAG, "+onDestroy()")
        stopForeground(STOP_FOREGROUND_REMOVE)
        runCatching { sysSources.stop() }
            .onFailure { FooLog.w(TAG, "onDestroy: sysSources.stop failed", it) }
        app.mediaSource.stop("PipelineService.onCreate")
        tts.stop()
        scope.cancel()
        AppShutdownManager.onPipelineServiceDestroyed(app)
        super.onDestroy()
        FooLog.v(TAG, "-onDestroy()")
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun buildOngoingNotification(promptEnable: Boolean = false): Notification {
        val chId = "alfred_pipeline"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                chId,
                getString(R.string.pipeline_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val builder = NotificationCompat.Builder(this, chId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(getString(R.string.pipeline_notification_title))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        val quitPendingIntent = PendingIntent.getService(
            this,
            100,
            quitIntent(this),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            0,
            getString(R.string.pipeline_notification_action_quit),
            quitPendingIntent
        )

        if (promptEnable) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setContentText(getString(R.string.pipeline_notification_permission))
                .addAction(0, getString(R.string.pipeline_notification_action_enable), pi)
        }

        return builder.build()
    }
}
