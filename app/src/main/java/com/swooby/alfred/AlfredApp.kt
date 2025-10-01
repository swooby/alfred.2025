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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlfredApp : Application() {
    lateinit var appScope: CoroutineScope
    lateinit var db: AlfredDb
    lateinit var ingest: EventIngest
    lateinit var rules: RulesEngine
    lateinit var summarizer: SummaryGenerator
    lateinit var settings: SettingsRepository
    lateinit var mediaSource: MediaSessionsSource

    override fun onCreate() {
        super.onCreate()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        db = AlfredDb.open(this)
        ingest = EventIngestImpl(appScope)
        rules = RulesEngineImpl()
        summarizer = TemplatedSummaryGenerator()
        settings = SettingsRepository(this)
        mediaSource = MediaSessionsSource(this, this)
    }
}