package com.swooby.alfred.support

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.pipeline.PipelineService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Coordinates a graceful, single-shot application shutdown. This ensures the foreground service
 * winds down, shared application resources are released, and finally the process exits so no
 * background components linger.
 */
object AppShutdownManager {
    private val TAG = FooLog.TAG(AppShutdownManager::class.java)
    private val quitting = AtomicBoolean(false)
    private val shutdownPrepared = AtomicBoolean(false)

    private fun prepareForShutdown(context: Context) {
        if (!shutdownPrepared.compareAndSet(false, true)) {
            FooLog.d(TAG, "prepareForShutdown: already prepared")
            return
        }
        FooLog.i(TAG, "prepareForShutdown: closing tasks and unbinding listener")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        activityManager?.appTasks?.forEach {
            runCatching { it.finishAndRemoveTask() }
        }
        PipelineService.requestNotificationListenerUnbind(context)
    }

    fun onPipelineServiceStarted(context: Context): Boolean {
        shutdownPrepared.set(false)
        quitting.set(false)
        return PipelineService.hasNotificationListenerAccess(context)
    }

    fun requestQuit(context: Context) {
        FooLog.i(TAG, "requestQuit(context)")
        if (!quitting.compareAndSet(false, true)) {
            FooLog.d(TAG, "requestQuit: shutdown already in progress")
        } else {
            FooLog.i(TAG, "requestQuit: initiating app shutdown")
        }
        prepareForShutdown(context)
        val appContext = context.applicationContext
        ContextCompat.startForegroundService(
            appContext,
            PipelineService.intentQuit(appContext),
        )
    }

    fun markQuitRequested(context: Context) {
        FooLog.v(TAG, "markQuitRequested()")
        if (quitting.compareAndSet(false, true)) {
            FooLog.i(TAG, "markQuitRequested: quit triggered by service command")
        }
        prepareForShutdown(context)
    }

    fun onPipelineServiceDestroyed(app: AlfredApp) {
        if (!quitting.get()) {
            FooLog.d(TAG, "onPipelineServiceDestroyed: service stopped outside quit flow; ignore")
            return
        }
        FooLog.i(TAG, "onPipelineServiceDestroyed: finalizing application scope")
        app.shutdown()
        FooLog.w(TAG, "onPipelineServiceDestroyed: terminating process")
        //android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }
}
