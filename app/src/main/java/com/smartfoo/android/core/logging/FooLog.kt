package com.smartfoo.android.core.logging

import android.util.Log
import com.smartfoo.android.core.reflection.FooReflectionUtils

@Suppress("unused")
object FooLog {
    @JvmStatic
    fun TAG(o: Any?): String {
        return TAG(o?.javaClass)
    }

    @JvmStatic
    fun TAG(c: Class<*>?): String {
        return TAG(FooReflectionUtils.getShortClassName(c))
    }

    /**
     * Per http://developer.android.com/reference/android/util/Log.html#isLoggable(java.lang.String, int)
     */
    const val LOG_TAG_LENGTH_LIMIT: Int = 23

    /**
     * Limits the tag length to [.LOG_TAG_LENGTH_LIMIT]
     *
     * @param value tag
     * @return the tag limited to [.LOG_TAG_LENGTH_LIMIT]
     */
    @JvmStatic
    fun TAG(value: String): String {
        var tag = value
        val length = tag.length
        if (length > LOG_TAG_LENGTH_LIMIT) {
            // Turn "ReallyLongClassName" to "ReallyLo…lassName";
            val half = LOG_TAG_LENGTH_LIMIT / 2
            tag = tag.substring(0, half) + '…' + tag.substring(length - half)
        }
        return tag
    }

    @JvmStatic
    fun v(tag: String?, msg: String?) {
        v(tag, msg, null)
    }

    @JvmStatic
    fun v(tag: String?, e: Throwable?) {
        v(tag, "Throwable", e)
    }

    @JvmStatic
    fun v(tag: String?, msg: String?, e: Throwable?) {
        Log.v(tag, msg, e)
    }

    @JvmStatic
    fun d(tag: String?, msg: String?) {
        d(tag, msg, null)
    }

    @JvmStatic
    fun d(tag: String?, e: Throwable?) {
        d(tag, "Throwable", e)
    }

    @JvmStatic
    fun d(tag: String?, msg: String?, e: Throwable?) {
        Log.d(tag, msg, e)
    }

    @JvmStatic
    fun i(tag: String?, msg: String?) {
        i(tag, msg, null)
    }

    @JvmStatic
    fun i(tag: String?, e: Throwable?) {
        i(tag, "Throwable", e)
    }

    @JvmStatic
    fun i(tag: String?, msg: String?, e: Throwable?) {
        Log.i(tag, msg, e)
    }

    @JvmStatic
    fun w(tag: String?, msg: String?) {
        w(tag, msg, null)
    }

    @JvmStatic
    fun w(tag: String?, e: Throwable?) {
        w(tag, "Throwable", e)
    }

    @JvmStatic
    fun w(tag: String?, msg: String?, e: Throwable?) {
        Log.w(tag, msg, e)
    }

    @JvmStatic
    fun e(tag: String?, msg: String?) {
        e(tag, msg, null)
    }

    @JvmStatic
    fun e(tag: String?, e: Throwable?) {
        e(tag, "Throwable", e)
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, e: Throwable?) {
        Log.e(tag, msg, e)
    }

    @JvmStatic
    fun f(tag: String?, msg: String?) {
        f(tag, msg, null)
    }

    @JvmStatic
    fun f(tag: String?, e: Throwable?) {
        f(tag, "Throwable", e)
    }

    @JvmStatic
    fun f(tag: String?, msg: String?, e: Throwable?) {
        Log.wtf(tag, msg, e)
    }
}