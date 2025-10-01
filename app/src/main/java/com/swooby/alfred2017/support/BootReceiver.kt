package com.swooby.alfred2017.support

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swooby.alfred2017.pipeline.HourlyDigestWorker
import com.swooby.alfred2017.pipeline.PipelineService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, PipelineService::class.java))
        HourlyDigestWorker.schedule(context)
    }
}