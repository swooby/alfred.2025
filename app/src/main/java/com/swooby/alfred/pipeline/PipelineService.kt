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
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.notification.FooNotification
import com.smartfoo.android.core.notification.FooNotificationListener
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.R
import com.swooby.alfred.core.rules.Decision
import com.swooby.alfred.core.rules.DeviceState
import com.swooby.alfred.core.rules.RulesConfig
import com.swooby.alfred.sources.NotificationsSource
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SystemSources
import com.swooby.alfred.support.AppShutdownManager
import com.swooby.alfred.ui.events.EventListActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone


class PipelineService : Service() {
    companion object {
        private val TAG = FooLog.TAG(PipelineService::class.java)
        private const val NOTIFICATION_ID = 42
        private const val REQUEST_QUIT = 100
        private const val REQUEST_PIN = 101

        private const val ACTION_REFRESH_NOTIFICATION = "com.swooby.alfred.pipeline.action.REFRESH_NOTIFICATION"
        private const val ACTION_QUIT = "com.swooby.alfred.pipeline.action.QUIT"

        fun quitIntent(context: Context): Intent =
            Intent(context, PipelineService::class.java)
                .setAction(ACTION_QUIT)

        fun isOngoingNotificationNoDismiss(context: Context): Boolean {
            return FooNotification.isCallingAppNotificationNoDismiss(context, NOTIFICATION_ID)
        }

        fun hasNotificationListenerAccess(context: Context): Boolean {
            return FooNotificationListener.hasNotificationListenerAccess(context, NotificationsSource::class.java)
        }

        fun requestNotificationListenerRebind(context: Context) {
            FooNotificationListener.requestNotificationListenerRebind(context, NotificationsSource::class.java)
        }

        fun requestNotificationListenerUnbind(context: Context) {
            FooNotificationListener.requestNotificationListenerUnbind(context, NotificationsSource::class.java)
        }

        fun refreshNotification(context: Context) {
            context.startService(Intent(context, PipelineService::class.java)
                .setAction(ACTION_REFRESH_NOTIFICATION))
        }
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

        val hasNotificationListenerAccess = AppShutdownManager.onPipelineServiceStarted(this)

        tts = FooTextToSpeech.instance.start(app)
        sysSources = SystemSources(this, app)
        sysSources.start()

        // 🔒 Only start media session source if we have notification-listener access
        if (hasNotificationListenerAccess) {
            try {
                requestNotificationListenerRebind(this)
                app.mediaSource.start("$TAG.onCreate")
            } catch (se: SecurityException) {
                // Access might have been revoked between check and start; keep running without media source
                FooLog.w(TAG, "onCreate: SecurityException", se)
            }
        }

        updateOngoingNotification(promptEnable = !hasNotificationListenerAccess)

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
                    val gate = app.audioProfiles.evaluateGate()
                    if (!gate.allow) {
                        val selectedId = gate.snapshot?.profile?.id?.value
                            ?: app.audioProfiles.uiState.value.selectedProfileId?.value
                            ?: "none"
                        val devices = gate.snapshot?.activeDevices
                            ?.joinToString(prefix = "[", postfix = "]") { it.safeDisplayName }
                            ?: "[]"
                        FooLog.d(TAG, "#PIPELINE audio profile blocked speech; reason=${gate.reason}; selected=$selectedId activeDevices=$devices")
                        return@collect
                    }
                    app.summarizer.livePhrase(ev)?.let { utter -> tts.speak(utter.text) }
                }
            }
        }
        FooLog.v(TAG, "-onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FooLog.v(TAG, "onStartCommand(intent=${FooPlatformUtils.toString(intent)}, flags=$flags, startId=$startId)")
        when (intent?.action) {
            ACTION_REFRESH_NOTIFICATION -> {
                updateOngoingNotification()
            }
            ACTION_QUIT -> {
                AppShutdownManager.markQuitRequested(this)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        FooLog.v(TAG, "+onDestroy()")
        stopForeground(STOP_FOREGROUND_REMOVE)
        runCatching { sysSources.stop() }
            .onFailure { FooLog.w(TAG, "onDestroy: sysSources.stop failed", it) }
        app.mediaSource.stop("$TAG.onDestroy")
        tts.stop()
        scope.cancel()
        AppShutdownManager.onPipelineServiceDestroyed(app)
        super.onDestroy()
        FooLog.v(TAG, "-onDestroy()")
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun updateNotification(
        @Suppress("SameParameterValue")
        id: Int,
        notification: Notification
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    private fun updateOngoingNotification(promptEnable: Boolean? = null) {
        scope.launch {
            var pinEnable = false
            val isPersistentNotificationActionIgnored = app.settings.persistentNotificationActionIgnoredFlow.first()
            if (!isPersistentNotificationActionIgnored &&
                !isOngoingNotificationNoDismiss(this@PipelineService)) {
                pinEnable = true
            }

            val promptEnable = promptEnable ?: !hasNotificationListenerAccess(this@PipelineService)

            updateNotification(NOTIFICATION_ID,
                buildOngoingNotification(
                    pinEnable = pinEnable,
                    promptEnable = promptEnable
                )
            )
        }
    }

    private fun buildOngoingNotification(
        pinEnable: Boolean = false,
        promptEnable: Boolean = false
    ): Notification {
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

        if (pinEnable) {
            val pinPendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_PIN,
                EventListActivity.pinIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_warning,
                getString(R.string.alfred_notification_action_persistent),
                pinPendingIntent
            )
        }

        val quitPendingIntent = PendingIntent.getService(
            this,
            REQUEST_QUIT,
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
