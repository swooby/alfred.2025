package com.swooby.alfred2017

import android.app.Application
import com.swooby.alfred2017.core.ingest.EventIngest
import com.swooby.alfred2017.core.ingest.EventIngestImpl
import com.swooby.alfred2017.core.rules.RulesEngine
import com.swooby.alfred2017.core.rules.RulesEngineImpl
import com.swooby.alfred2017.core.summary.SummaryGenerator
import com.swooby.alfred2017.core.summary.TemplatedSummaryGenerator
import com.swooby.alfred2017.data.AlfredDb
import com.swooby.alfred2017.settings.SettingsRepository
import com.swooby.alfred2017.sources.MediaSessionsSource
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