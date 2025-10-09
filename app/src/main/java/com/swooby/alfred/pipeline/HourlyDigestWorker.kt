package com.swooby.alfred.pipeline

import android.content.Context
import androidx.work.*
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.summary.Utterance
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.texttospeech.FooTextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

class HourlyDigestWorker(appCtx: Context, params: WorkerParameters): CoroutineWorker(appCtx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as AlfredApp
        val now: Instant = Clock.System.now()
        val from = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 60L*60L*1000L)
        val recent = app.db.events().listByTime("u_local", from, now, 5000)
        val digest: Utterance.Digest = app.summarizer.digest("Last hour", recent)
        val text = (listOf(digest.title) + digest.lines).joinToString(" ")
        val gate = app.audioProfiles.evaluateGate()
        if (!gate.allow) {
            val selectedId = gate.snapshot?.profile?.id?.value
                ?: app.audioProfiles.uiState.value.selectedProfileId?.value
                ?: "none"
            FooLog.d(TAG, "#DIGEST audio profile blocked speech; reason=${gate.reason}; selected=$selectedId")
            return@withContext Result.success()
        }
        FooTextToSpeech.speak(app, text)
        Result.success()
    }
    companion object {
        private val TAG = FooLog.TAG(HourlyDigestWorker::class.java)
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
