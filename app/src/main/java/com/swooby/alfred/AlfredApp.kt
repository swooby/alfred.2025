package com.swooby.alfred

import android.app.Application
import android.bluetooth.BluetoothManager
import android.media.AudioManager
import com.swooby.alfred.core.ingest.EventIngest
import com.swooby.alfred.core.ingest.EventIngestImpl
import com.swooby.alfred.core.profile.AndroidAudioProfilePermissionChecker
import com.swooby.alfred.core.profile.AndroidAudioProfileStore
import com.swooby.alfred.core.profile.AudioProfileController
import com.swooby.alfred.core.rules.RulesEngine
import com.swooby.alfred.core.rules.RulesEngineImpl
import com.swooby.alfred.core.summary.SummaryGenerator
import com.swooby.alfred.core.summary.TemplatedSummaryGenerator
import com.swooby.alfred.data.AlfredDb
import com.swooby.alfred.settings.SettingsRepository
import com.swooby.alfred.sources.MediaSessionsSource
import com.smartfoo.android.core.logging.FooLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicBoolean

class AlfredApp : Application() {
    companion object {
        private val TAG = FooLog.TAG(AlfredApp::class.java)
    }

    lateinit var appScope: CoroutineScope
    lateinit var db: AlfredDb
    lateinit var ingest: EventIngest
    lateinit var rules: RulesEngine
    lateinit var summarizer: SummaryGenerator
    lateinit var settings: SettingsRepository
    lateinit var mediaSource: MediaSessionsSource
    lateinit var audioProfiles: AudioProfileController
    private val isShutdown = AtomicBoolean(false)

    override fun onCreate() {
        FooLog.v(TAG, "+onCreate()")
        super.onCreate()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = AlfredDb.open(this)
        ingest = EventIngestImpl(appScope)
        rules = RulesEngineImpl()
        summarizer = TemplatedSummaryGenerator()
        settings = SettingsRepository(this)
        mediaSource = MediaSessionsSource(this, this)
        val audioManager = getSystemService(AudioManager::class.java)
            ?: throw IllegalStateException("AudioManager service unavailable")
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        audioProfiles = AudioProfileController(
            context = this,
            audioManager = audioManager,
            bluetoothAdapter = bluetoothAdapter,
            profileStore = AndroidAudioProfileStore(this),
            permissionChecker = AndroidAudioProfilePermissionChecker(this),
            externalScope = appScope,
            ioDispatcher = Dispatchers.IO
        )
        FooLog.v(TAG, "-onCreate()")
    }

    fun shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            FooLog.d(TAG, "shutdown: already executed")
            return
        }
        FooLog.v(TAG, "+shutdown()")
        if (this::mediaSource.isInitialized) {
            try {
                mediaSource.stop("AlfredApp.shutdown")
            } catch (t: Throwable) {
                FooLog.w(TAG, "shutdown: mediaSource.stop failed", t)
            }
        }
        if (this::audioProfiles.isInitialized) {
            runCatching { audioProfiles.shutdown() }
                .onFailure { FooLog.w(TAG, "shutdown: audioProfiles.shutdown failed", it) }
        }
        if (this::appScope.isInitialized) {
            appScope.cancel()
        }
        if (this::db.isInitialized) {
            try {
                db.close()
            } catch (t: Throwable) {
                FooLog.w(TAG, "shutdown: db.close failed", t)
            }
        }
        FooLog.v(TAG, "-shutdown()")
    }
}
