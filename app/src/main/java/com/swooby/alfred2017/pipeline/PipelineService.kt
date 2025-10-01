package com.swooby.alfred2017.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.swooby.alfred2017.AlfredApp
import com.swooby.alfred2017.core.rules.*
import com.swooby.alfred2017.tts.Speaker
import com.swooby.alfred2017.tts.SpeakerImpl
import com.swooby.alfred2017.sources.SystemSources
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.collectLatest

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
        app.mediaSource.start()

        scope.launch {
            app.settings.rulesConfigFlow.collectLatest { newCfg -> cfg = newCfg }
        }
        scope.launch {
            app.ingest.out.collect { ev ->
                app.db.events().insert(ev)
                val decision = app.rules.decide(
                    e = ev,
                    state = DeviceState(interactive = ev.userInteractive, audioActive = null, tz = tz),
                    cfg = cfg
                )
                if (decision is Decision.Speak) {
                    app.summarizer.livePhrase(ev)?.let { utter -> speaker.speak(utter.text) }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { app.mediaSource.stop(); speaker.shutdown(); scope.cancel(); super.onDestroy() }
    override fun onBind(p0: Intent?): IBinder? = null

    private fun buildOngoingNotification(): Notification {
        val chId = "alfred_pipeline"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(chId, "Alfred", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, chId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Alfred is listening")
            .setOngoing(true)
            .build()
    }
}