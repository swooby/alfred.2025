package com.smartfoo.android.core

import com.smartfoo.android.core.reflection.FooReflectionUtils

class FooListenerAutoStartManager<T>(name: String) : FooListenerManager<T?>(name) {
    interface FooListenerAutoStartManagerCallbacks {
        fun onFirstAttach()

        /**
         * @return true to automatically detach this [FooListenerAutoStartManagerCallbacks] from [ ][FooListenerAutoStartManager.autoStartListeners]
         */
        fun onLastDetach(): Boolean
    }

    private val autoStartListeners: FooListenerManager<FooListenerAutoStartManagerCallbacks> =
        FooListenerManager<FooListenerAutoStartManagerCallbacks>(this)

    private var isStarted = false

    constructor(name: Any) : this(FooReflectionUtils.getShortClassName(name))

    fun attach(callbacks: FooListenerAutoStartManagerCallbacks) {
        autoStartListeners.attach(callbacks)
    }

    fun detach(callbacks: FooListenerAutoStartManagerCallbacks) {
        autoStartListeners.detach(callbacks)
    }

    override fun onListenersUpdated(listenersSize: Int) {
        super.onListenersUpdated(listenersSize)

        if (isStarted) {
            if (listenersSize == 0) {
                isStarted = false
                for (callbacks in autoStartListeners.beginTraversing()) {
                    if (callbacks.onLastDetach()) {
                        //mAutoStartListeners.detach(callbacks);
                    }
                }
                autoStartListeners.endTraversing()
            }
        } else {
            if (listenersSize > 0) {
                isStarted = true
                for (callbacks in autoStartListeners.beginTraversing()) {
                    callbacks.onFirstAttach()
                }
                autoStartListeners.endTraversing()
            }
        }
    }
}
