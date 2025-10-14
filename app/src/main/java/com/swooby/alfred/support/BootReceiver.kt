package com.swooby.alfred.support

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swooby.alfred.pipeline.HourlyDigestWorker
import com.swooby.alfred.pipeline.PipelineService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                context.startForegroundService(Intent(context, PipelineService::class.java))
                HourlyDigestWorker.schedule(context)
            }
        }
    }
}
