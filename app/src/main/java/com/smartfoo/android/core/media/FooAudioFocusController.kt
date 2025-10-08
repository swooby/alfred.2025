package com.smartfoo.android.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import com.smartfoo.android.core.notification.FooListenerManager
import com.smartfoo.android.core.logging.FooLog
import java.util.concurrent.atomic.AtomicLong

/**
 * FooAudioFocusController
 *
 * Single controller for system AudioFocus with multi-caller safety.
 * - Only the first acquire() requests focus from the system.
 * - Only the final release() abandons focus.
 */
class FooAudioFocusController private constructor() {
    companion object {
        private val TAG = FooLog.TAG(FooAudioFocusController::class.java)
        val instance = FooAudioFocusController()
        var VERBOSE = true
    }

    // --- Callback API -------------------------------------------------------
    abstract class Callbacks {
        /** Return true to consume. */
        open fun onFocusGained(
            audioFocusController: FooAudioFocusController,
            audioFocusRequest: AudioFocusRequest
        ): Boolean = false

        /** Return true to consume. */
        open fun onFocusLost(
            audioFocusController: FooAudioFocusController,
            audioFocusRequest: AudioFocusRequest,
            focusChange: Int
        ): Boolean = false
    }

    /**
     * A per-caller lifetime object. Releasing detaches its callbacks and may abandon focus if last.
     */
    inner class FocusHandle internal constructor(
        private val id: Long,
        private val tag: String?,
        private val callbacks: Callbacks?
    ) : AutoCloseable {
        @Volatile private var closed = false
        override fun close() = release()
        fun release() {
            if (closed) return
            closed = true
            this@FooAudioFocusController.releaseInternal(id, tag, callbacks)
        }
    }

    // --- State --------------------------------------------------------------
    private val listeners = FooListenerManager<Callbacks>("#AUDIOFOCUS")
    private val nextId = AtomicLong(1)

    private var audioManager: AudioManager? = null
    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioAttributes: AudioAttributes? = null
    private var currentFocusGainType: Int = AudioManager.AUDIOFOCUS_NONE

    /** Total live handles (ownership), independent of tags. */
    private var liveHolders: Int = 0

    private val focusListener = OnAudioFocusChangeListener { change -> onAudioFocusChange(change) }

    // --- Core API ------------------------------------------------------------

    private fun buildRequest(audioAttributes: AudioAttributes, focusGainType: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(focusGainType)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    /**
     * Acquire a focus handle.
     * @param tag Optional log tag (purely for logs; does not affect ownership).
     * @return a [FocusHandle] when audio focus is granted, otherwise null.
     */
    @Synchronized
    fun acquire(
        context: Context,
        audioAttributes: AudioAttributes,
        focusGainType: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        callbacks: Callbacks? = null,
        tag: String? = null
    ): FocusHandle? {
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }

        val logTag = if (tag.isNullOrBlank()) "" else "[$tag] "

        // Attach per-caller callbacks for the lifetime of this handle.
        callbacks?.let { listeners.attach(it) }

        val wasZero = (liveHolders == 0)
        liveHolders += 1

        if (VERBOSE) FooLog.v(TAG, "${logTag}acquire(): holders=$liveHolders (wasZero=$wasZero)")

        if (wasZero) {
            // First holder triggers the system request
            val audioFocusRequest = buildRequest(audioAttributes, focusGainType)
            val result = audioManager!!.requestAudioFocus(audioFocusRequest)
            val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (VERBOSE) FooLog.v(TAG, "${logTag}requestAudioFocus result=$result granted=$granted")
            if (granted) {
                currentAudioFocusRequest = audioFocusRequest
                currentAudioAttributes   = audioAttributes
                currentFocusGainType    = focusGainType
                dispatchGained(audioFocusRequest)
            } else {
                // roll back this acquire since request failed
                liveHolders -= 1
                callbacks?.let { listeners.detach(it) }
                FooLog.w(TAG, "${logTag}requestAudioFocus denied: result=$result; returning null handle")
                return null
            }
        }

        return FocusHandle(nextId.getAndIncrement(), logTag, callbacks)
    }

    @Synchronized
    private fun releaseInternal(id: Long, tag: String?, callbacks: Callbacks?) {
        if (liveHolders <= 0) {
            // Already released/abandoned; detach callbacks if present and return.
            callbacks?.let { listeners.detach(it) }
            if (VERBOSE) FooLog.v(TAG, "${tag}release(#$id): already zero; ignoring")
            return
        }

        callbacks?.let { listeners.detach(it) }

        liveHolders -= 1
        if (VERBOSE) FooLog.v(TAG, "${tag}release(#$id): holders=$liveHolders")

        if (liveHolders == 0) {
            val audioFocusRequest = currentAudioFocusRequest
            currentAudioFocusRequest = null
            currentAudioAttributes = null
            currentFocusGainType = AudioManager.AUDIOFOCUS_NONE

            if (audioFocusRequest != null && audioManager != null) {
                val result = audioManager!!.abandonAudioFocusRequest(audioFocusRequest)
                val ok = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (VERBOSE) FooLog.v(TAG, "${tag}abandonAudioFocusRequest result=$result ok=$ok")
                if (ok) dispatchLost(audioFocusRequest, AudioManager.AUDIOFOCUS_LOSS)
            }
        }
    }

    // --- Focus change routing -----------------------------------------------
    @Synchronized
    private fun onAudioFocusChange(change: Int) {
        val audioFocusRequest = currentAudioFocusRequest ?: run {
            if (VERBOSE) FooLog.v(TAG, "onAudioFocusChange(${focusToString(change)}): no currentRequest; ignoring")
            return
        }
        if (VERBOSE) FooLog.v(TAG, "onAudioFocusChange(${focusToString(change)}) holders=$liveHolders")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> dispatchGained(audioFocusRequest)
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> dispatchLost(audioFocusRequest, change)
        }
    }

    private fun dispatchGained(audioFocusRequest: AudioFocusRequest) {
        for (cb in listeners.beginTraversing()) if (cb.onFocusGained(this, audioFocusRequest)) break
        listeners.endTraversing()
    }

    private fun dispatchLost(audioFocusRequest: AudioFocusRequest, change: Int) {
        for (cb in listeners.beginTraversing()) if (cb.onFocusLost(this, audioFocusRequest, change)) break
        listeners.endTraversing()
    }

    // --- Utils ---------------------------------------------------------------
    private fun focusToString(v: Int): String = when (v) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> "UNKNOWN($v)"
    }
}