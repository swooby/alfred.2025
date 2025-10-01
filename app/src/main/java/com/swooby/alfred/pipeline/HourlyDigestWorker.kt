package com.swooby.alfred.pipeline

import android.content.Context
import androidx.work.*
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.summary.Utterance
import com.swooby.alfred.tts.SpeakerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class HourlyDigestWorker(appCtx: Context, params: WorkerParameters): CoroutineWorker(appCtx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as AlfredApp
        val now: Instant = Clock.System.now()
        val from = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 60L*60L*1000L)
        val recent = app.db.events().listByTime("u_local", from, now, 5000)
        val digest: Utterance.Digest = app.summarizer.digest("Last hour", recent)
        val speaker = SpeakerImpl(applicationContext)
        speaker.speak((listOf(digest.title) + digest.lines).joinToString(" "))
        speaker.shutdown()
        Result.success()
    }
    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<HourlyDigestWorker>(java.time.Duration.ofHours(1))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "alfred-hourly-digest", ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}