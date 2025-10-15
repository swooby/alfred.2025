package com.swooby.alfred.support

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swooby.alfred.AlfredApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as? AlfredApp)?.onBootCompleted()
    }
}
