package com.swooby.alfred.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.swooby.alfred.sources.SourceEventTypes
import com.swooby.alfred.sources.SystemSources
import com.swooby.alfred.sources.system.SystemEvent
import com.swooby.alfred.support.AppShutdownManager
import com.swooby.alfred.ui.events.EventListActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

        private const val REQUEST_SHOW = 100
        private const val REQUEST_PIN = 101
        private const val REQUEST_QUIT = 102
        private const val REQUEST_ENABLE = 103

        private const val ACTION_REFRESH_NOTIFICATION = "com.swooby.alfred.pipeline.action.REFRESH_NOTIFICATION"
        private const val ACTION_APP_SHUTDOWN = "com.swooby.alfred.pipeline.action.APP_SHUTDOWN"

        fun start(context: Context) {
            startForegroundService(context)
        }

        fun refreshNotification(context: Context) {
            startForegroundService(context, ACTION_REFRESH_NOTIFICATION)
        }

        fun appShutdown(context: Context) {
            startForegroundService(context, ACTION_APP_SHUTDOWN)
        }

        private fun startForegroundService(
            context: Context,
            action: String? = null,
        ) {
            context.startForegroundService(intent(context, action))
        }

        private fun intent(
            context: Context,
            action: String? = null,
        ): Intent =
            Intent(context, PipelineService::class.java)
                .setAction(action)

        private fun intentAppShutdown(context: Context): Intent = intent(context, ACTION_APP_SHUTDOWN)

        fun isOngoingNotificationNoDismiss(context: Context): Boolean = FooNotification.isCallingAppNotificationNoDismiss(context, NOTIFICATION_ID)

        fun hasNotificationListenerAccess(context: Context): Boolean = FooNotificationListener.hasNotificationListenerAccess(context, NotificationsSource::class.java)

        fun requestNotificationListenerRebind(context: Context) {
            FooNotificationListener.requestNotificationListenerRebind(context, NotificationsSource::class.java)
        }

        fun requestNotificationListenerUnbind(context: Context) {
            FooNotificationListener.requestNotificationListenerUnbind(context, NotificationsSource::class.java)
        }
    }

    private val app by lazy { application as AlfredApp }
    private lateinit var tts: FooTextToSpeech
    private lateinit var sysSources: SystemSources
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tz = TimeZone.currentSystemDefault()
    private var cfg = RulesConfig()
    private var systemEventsJob: Job? = null

    override fun onCreate() {
        FooLog.v(TAG, "+onCreate()")
        super.onCreate()

        /**
         * From [https://developer.android.com/about/versions/14/changes/fgs-types-required#include-fgs-type-runtime](https://developer.android.com/about/versions/14/changes/fgs-types-required#include-fgs-type-runtime):
         * > **If the foreground service type is not specified in the call,
         * > the type defaults to the values defined in the manifest.**
         * > If you didn't specify the service type in the manifest, the system throws
         * > [MissingForegroundServiceTypeException](https://developer.android.com/reference/android/app/MissingForegroundServiceTypeException).
         *
         * ie: [android.app.Service.startForeground]`(int id, Notification notification)` defaults `foregroundServiceType` to [ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST]
         *
         * NOTE: **Only do this if only one foreground service type is defined!**
         *
         * If the manifest defines multiple types then all requirements need to be met before
         * `FOREGROUND_SERVICE_TYPE_MANIFEST` will succeed:
         * > In cases where a foreground service is started with multiple types, then the
         * > foreground service must adhere to the
         * > [platform enforcement requirements](https://developer.android.com/guide/components/foreground-services#runtime-permissions)
         * > of all types.
         */
        val foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        startForeground(NOTIFICATION_ID, buildOngoingNotification(), foregroundServiceType)

        val hasNotificationListenerAccess = AppShutdownManager.onPipelineServiceStarted(this)

        tts = FooTextToSpeech.instance.start(app)
        sysSources = SystemSources(app)
        sysSources.start()
        app.systemEvents.start()
        systemEventsJob =
            scope.launch {
                app.systemEvents.events.collect { event: SystemEvent ->
                    val raw = app.systemEventMapper.map(event)
                    app.ingest.submit(raw)
                }
            }

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
                if (component == SourceComponentIds.NOTIFICATION_SOURCE &&
                    ev.eventType == SourceEventTypes.NOTIFICATION_POST &&
                    ev.subjectEntityId != null
                ) {
                    val alreadyStored =
                        app.db.events().existsNotification(
                            userId = ev.userId,
                            component = component,
                            subjectEntityId = ev.subjectEntityId,
                            tsStart = ev.tsStart,
                        )
                    if (alreadyStored) {
                        FooLog.d(TAG, "#PIPELINE skip duplicate ${SourceComponentIds.NOTIFICATION_SOURCE} event subjectId=${ev.subjectEntityId} tsStart=${ev.tsStart}")
                        return@collect
                    }
                }

                app.db.events().insert(ev)
                val decision =
                    app.rules.decide(
                        e = ev,
                        state =
                            DeviceState(
                                interactive = ev.userInteractive,
                                audioActive = null,
                                tz = tz,
                            ),
                        cfg = cfg,
                    )
                if (decision is Decision.Speak) {
                    val gate = app.audioProfiles.evaluateGate()
                    if (!gate.allow) {
                        val selectedId =
                            gate.snapshot
                                ?.profile
                                ?.id
                                ?.value
                                ?: app.audioProfiles.uiState.value.selectedProfileId
                                    ?.value
                                ?: "none"
                        val devices =
                            gate.snapshot
                                ?.activeDevices
                                ?.joinToString(prefix = "[", postfix = "]") { it.safeDisplayName }
                                ?: "[]"
                        FooLog.i(TAG, "#PIPELINE audio profile blocked speech; reason=${gate.reason}; selected=$selectedId activeDevices=$devices")
                        return@collect
                    }
                    app.summarizer.livePhrase(ev)?.let { utter -> tts.speak(utter.text) }
                }
            }
        }
        FooLog.v(TAG, "-onCreate()")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        FooLog.v(TAG, "onStartCommand(intent=${FooPlatformUtils.toString(intent)}, flags=$flags, startId=$startId)")
        when (intent?.action) {
            ACTION_REFRESH_NOTIFICATION -> {
                updateOngoingNotification()
            }
            ACTION_APP_SHUTDOWN -> {
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
        @Suppress("MemberExtensionConflict")
        systemEventsJob?.cancel()
        app.systemEvents.stop()
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
        notification: Notification,
    ) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    private fun updateOngoingNotification(promptEnable: Boolean? = null) {
        scope.launch {
            var pinEnable = false
            val isPersistentNotificationActionIgnored = app.settings.persistentNotificationActionIgnoredFlow.first()
            if (!isPersistentNotificationActionIgnored &&
                !isOngoingNotificationNoDismiss(this@PipelineService)
            ) {
                pinEnable = true
            }

            val promptEnable = promptEnable ?: !hasNotificationListenerAccess(this@PipelineService)

            updateNotification(
                NOTIFICATION_ID,
                buildOngoingNotification(
                    pinEnable = pinEnable,
                    promptEnable = promptEnable,
                ),
            )
        }
    }

    private fun buildOngoingNotification(
        pinEnable: Boolean = false,
        promptEnable: Boolean = false,
    ): Notification {
        val chId = "alfred_pipeline"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                chId,
                getString(R.string.pipeline_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val pendingIntentShow =
            PendingIntent.getActivity(
                this,
                REQUEST_SHOW,
                EventListActivity.intentShow(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(this, chId)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(getString(R.string.pipeline_notification_title))
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntentShow)

        if (pinEnable) {
            val pendingIntentPin =
                PendingIntent.getActivity(
                    this,
                    REQUEST_PIN,
                    EventListActivity.intentPin(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder
                //.setContentTitle("...")
                .addAction(R.drawable.ic_warning, getString(R.string.alfred_notification_action_persistent), pendingIntentPin)
        }

        val pendingIntentAppShutdown =
            PendingIntent.getService(
                this,
                REQUEST_QUIT,
                intentAppShutdown(this),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        builder
            //.setContentText("...")
            .addAction(0, getString(R.string.pipeline_notification_action_quit), pendingIntentAppShutdown)

        if (promptEnable) {
            val intentEnable = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            val pendingIntentEnable =
                PendingIntent.getActivity(
                    this,
                    REQUEST_ENABLE,
                    intentEnable,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder
                //.setContentText(getString(R.string.pipeline_notification_permission))
                .addAction(0, getString(R.string.pipeline_notification_action_enable), pendingIntentEnable)
        }

        return builder.build()
    }
}
