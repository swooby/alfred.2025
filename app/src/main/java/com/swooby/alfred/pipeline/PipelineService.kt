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
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.notification.FooNotification
import com.smartfoo.android.core.notification.FooNotificationListener
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import com.smartfoo.android.core.texttospeech.FooTextToSpeech.SequenceCallbacks
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.R
import com.swooby.alfred.core.profile.AudioProfile
import com.swooby.alfred.core.profile.AudioProfileGate
import com.swooby.alfred.core.profile.AudioProfileGateReason
import com.swooby.alfred.core.rules.Decision
import com.swooby.alfred.core.rules.DeviceState
import com.swooby.alfred.core.rules.RulesConfig
import com.swooby.alfred.core.summary.Utterance
import com.swooby.alfred.sources.NotificationsSource
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SourceEventTypes
import com.swooby.alfred.sources.SystemSources
import com.swooby.alfred.sources.system.CallStatus
import com.swooby.alfred.sources.system.SystemEvent
import com.swooby.alfred.support.AppShutdownManager
import com.swooby.alfred.support.DebugNotificationController
import com.swooby.alfred.ui.events.EventListActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PipelineService : Service() {
    companion object {
        private val TAG = FooLog.TAG(PipelineService::class.java)

        private const val NOTIFICATION_ID = 42

        private const val REQUEST_SHOW = 100
        private const val REQUEST_PIN = 101
        private const val REQUEST_QUIT = 102
        private const val REQUEST_ENABLE = 103
        private const val REQUEST_DEBUG_SPEECH = 104

        private const val ACTION_REFRESH_NOTIFICATION = "com.swooby.alfred.pipeline.action.REFRESH_NOTIFICATION"
        private const val ACTION_APP_SHUTDOWN = "com.swooby.alfred.pipeline.action.APP_SHUTDOWN"
        private const val ACTION_SPEECH_DISMISS = "com.swooby.alfred.pipeline.action.SPEECH_DISMISS"
        private const val ACTION_SPEECH_DISMISS_ALL = "com.swooby.alfred.pipeline.action.SPEECH_DISMISS_ALL"

        const val ACTION_DEBUG_SPEECH_NOTIFICATION = "com.swooby.alfred.pipeline.action.DEBUG_SPEECH_NOTIFICATION"
        const val ACTION_DEBUG_NOISY_START = "com.swooby.alfred.pipeline.action.DEBUG_NOISY_START"
        const val ACTION_DEBUG_NOISY_STOP = "com.swooby.alfred.pipeline.action.DEBUG_NOISY_STOP"
        const val ACTION_DEBUG_PROGRESS_START = "com.swooby.alfred.pipeline.action.DEBUG_PROGRESS_START"
        const val ACTION_DEBUG_PROGRESS_STOP = "com.swooby.alfred.pipeline.action.DEBUG_PROGRESS_STOP"

        private const val EXTRA_SEQUENCE_ID = "extra_sequence_id"

        private const val PIPELINE_NOTIFICATION_CHANNEL_ID = "alfred_pipeline_status"
        private const val PIPELINE_NOTIFICATION_GROUP_KEY = "alfred_pipeline_group"

        private const val SPEECH_NOTIFICATION_CHANNEL_ID = "alfred_speech_alerts"
        private const val SPEECH_NOTIFICATION_GROUP_KEY = "alfred_speech_group"
        private const val SPEECH_NOTIFICATION_ID_START = 5_000
        private const val SPEECH_SUMMARY_NOTIFICATION_ID = SPEECH_NOTIFICATION_ID_START - 1

        fun start(context: Context) {
            startForegroundService(context)
        }

        fun refreshNotification(context: Context) {
            startForegroundService(context, ACTION_REFRESH_NOTIFICATION)
        }

        fun appShutdown(context: Context) {
            startForegroundService(context, ACTION_APP_SHUTDOWN)
        }

        fun startForegroundService(
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

        private fun intentAppShutdown(context: Context) = intent(context, ACTION_APP_SHUTDOWN)

        fun isOngoingNotificationNoDismiss(context: Context) = FooNotification.isCallingAppNotificationNoDismiss(context, NOTIFICATION_ID)

        fun hasNotificationListenerAccess(context: Context) = FooNotificationListener.hasNotificationListenerAccess(context, NotificationsSource::class.java)

        fun requestNotificationListenerRebind(context: Context) {
            FooNotificationListener.requestNotificationListenerRebind(context, NotificationsSource::class.java)
        }

        fun requestNotificationListenerUnbind(context: Context) {
            FooNotificationListener.requestNotificationListenerUnbind(context, NotificationsSource::class.java)
        }
    }

    private val app by lazy { application as AlfredApp }
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val tts by lazy { FooTextToSpeech.instance }
    private val sysSources by lazy { SystemSources(app) }
    private val debugNotifications by lazy { DebugNotificationController(this, scope) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tz = TimeZone.currentSystemDefault()
    private val speechMutex = Mutex()
    private val pendingUtterances = ArrayDeque<Utterance.Live>()
    private val speechNotificationIdGenerator = AtomicInteger(SPEECH_NOTIFICATION_ID_START)
    private val speechNotificationOrder = AtomicLong(0)
    private val pipelineNotificationChannelInitialized = AtomicBoolean(false)
    private val speechNotificationChannelInitialized = AtomicBoolean(false)
    private val speechNotifications = ConcurrentHashMap<String, SpeechNotificationEntry>()
    private val pendingSpeechStarts = ConcurrentHashMap<String, Boolean>()

    private var cfg = RulesConfig()
    private var systemEventsJob: Job? = null
    private var callStateJob: Job? = null
    private var audioGateNotificationJob: Job? = null
    private var currentCallStatus: CallStatus = CallStatus.UNKNOWN

    private fun CallStatus.blocksSpeech(): Boolean =
        when (this) {
            CallStatus.ACTIVE,
            CallStatus.RINGING,
            -> true
            CallStatus.UNKNOWN,
            CallStatus.IDLE,
            -> false
        }

    private enum class SpeechNotificationState {
        QUEUED,
        SPEAKING,
    }

    private data class SpeechNotificationEntry(
        val notificationId: Int,
        val text: String,
        val createdAt: Long,
        val order: Long,
        @Volatile var state: SpeechNotificationState,
    )

    private data class AudioGateNotificationState(
        val allow: Boolean,
        val reason: AudioProfileGateReason,
        val snapshotId: String?,
    )

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

        tts.start(app)
        sysSources.start()
        app.systemEvents.start()
        app.systemEvents.refreshCallStateObserver()
        currentCallStatus = app.systemEvents.currentCallStatus()
        FooLog.i(TAG, "#PIPELINE initial call state=${currentCallStatus.name.lowercase()}")
        callStateJob =
            scope.launch {
                app.systemEvents.callState.collect { status ->
                    handleCallStateChange(status)
                }
            }
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

        audioGateNotificationJob =
            scope.launch {
                var last: AudioGateNotificationState? = null
                app.audioProfiles.uiState.collect {
                    val gate = app.audioProfiles.evaluateGate()
                    val snapshotId =
                        gate.snapshot
                            ?.profile
                            ?.id
                            ?.value
                    val state =
                        AudioGateNotificationState(
                            allow = gate.allow,
                            reason = gate.reason,
                            snapshotId = snapshotId,
                        )
                    if (state != last) {
                        last = state
                        updateOngoingNotification()
                    }
                }
            }

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
                    app.summarizer.livePhrase(ev)?.let { utter ->
                        handleUtterance(utter)
                    }
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
        app.systemEvents.refreshCallStateObserver()
        when (intent?.action) {
            ACTION_REFRESH_NOTIFICATION -> {
                updateOngoingNotification()
            }
            ACTION_SPEECH_DISMISS -> {
                handleSpeechNotificationDismiss(intent.getStringExtra(EXTRA_SEQUENCE_ID))
            }
            ACTION_SPEECH_DISMISS_ALL -> {
                handleSpeechNotificationDismissAll()
            }
            ACTION_DEBUG_SPEECH_NOTIFICATION -> {
                debugNotifications.postDebugSpeechNotification()
            }
            ACTION_DEBUG_NOISY_START -> {
                debugNotifications.startNoisyNotification()
            }
            ACTION_DEBUG_NOISY_STOP -> {
                debugNotifications.stopNoisyNotification()
            }
            ACTION_DEBUG_PROGRESS_START -> {
                debugNotifications.startProgressNotification()
            }
            ACTION_DEBUG_PROGRESS_STOP -> {
                debugNotifications.stopProgressNotification()
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
        callStateJob?.cancel()
        audioGateNotificationJob?.cancel()
        app.systemEvents.stop()
        app.mediaSource.stop("$TAG.onDestroy")
        tts.stop()
        clearSpeechNotifications()
        debugNotifications.shutdown()
        scope.cancel()
        AppShutdownManager.onPipelineServiceDestroyed(app)
        super.onDestroy()
        FooLog.v(TAG, "-onDestroy()")
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun ensureOngoingNotificationChannel() {
        if (pipelineNotificationChannelInitialized.compareAndSet(false, true)) {
            val channel =
                NotificationChannel(
                    PIPELINE_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.pipeline_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            channel.description = getString(R.string.pipeline_notification_channel_description)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun ensureSpeechNotificationChannel() {
        if (speechNotificationChannelInitialized.compareAndSet(false, true)) {
            val channel =
                NotificationChannel(
                    SPEECH_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.speech_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            channel.description = getString(R.string.speech_notification_channel_description)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        @Suppress("SameParameterValue")
        id: Int,
        notification: Notification,
    ) {
        notificationManager.notify(id, notification)
    }

    private fun postSpeechNotification(
        sequenceId: String,
        text: String,
    ) {
        val entry =
            SpeechNotificationEntry(
                notificationId = speechNotificationIdGenerator.getAndIncrement(),
                text = text,
                createdAt = System.currentTimeMillis(),
                order = speechNotificationOrder.getAndIncrement(),
                state = SpeechNotificationState.QUEUED,
            )
        speechNotifications[sequenceId] = entry
        if (pendingSpeechStarts.remove(sequenceId) == true) {
            onSpeechSequenceStarted(sequenceId)
        } else {
            showSpeechNotification(sequenceId, entry)
        }
    }

    private fun showSpeechNotification(
        sequenceId: String,
        entry: SpeechNotificationEntry,
    ) {
        ensureSpeechNotificationChannel()
        notificationManager.notify(entry.notificationId, buildSpeechNotification(sequenceId, entry))
        updateSpeechGroupSummary()
    }

    private fun buildSpeechNotification(
        sequenceId: String,
        entry: SpeechNotificationEntry,
    ): Notification {
        val notificationId = entry.notificationId
        val dismissIntent =
            PendingIntent.getService(
                this,
                notificationId,
                intent(this, ACTION_SPEECH_DISMISS)
                    .putExtra(EXTRA_SEQUENCE_ID, sequenceId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val sortKey = String.format(Locale.US, "%020d", entry.order)
        val builder =
            NotificationCompat
                .Builder(this, SPEECH_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentText(entry.text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setDeleteIntent(dismissIntent)
                .setGroup(SPEECH_NOTIFICATION_GROUP_KEY)
                .setSortKey(sortKey)
                .setWhen(entry.createdAt)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null) // TODO: Settings option to ding
                .setVibrate(null) // TODO: Settings option to vibrate
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

        val title = StringBuilder()
        if (BuildConfig.DEBUG) {
            title.append("[$notificationId] ")
        }
        when (entry.state) {
            SpeechNotificationState.SPEAKING -> {
                title.append(getString(R.string.speech_notification_title_current))
                val swipeInstruction = getString(R.string.speech_notification_swipe_to_dismiss)
                builder
                    .setContentTitle(title)
                    .setSubText(swipeInstruction)
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText("${entry.text}\n\n$swipeInstruction"),
                    ).addAction(0, getString(R.string.speech_notification_action_dismiss), dismissIntent)
            }
            SpeechNotificationState.QUEUED -> {
                title.append(getString(R.string.speech_notification_title_queued))
                builder
                    .setContentTitle(title)
                    .setSubText(getString(R.string.speech_notification_subtext_queued))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(entry.text))
            }
        }

        return builder.build()
    }

    private fun updateSpeechGroupSummary() {
        if (speechNotifications.isEmpty()) {
            notificationManager.cancel(SPEECH_SUMMARY_NOTIFICATION_ID)
            return
        }
        ensureSpeechNotificationChannel()
        notificationManager.notify(SPEECH_SUMMARY_NOTIFICATION_ID, buildSpeechSummaryNotification())
    }

    private fun buildSpeechSummaryNotification(): Notification {
        val dismissIntent =
            PendingIntent.getService(
                this,
                SPEECH_SUMMARY_NOTIFICATION_ID,
                intent(this, ACTION_SPEECH_DISMISS_ALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val count = speechNotifications.size
        val speaking = speechNotifications.values.any { it.state == SpeechNotificationState.SPEAKING }
        val title =
            if (speaking) {
                getString(R.string.speech_notification_summary_title_active, count)
            } else {
                getString(R.string.speech_notification_summary_title_idle, count)
            }
        val text = getString(R.string.speech_notification_summary_text, count)
        return NotificationCompat
            .Builder(this, SPEECH_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setDeleteIntent(dismissIntent)
            .setGroup(SPEECH_NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(null) // TODO: Settings option to ding
            .setVibrate(null) // TODO: Settings option to vibrate
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(0, getString(R.string.speech_notification_action_dismiss_all), dismissIntent)
            .build()
    }

    private fun handleSpeechNotificationDismiss(sequenceId: String?) {
        if (sequenceId.isNullOrBlank()) {
            return
        }
        val entry = speechNotifications.remove(sequenceId)
        entry?.let {
            notificationManager.cancel(it.notificationId)
            updateSpeechGroupSummary()
        }
        pendingSpeechStarts.remove(sequenceId)
        val canceled = tts.sequenceStop(sequenceId)
        FooLog.i(TAG, "#PIPELINE speech notification dismissed; sequence=$sequenceId canceled=$canceled")
    }

    private fun handleSpeechNotificationDismissAll() {
        val ids = speechNotifications.keys.toList()
        if (ids.isEmpty()) {
            notificationManager.cancel(SPEECH_SUMMARY_NOTIFICATION_ID)
            return
        }
        ids.forEach { id ->
            speechNotifications.remove(id)?.let { notificationManager.cancel(it.notificationId) }
            pendingSpeechStarts.remove(id)
            tts.sequenceStop(id)
        }
        notificationManager.cancel(SPEECH_SUMMARY_NOTIFICATION_ID)
    }

    private fun onSpeechSequenceStarted(sequenceId: String) {
        val entry = speechNotifications[sequenceId]
        //FooLog.e(TAG, "#PIPELINE onSpeechSequenceStarted: speechNotifications[${FooString.quote(sequenceId)}]=$entry")
        if (entry == null) {
            pendingSpeechStarts[sequenceId] = true
            FooLog.v(TAG, "#PIPELINE onSpeechSequenceStarted: pending notification entry for sequence=${sequenceId}")
            return
        }
        pendingSpeechStarts.remove(sequenceId)
        val updates = mutableListOf<Pair<String, SpeechNotificationEntry>>()
        if (entry.state != SpeechNotificationState.SPEAKING) {
            entry.state = SpeechNotificationState.SPEAKING
        }
        updates += sequenceId to entry
        speechNotifications.forEach { (id, other) ->
            if (id != sequenceId && other.state != SpeechNotificationState.QUEUED) {
                other.state = SpeechNotificationState.QUEUED
                updates += id to other
            }
        }
        updates.forEach { (id, updated) -> showSpeechNotification(id, updated) }
        updateSpeechGroupSummary()
    }

    private fun onSpeechSequenceCompleted(
        sequenceId: String,
        neverStarted: Boolean,
        errorCode: Int,
    ) {
        FooLog.d(TAG, "#PIPELINE onSpeechSequenceCompleted(sequenceId=$sequenceId, neverStarted=$neverStarted, errorCode=$errorCode)")
        pendingSpeechStarts.remove(sequenceId)
        val entry = speechNotifications.remove(sequenceId) ?: return
        notificationManager.cancel(entry.notificationId)
        FooLog.v(TAG, "#PIPELINE speech sequence completed; sequence=$sequenceId notificationId=${entry.notificationId}")
        updateSpeechGroupSummary()
    }

    private fun clearSpeechNotifications() {
        val entries = speechNotifications.values.toList()
        speechNotifications.clear()
        pendingSpeechStarts.clear()
        if (entries.isEmpty()) {
            return
        }
        entries.forEach { notificationManager.cancel(it.notificationId) }
        notificationManager.cancel(SPEECH_SUMMARY_NOTIFICATION_ID)
    }

    private suspend fun handleUtterance(utterance: Utterance.Live) {
        val gate = app.audioProfiles.evaluateGate()
        if (!gate.allow) {
            logAudioGateBlocked(gate, "live")
            return
        }
        val queued =
            speechMutex.withLock {
                if (currentCallStatus.blocksSpeech()) {
                    pendingUtterances.addLast(utterance)
                    FooLog.i(TAG, "#PIPELINE call state=${currentCallStatus.name.lowercase()} queued speech; pending=${pendingUtterances.size}")
                    true
                } else {
                    false
                }
            }
        if (queued) {
            return
        }
        speakWithGate(utterance, gate, context = "live")
    }

    private suspend fun handleCallStateChange(status: CallStatus) {
        val queuedToSpeak = mutableListOf<Utterance.Live>()
        var statusChanged = false
        var shouldFlush = false
        speechMutex.withLock {
            val previous = currentCallStatus
            currentCallStatus = status
            statusChanged = previous != status
            if (status.blocksSpeech()) {
                FooLog.i(TAG, "#PIPELINE call state=${status.name.lowercase()} -> speech gated; pending=${pendingUtterances.size}")
                return@withLock
            }
            if (pendingUtterances.isEmpty()) {
                FooLog.v(TAG, "#PIPELINE call state=${status.name.lowercase()} -> no queued speech to flush")
                return@withLock
            }
            queuedToSpeak.addAll(pendingUtterances)
            pendingUtterances.clear()
            shouldFlush = true
        }
        if (statusChanged) {
            updateOngoingNotification()
        }
        if (!shouldFlush) {
            return
        }
        if (queuedToSpeak.isNotEmpty()) {
            FooLog.i(TAG, "#PIPELINE call state=${status.name.lowercase()} -> flushing ${queuedToSpeak.size} queued utterances")
            // TODO: tts.speak probably needs to evaluate the gate immediately before each utterance?
            val gate = app.audioProfiles.evaluateGate()
            queuedToSpeak.forEachIndexed { index, utterance ->
                val shouldSpeak =
                    speechMutex.withLock {
                        if (currentCallStatus.blocksSpeech()) {
                            val remaining = queuedToSpeak.subList(index, queuedToSpeak.size)
                            pendingUtterances.addAll(0, remaining.asReversed())
                            FooLog.i(TAG, "#PIPELINE call state=${currentCallStatus.name.lowercase()} -> speech re-gated; re-queued ${remaining.size} utterances")
                            false
                        } else {
                            true
                        }
                    }
                if (!shouldSpeak) {
                    return
                }
                speakWithGate(utterance, evaluatedGate = gate, context = "flush")
            }
        }
    }

    private fun speakWithGate(
        utterance: Utterance.Live,
        evaluatedGate: AudioProfileGate?,
        context: String,
    ) {
        val gate = evaluatedGate ?: app.audioProfiles.evaluateGate()
        if (!gate.allow) {
            logAudioGateBlocked(gate, context)
            return
        }
        val utteranceText = utterance.text
        val callbacks =
            object : SequenceCallbacks {
                override fun onSequenceStart(sequenceId: String) {
                    //FooLog.d(TAG, "#PIPELINE speakWithGate: onSequenceStart(sequenceId=${FooString.quote(sequenceId)})")
                    onSpeechSequenceStarted(sequenceId)
                }

                override fun onSequenceComplete(
                    sequenceId: String,
                    neverStarted: Boolean,
                    errorCode: Int,
                ) {
                    //FooLog.d(TAG, "#PIPELINE speakWithGate: onSequenceComplete(sequenceId=${FooString.quote(sequenceId)}, neverStarted=$neverStarted, errorCode=$errorCode)")
                    onSpeechSequenceCompleted(sequenceId, neverStarted, errorCode)
                }
            }
        //FooLog.d(TAG, "#PIPELINE speakWithGate: +tts.speak(text=${FooString.quote(utteranceText)}, ...)")
        val sequenceId = tts.speak(utteranceText, callbacks = callbacks)
        //FooLog.d(TAG, "#PIPELINE speakWithGate: -tts.speak(text=${FooString.quote(utteranceText)}, ...)")
        if (sequenceId.isNullOrBlank()) {
            FooLog.w(TAG, "#PIPELINE speakWithGate: unexpected tts.speak returned null sequenceId; ignoring")
            return
        }
        postSpeechNotification(sequenceId, utterance.text)
    }

    private fun logAudioGateBlocked(
        gate: AudioProfileGate,
        context: String,
    ) {
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
        FooLog.i(TAG, "#PIPELINE audio profile blocked speech ($context); reason=${gate.reason}; selected=$selectedId activeDevices=$devices")
    }

    private fun updateOngoingNotification(promptEnable: Boolean? = null) {
        scope.launch {
            val contentTitle = buildNotificationContentTitle()
            val contentText = buildNotificationContentText()
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
                    contentTitle = contentTitle,
                    contentText = contentText,
                    pinEnable = pinEnable,
                    promptEnable = promptEnable,
                ),
            )
        }
    }

    private var contentTitle: String? = null

    private fun buildOngoingNotification(
        contentTitle: String? = null,
        contentText: String? = null,
        pinEnable: Boolean = false,
        promptEnable: Boolean = false,
    ): Notification {
        val contentTitle = contentTitle ?: this.contentTitle ?: getString(R.string.pipeline_notification_initializing)

        this.contentTitle = contentTitle

        ensureOngoingNotificationChannel()

        val pendingIntentShow =
            PendingIntent.getActivity(
                this,
                REQUEST_SHOW,
                EventListActivity.intentShow(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(this, PIPELINE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(contentTitle)
                //.setSubText("TODO")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntentShow)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(PIPELINE_NOTIFICATION_GROUP_KEY)
                .setSortKey("0000_ongoing")
                .setWhen(Long.MAX_VALUE)
                .setShowWhen(false)
        contentText?.let {
            builder.setContentText(it)
        }

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

        // BUG: "Quit" showed up as disabled once! Why?!?!?
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

        if (BuildConfig.DEBUG) {
            val debugIntent =
                PendingIntent.getService(
                    this,
                    REQUEST_DEBUG_SPEECH,
                    intent(this, ACTION_DEBUG_SPEECH_NOTIFICATION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(0, getString(R.string.pipeline_notification_action_debug_speak), debugIntent)
        }

        return builder.build()
    }

    private fun buildNotificationContentTitle(): String {
        if (currentCallStatus.blocksSpeech()) {
            return when (currentCallStatus) {
                CallStatus.ACTIVE -> getString(R.string.pipeline_notification_call_active)
                CallStatus.RINGING -> getString(R.string.pipeline_notification_call_ringing)
                CallStatus.UNKNOWN,
                CallStatus.IDLE,
                -> "Unexpected Call Blocking Speech" // getString(R.string.pipeline_notification_ready_for_events)
            }
        }
        val gate = app.audioProfiles.evaluateGate()
        if (gate.allow) {
            return getString(R.string.pipeline_notification_ready_for_events)
        }
        return when (gate.reason) {
            AudioProfileGateReason.PROFILE_DISABLED -> getString(R.string.pipeline_notification_profile_disabled)
            AudioProfileGateReason.NO_ACTIVE_DEVICES ->
                gate.snapshot?.profile?.let(::describeDevicePrompt)
                    ?: getString(R.string.pipeline_notification_need_device_generic)
            AudioProfileGateReason.UNINITIALIZED -> getString(R.string.pipeline_notification_initializing)
            AudioProfileGateReason.ALLOWED -> getString(R.string.pipeline_notification_ready_for_events)
        }
    }

    private fun buildNotificationContentText(): String? {
        // TODO: Dynamically change this based on state...
        return getString(R.string.pipeline_notification_waiting_for_events)
    }

    private fun describeDevicePrompt(profile: AudioProfile): String =
        when (profile) {
            is AudioProfile.Disabled -> getString(R.string.pipeline_notification_profile_disabled)
            is AudioProfile.AlwaysOn -> getString(R.string.pipeline_notification_ready_for_events)
            is AudioProfile.WiredOnly -> getString(R.string.pipeline_notification_need_wired_headset)
            is AudioProfile.BluetoothAny -> getString(R.string.pipeline_notification_need_bluetooth_any)
            is AudioProfile.BluetoothDevice -> getString(R.string.pipeline_notification_need_bluetooth_device, profile.displayName)
            is AudioProfile.AnyHeadset -> getString(R.string.pipeline_notification_need_any_headset)
        }
}
