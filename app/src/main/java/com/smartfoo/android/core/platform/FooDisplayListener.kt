package com.smartfoo.android.core.platform

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import com.smartfoo.android.core.FooListenerAutoStartManager
import com.smartfoo.android.core.FooListenerAutoStartManager.FooListenerAutoStartManagerCallbacks
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils.toString

class FooDisplayListener(private val context: Context) {
    companion object {
        private val TAG = FooLog.TAG(FooDisplayListener::class.java)
    }

    interface FooDisplayListenerCallbacks {
        /**
         * Fires on [android.content.Intent.ACTION_SCREEN_OFF] broadcast.
         *
         * After the display turns off the OS allows a small grace period for the user to turn the
         * display back on without having to unlock the device.
         * If the user turns on the display during that small grace period then [onDisplayOn] will
         * fire with `isDeviceLocked == false`.
         * If the user turns on the display after that grace period then [onDisplayOn] will fire
         * with `isDeviceLocked == true`.
         *
         * There is no reliable event for when the device locks.
         *
         * When the screen is off assume the device is locked and private until either
         * [onDeviceUnlocked] fires or [onDisplayOn] fires with `isDeviceLocked == false`.
         */
        fun onDisplayOff(displayId: Int)

        /**
         * Fires on [android.content.Intent.ACTION_SCREEN_ON] broadcast.
         */
        fun onDisplayOn(displayId: Int, isDeviceLocked: Boolean)

        /**
         * Fires on [android.content.Intent.ACTION_USER_PRESENT] broadcast.
         */
        fun onDeviceUnlocked()
    }

    private val listenerManager = FooListenerAutoStartManager<FooDisplayListenerCallbacks?>(this)
    private val screenBroadcastReceiver: FooScreenBroadcastReceiver

    init {
        listenerManager.attach(object : FooListenerAutoStartManagerCallbacks {
            override fun onFirstAttach() {
                screenBroadcastReceiver.start(context, object : FooDisplayListenerCallbacks {
                    override fun onDisplayOff(displayId: Int) {
                        for (callbacks in listenerManager.beginTraversing()) {
                            callbacks!!.onDisplayOff(displayId)
                        }
                        listenerManager.endTraversing()
                    }

                    override fun onDisplayOn(displayId: Int, isDeviceLocked: Boolean) {
                        for (callbacks in listenerManager.beginTraversing()) {
                            callbacks!!.onDisplayOn(displayId, isDeviceLocked)
                        }
                        listenerManager.endTraversing()
                    }

                    override fun onDeviceUnlocked() {
                        for (callbacks in listenerManager.beginTraversing()) {
                            callbacks!!.onDeviceUnlocked()
                        }
                        listenerManager.endTraversing()
                    }
                })
            }

            override fun onLastDetach(): Boolean {
                screenBroadcastReceiver.stop(context)
                return false
            }
        })
        screenBroadcastReceiver = FooScreenBroadcastReceiver(context)
    }

    fun isDisplayOn(displayId: Int = Display.DEFAULT_DISPLAY): Boolean = screenBroadcastReceiver.isDisplayOn(displayId)

    val isDeviceLocked: Boolean = screenBroadcastReceiver.isDeviceLocked

    fun attach(callbacks: FooDisplayListenerCallbacks?) {
        FooLog.v(TAG, "attach(callbacks=$callbacks)")
        listenerManager.attach(callbacks)
    }

    fun detach(callbacks: FooDisplayListenerCallbacks?) {
        FooLog.v(TAG, "detach(callbacks=$callbacks)")
        listenerManager.detach(callbacks)
    }

    private class FooScreenBroadcastReceiver(context: Context) : BroadcastReceiver() {
        companion object {
            private val TAG = FooLog.TAG(FooScreenBroadcastReceiver::class.java)
        }

        private val syncLock: Any = Any()
        private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        //private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        private var callbacks: FooDisplayListenerCallbacks? = null

        fun isDisplayOn(displayId: Int): Boolean = !isDisplayOff(displayId)

        fun isDisplayOff(displayId: Int): Boolean {
            return displayManager.displays
                .firstOrNull { it.displayId == displayId }
                ?.let { it.state == Display.STATE_OFF }
                ?: false
        }

        val isDeviceLocked: Boolean
            get() {
                val isDeviceLocked = keyguardManager.isDeviceLocked
                FooLog.v(TAG, "isDeviceLocked: isDeviceLocked == $isDeviceLocked")
                return isDeviceLocked
            }

        var isStarted: Boolean = false
            get() {
                synchronized(syncLock) {
                    return field
                }
            }
            private set(value) {
                synchronized(syncLock) {
                    field = value
                }
            }

        fun start(context: Context, callbacks: FooDisplayListenerCallbacks) {
            FooLog.v(TAG, "+start(...)")
            synchronized(syncLock) {
                if (!isStarted) {
                    isStarted = true

                    this.callbacks = callbacks

                    val intentFilter = IntentFilter()
                    intentFilter.addAction(Intent.ACTION_SCREEN_OFF) // API 1
                    intentFilter.addAction(Intent.ACTION_SCREEN_ON) // API 1
                    intentFilter.addAction(Intent.ACTION_USER_PRESENT) // API 3

                    context.registerReceiver(this, intentFilter)
                }
            }
            FooLog.v(TAG, "-start(...)")
        }

        fun stop(context: Context) {
            FooLog.v(TAG, "+stop()")
            synchronized(syncLock) {
                if (isStarted) {
                    isStarted = false

                    context.unregisterReceiver(this)

                    this.callbacks = null
                }
            }
            FooLog.v(TAG, "-stop()")
        }

        override fun onReceive(context: Context?, intent: Intent) {
            FooLog.v(TAG, "onReceive: intent == " + toString(intent))
            val action = intent.action
            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (isDisplayOff(Display.DEFAULT_DISPLAY)) {
                        callbacks?.onDisplayOff(Display.DEFAULT_DISPLAY)
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    if (isDisplayOn(Display.DEFAULT_DISPLAY)) {
                        val isDeviceLocked = this.isDeviceLocked
                        FooLog.v(TAG, "onReceive: isDisplayOn==true; isDeviceLocked==$isDeviceLocked")
                        callbacks?.onDisplayOn(Display.DEFAULT_DISPLAY, isDeviceLocked)
                    }
                }

                Intent.ACTION_USER_PRESENT -> {
                    callbacks?.onDeviceUnlocked()
                }
            }
        }
    }
}
