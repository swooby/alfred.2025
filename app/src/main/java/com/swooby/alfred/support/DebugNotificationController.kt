package com.swooby.alfred.support

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.R
import com.swooby.alfred.pipeline.PipelineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DebugNotificationController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private val TAG = FooLog.TAG(DebugNotificationController::class.java)

        const val EXTRA_DEBUG_SPEECH_NOTIFICATION = "com.swooby.alfred.debug.SPEECH_NOTIFICATION"

        private const val DEBUG_NOTIFICATION_CHANNEL_ID = "com.swooby.alfred.debug.notifications"
        private const val DEBUG_NOTIFICATION_GROUP_KEY = "alfred_debug_group"
        private const val DEBUG_NOISY_NOTIFICATION_ID = 0xD09
        private const val DEBUG_PROGRESS_NOTIFICATION_ID = 0xD0A
        private const val DEBUG_NOISY_PERIOD_MS = 10_000L
        private const val DEBUG_PROGRESS_RESET_DELAY_MS = 5_000L
        private const val DEBUG_PROGRESS_STEPS = 100
        private const val DEBUG_PROGRESS_TOTAL_MS = 60_000L
        private const val DEBUG_PROGRESS_STEP_DELAY_MS = DEBUG_PROGRESS_TOTAL_MS / DEBUG_PROGRESS_STEPS
        private const val DEBUG_SORT_KEY_NOISY = "1000_noisy"
        private const val DEBUG_SORT_KEY_PROGRESS_COMPLETE = "2000_progress_zzz"

        private val _debugNoisyNotificationActiveFlow = MutableStateFlow(false)
        val debugNoisyNotificationActiveFlow: StateFlow<Boolean> = _debugNoisyNotificationActiveFlow.asStateFlow()

        private val _debugProgressNotificationActiveFlow = MutableStateFlow(false)
        val debugProgressNotificationActiveFlow: StateFlow<Boolean> = _debugProgressNotificationActiveFlow.asStateFlow()

        fun startDebugNoisyNotification(context: Context) {
            PipelineService.startForegroundService(context, PipelineService.ACTION_DEBUG_NOISY_START)
        }

        fun stopDebugNoisyNotification(context: Context) {
            PipelineService.startForegroundService(context, PipelineService.ACTION_DEBUG_NOISY_STOP)
        }

        fun startDebugProgressNotification(context: Context) {
            PipelineService.startForegroundService(context, PipelineService.ACTION_DEBUG_PROGRESS_START)
        }

        fun stopDebugProgressNotification(context: Context) {
            PipelineService.startForegroundService(context, PipelineService.ACTION_DEBUG_PROGRESS_STOP)
        }

        fun startDebugSpeechNotification(context: Context) {
            PipelineService.startForegroundService(context, PipelineService.ACTION_DEBUG_SPEECH_NOTIFICATION)
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var noisyJob: Job? = null
    private var progressJob: Job? = null
    private val debugNotificationChannelInitialized = AtomicBoolean(false)
    private val debugSpeechNotificationOrder = AtomicLong(0)

    fun startNoisyNotification() {
        if (isNoisyActive()) {
            return
        }
        ensureDebugNotificationChannel()
        if (!canPostNotifications()) {
            FooLog.w(TAG, "#DEBUG_NOTIFS noisy: POST_NOTIFICATIONS not granted")
            _debugNoisyNotificationActiveFlow.value = false
            return
        }
        val title = context.getString(R.string.debug_notification_noisy_title)
        val text = context.getString(R.string.debug_notification_noisy_text)
        val job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val notification =
                        baseBuilder(DEBUG_SORT_KEY_NOISY)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                            .build()
                    notifySafely(DEBUG_NOISY_NOTIFICATION_ID, notification)
                    delay(DEBUG_NOISY_PERIOD_MS)
                }
            }
        job.invokeOnCompletion {
            if (noisyJob == job) {
                noisyJob = null
                _debugNoisyNotificationActiveFlow.value = false
            }
        }
        noisyJob = job
        _debugNoisyNotificationActiveFlow.value = true
    }

    fun stopNoisyNotification() {
        noisyJob?.cancel()
        noisyJob = null
        notificationManager.cancel(DEBUG_NOISY_NOTIFICATION_ID)
        _debugNoisyNotificationActiveFlow.value = false
    }

    fun startProgressNotification() {
        if (isProgressActive()) {
            return
        }
        ensureDebugNotificationChannel()
        if (!canPostNotifications()) {
            FooLog.w(TAG, "#DEBUG_NOTIFS progress: POST_NOTIFICATIONS not granted")
            _debugProgressNotificationActiveFlow.value = false
            return
        }
        val title = context.getString(R.string.debug_notification_progress_title)
        val job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    for (progress in 0..DEBUG_PROGRESS_STEPS) {
                        if (!isActive) {
                            break
                        }
                        val text =
                            context.getString(
                                R.string.debug_notification_progress_text,
                                progress,
                            )
                        val sortKey = String.format(Locale.US, "2000_progress_%03d", progress)
                        val notification =
                            baseBuilder(sortKey)
                                .setContentTitle(title)
                                .setContentText(text)
                                .setOnlyAlertOnce(true)
                                .setOngoing(true)
                                .setProgress(DEBUG_PROGRESS_STEPS, progress, false)
                                .build()
                        notifySafely(DEBUG_PROGRESS_NOTIFICATION_ID, notification)
                        delay(DEBUG_PROGRESS_STEP_DELAY_MS)
                    }
                    if (!isActive) {
                        break
                    }
                    val completionNotification =
                        baseBuilder(DEBUG_SORT_KEY_PROGRESS_COMPLETE)
                            .setContentTitle(title)
                            .setContentText(context.getString(R.string.debug_notification_progress_complete))
                            .setOnlyAlertOnce(true)
                            .setOngoing(false)
                            .setProgress(0, 0, false)
                            .build()
                    notifySafely(DEBUG_PROGRESS_NOTIFICATION_ID, completionNotification)
                    delay(DEBUG_PROGRESS_RESET_DELAY_MS)
                }
            }
        job.invokeOnCompletion {
            if (progressJob == job) {
                progressJob = null
                _debugProgressNotificationActiveFlow.value = false
            }
        }
        progressJob = job
        _debugProgressNotificationActiveFlow.value = true
    }

    fun stopProgressNotification() {
        progressJob?.cancel()
        progressJob = null
        notificationManager.cancel(DEBUG_PROGRESS_NOTIFICATION_ID)
        _debugProgressNotificationActiveFlow.value = false
    }

    fun postDebugSpeechNotification() {
        ensureDebugNotificationChannel()
        if (!canPostNotifications()) {
            FooLog.w(TAG, "#DEBUG_NOTIFS speech: POST_NOTIFICATIONS not granted")
            return
        }
        val notificationId = (SystemClock.uptimeMillis() and 0x7FFFFFFF).toInt()
        val body = context.getString(R.string.debug_speech_notification_body)
        val extras = Bundle().apply { putBoolean(EXTRA_DEBUG_SPEECH_NOTIFICATION, true) }
        val sortKey = String.format(Locale.US, "5000_speech_%020d", debugSpeechNotificationOrder.getAndIncrement())
        val notification =
            NotificationCompat
                .Builder(context, DEBUG_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(context.getString(R.string.debug_speech_notification_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(DEBUG_NOTIFICATION_GROUP_KEY)
                .setSortKey(sortKey)
                .addExtras(extras)
                .build()
        notifySafely(notificationId, notification)
    }

    fun shutdown() {
        stopNoisyNotification()
        stopProgressNotification()
    }

    private fun isNoisyActive(): Boolean =
        (
            @Suppress("MemberExtensionConflict")
            noisyJob?.isActive
                == true
        )

    private fun isProgressActive(): Boolean =
        (
            @Suppress("MemberExtensionConflict")
            progressJob?.isActive
                == true
        )

    private fun baseBuilder(
        sortKey: String,
        whenMillis: Long = System.currentTimeMillis(),
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, DEBUG_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(DEBUG_NOTIFICATION_GROUP_KEY)
            .setSortKey(sortKey)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .addExtras(Bundle().apply { putBoolean(EXTRA_DEBUG_SPEECH_NOTIFICATION, true) })

    private fun ensureDebugNotificationChannel() {
        if (debugNotificationChannelInitialized.compareAndSet(false, true)) {
            val channel =
                NotificationChannel(
                    DEBUG_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.debug_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.debug_notification_channel_description)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun notifySafely(
        id: Int,
        notification: Notification,
    ) {
        if (canPostNotifications()) {
            notificationManager.notify(id, notification)
        } else {
            FooLog.w(TAG, "#DEBUG_NOTIFS notifySafely: POST_NOTIFICATIONS missing; skipping id=$id")
        }
    }

    private fun canPostNotifications(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
