package com.smartfoo.android.core

import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.reflection.FooReflectionUtils
import com.swooby.alfred.BuildConfig
import java.util.Collections

open class FooListenerManager<T>(name: String) {
    companion object {
        private val TAG = FooLog.TAG(FooListenerManager::class.java)

        private val VERBOSE_LOG = false && BuildConfig.DEBUG
    }

    constructor(name: Any) : this(FooReflectionUtils.getShortClassName(name))

    private val mName: String = FooString.quote(name.trim())
    private val mListeners: MutableSet<T> = LinkedHashSet()
    private val mListenersToAdd: MutableSet<T> = LinkedHashSet()
    private val mListenersToRemove: MutableSet<T> = LinkedHashSet()

    private var mIsTraversingListeners = false

    override fun toString(): String {
        return "{ mName=$mName, size()=${size()} }"
    }

    fun size(): Int {
        val size: Int
        synchronized(mListeners) {
            val consolidated: MutableSet<T> = LinkedHashSet(mListeners)
            consolidated.addAll(mListenersToAdd)
            consolidated.removeAll(mListenersToRemove)
            size = consolidated.size
        }
        /*
        if (VERBOSE_LOG)
        {
            FooLog.v(TAG, mName + " size() == " + size);
        }
        */
        return size
    }

    val isEmpty: Boolean
        get() = size() == 0

    fun hasListener(listener: T): Boolean {
        synchronized(mListeners) {
            return mListenersToAdd.contains(listener) || mListeners.contains(listener) || mListenersToRemove.contains(
                listener
            )
        }
    }

    fun attach(listener: T?) {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName attach(...)")
        }

        if (listener == null) {
            return
        }

        synchronized(mListeners) {
            if (hasListener(listener)) {
                return
            }
            if (mIsTraversingListeners) {
                mListenersToAdd.add(listener)
            } else {
                mListeners.add(listener)
                updateListeners()
            }
        }
    }

    fun detach(listener: T?) {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName detach(...)")
        }

        if (listener == null) {
            return
        }

        synchronized(mListeners) {
            if (mIsTraversingListeners) {
                mListenersToRemove.add(listener)
            } else {
                mListeners.remove(listener)
                updateListeners()
            }
        }
    }

    fun clear() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName clear()")
        }
        synchronized(mListeners) {
            mListenersToAdd.clear()
            if (mIsTraversingListeners) {
                mListenersToRemove.addAll(mListeners)
            } else {
                mListeners.clear()
                mListenersToRemove.clear()
            }
        }
    }

    fun beginTraversing(): Set<T> {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName beginTraversing()")
        }
        synchronized(mListeners) {
            mIsTraversingListeners = true
            return Collections.unmodifiableSet(mListeners)
        }
    }

    fun endTraversing() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName endTraversing()")
        }
        synchronized(mListeners) {
            updateListeners()
            mIsTraversingListeners = false
        }
    }

    private fun updateListeners() {
        if (VERBOSE_LOG) {
            FooLog.v(TAG, "$mName updateListeners()")
        }
        synchronized(mListeners) {
            var it = mListenersToAdd.iterator()
            while (it.hasNext()) {
                mListeners.add(it.next())
                it.remove()
            }
            it = mListenersToRemove.iterator()
            while (it.hasNext()) {
                mListeners.remove(it.next())
                it.remove()
            }
            onListenersUpdated(mListeners.size)
        }
    }

    protected open fun onListenersUpdated(listenersSize: Int) {
    }
}