package com.swooby.alfred

import android.app.Application
import com.swooby.alfred.core.ingest.EventIngest
import com.swooby.alfred.core.ingest.EventIngestImpl
import com.swooby.alfred.core.rules.RulesEngine
import com.swooby.alfred.core.rules.RulesEngineImpl
import com.swooby.alfred.core.summary.SummaryGenerator
import com.swooby.alfred.core.summary.TemplatedSummaryGenerator
import com.swooby.alfred.data.AlfredDb
import com.swooby.alfred.settings.SettingsRepository
import com.swooby.alfred.sources.MediaSessionsSource
import com.swooby.alfred.util.FooLog
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
