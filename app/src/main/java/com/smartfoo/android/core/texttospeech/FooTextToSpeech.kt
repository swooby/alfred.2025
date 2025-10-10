package com.smartfoo.android.core.texttospeech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.smartfoo.android.core.FooListenerManager
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.media.FooAudioFocusController
import com.smartfoo.android.core.media.FooAudioUtils
import com.smartfoo.android.core.platform.FooPlatformUtils
import com.smartfoo.android.core.texttospeech.FooTextToSpeech.Companion.speak
import com.smartfoo.android.core.texttospeech.FooTextToSpeechBuilder.FooTextToSpeechPart
import com.smartfoo.android.core.texttospeech.FooTextToSpeechBuilder.FooTextToSpeechPartEarcon
import com.smartfoo.android.core.texttospeech.FooTextToSpeechBuilder.FooTextToSpeechPartSilence
import com.smartfoo.android.core.texttospeech.FooTextToSpeechBuilder.FooTextToSpeechPartSpeech
import java.io.File

/**
 * NOTE: There should be only one TextToSpeech instance per application.
 * Callers must use FooTextToSpeech.instance to get the singleton instance.
 * Then call instance.start(context, callbacks) to initialize the instance.
 * This call instance.stop() when done.
 * Optionally call instance.shutdown() when done.
 *
 * References:
 *  * https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/speech/tts/
 *  * https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Settings/src/com/android/settings/tts/
 *
 *  NOTE: There should be no need to implement [android.speech.tts.TextToSpeech.setLanguage], the code of which has a comment that reads:
 *      "As of API level 21, setLanguage is implemented using setVoice"
 *
 *  TODO: Add wrappers around [android.speech.tts.TextToSpeech.addSpeech]?
 */
class FooTextToSpeech private constructor() {
    companion object {
        private val TAG = FooLog.TAG(FooTextToSpeech::class.java)

        var VERBOSE_LOG_INIT_QUEUE = false
        var VERBOSE_LOG_SPEAK = false
        var VERBOSE_LOG_SILENCE = false
        var VERBOSE_LOG_EARCON = false
        /**
         * Main thing to debug
         */
        var VERBOSE_LOG_UTTERANCE = false
        var VERBOSE_LOG_UTTERANCE_PROGRESS = false
        var VERBOSE_LOG_AUDIO_FOCUS = false

        const val DEFAULT_VOICE_SPEED = 1.35f
        const val DEFAULT_VOICE_PITCH = 1.0f
        const val DEFAULT_VOICE_VOLUME = 1.0f

        /**
         * Singleton instance of [FooTextToSpeech]
         */
        @JvmStatic
        val instance: FooTextToSpeech by lazy {
            FooTextToSpeech()
        }

        @JvmStatic
        fun speak(context: Context, text: String): Boolean {
            return instance.start(context).speak(text)
        }

        @JvmStatic
        fun statusToString(status: Int): String {
            return when (status) {
                TextToSpeech.SUCCESS -> "SUCCESS"
                TextToSpeech.ERROR -> "ERROR"
                TextToSpeech.STOPPED -> "STOPPED"
                else -> "UNKNOWN"
            }.let { "$it($status)" }
        }

        @JvmStatic
        fun queueModeToString(queueMode: Int): String {
            @Suppress("KDocUnresolvedReference")
            return when (queueMode) {
                TextToSpeech.QUEUE_ADD -> "QUEUE_ADD"
                TextToSpeech.QUEUE_FLUSH -> "QUEUE_FLUSH"
                /** hidden [android.speech.tts.TextToSpeech.QUEUE_DESTROY] */
                2 -> "QUEUE_DESTROY"
                else -> "UNKNOWN"
            }.let { "$it($queueMode)" }
        }
    }

    interface FooTextToSpeechCallbacks {
        fun onTextToSpeechInitialized(status: Int)
    }

    abstract class Utterance(val runAfter: Runnable?)
    class UtteranceText(val text: String, runAfter: Runnable?) : Utterance(runAfter)
    class UtteranceSilence(val durationMillis: Int, runAfter: Runnable?) : Utterance(runAfter)
    class UtteranceEarcon(val earcon: String, runAfter: Runnable?) : Utterance(runAfter)

    private val syncLock = Any()
    private val listeners = FooListenerManager<FooTextToSpeechCallbacks>(TAG)
    private val initQueue = mutableListOf<Utterance>()
    private val utteranceCallbacks = mutableMapOf<String, Runnable>()
    private val runAfterSpeak = Runnable { onRunAfterSpeak() }

    private val audioFocusController: FooAudioFocusController
    private val audioFocusControllerCallbacks: FooAudioFocusController.Callbacks
    @Volatile
    private var audioFocusControllerHandle: FooAudioFocusController.FocusHandle? = null

    /**
     * Audio-focus acquisition strategy:
     *  - Read/assign `audioFocusControllerHandle` only while holding `syncLock`.
     *  - Perform the potentially blocking framework `acquire(...)` call *outside* the lock.
     *  - Use double‑checked publication under `syncLock`; if another thread wins the race,
     *    release the extra handle to avoid leaks.
     */
    private fun audioFocusAcquireTry(audioAttributes: AudioAttributes): Boolean {
        val context = applicationContext
            ?: throw IllegalStateException("start(context) must be called before acquiring audio focus")

        // Fast path: if we already hold a handle, reuse it.
        synchronized(syncLock) {
            if (audioFocusControllerHandle != null) {
                if (VERBOSE_LOG_AUDIO_FOCUS) {
                    FooLog.v(TAG, "#TTS_AUDIO_FOCUS audioFocusAcquireTry: audioFocusControllerHandle already acquired; reusing")
                }
                return true
            }
        }

        // Slow path: request focus WITHOUT holding the lock.
        // This avoids blocking other threads that might be calling speak()/stop() and also
        // prevents lock inversions with framework callbacks.
        val newHandle = audioFocusController.acquire(
            context = context,
            audioAttributes = audioAttributes,
            callbacks = audioFocusControllerCallbacks
        )
        if (newHandle == null) {
            FooLog.w(TAG, "#TTS_AUDIO_FOCUS audioFocusAcquireTry: audio focus request denied (ex: another app has AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE); ignoring")
            return false
        }

        synchronized(syncLock) {
            if (VERBOSE_LOG_AUDIO_FOCUS) {
                FooLog.v(TAG, "#TTS_AUDIO_FOCUS audioFocusAcquireTry: audio focus newHandle acquired; checking if no other thread acquired one...")
            }
            if (audioFocusControllerHandle == null) {
                if (VERBOSE_LOG_AUDIO_FOCUS) {
                    FooLog.v(TAG, "#TTS_AUDIO_FOCUS audioFocusAcquireTry: no other thread acquired audioFocusControllerHandle; audioFocusControllerHandle = newHandle")
                }
                audioFocusControllerHandle = newHandle
                null
            } else {
                newHandle
            }
        }?.let {
            if (VERBOSE_LOG_AUDIO_FOCUS) {
                FooLog.v(TAG, "#TTS_AUDIO_FOCUS audioFocusAcquireTry: another thread already acquired audioFocusControllerHandle; newHandle.release()")
            }
            it.release()
        }
        return true
    }

    private fun audioFocusHandleClearLocked(): FooAudioFocusController.FocusHandle? {
        val handle = audioFocusControllerHandle ?: return null
        audioFocusControllerHandle = null
        return handle
    }

    private var applicationContext: Context? = null
    private var tts: TextToSpeech? = null
    private var nextUtteranceId: Long = 0

    val voices: Set<Voice>?
        get() {
            synchronized(syncLock) { return tts?.voices }
        }

    var voiceName: String? = null
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

    /**
     * @param value null to set default voice, or the name of a voice in [.getVoices]
     * @return true if changed, otherwise false
     */
    fun setVoiceName(value: String?): Boolean {
        var voiceName = value
        FooLog.v(TAG, "setVoiceName(${voiceName})")
        if (voiceName.isNullOrEmpty()) {
            voiceName = null
        }
        val oldValue: String?
        val changed: Boolean
        synchronized(syncLock) {
            oldValue = this.voiceName
            if (tts == null) {
                this.voiceName = voiceName
                changed = oldValue != this.voiceName
            } else {
                var foundVoice = tts!!.defaultVoice
                if (voiceName != null) {
                    val voices = voices
                    if (voices != null) {
                        for (voice in voices) {
                            //FooLog.e(TAG, "setVoiceName: voice=${FooString.quote(voice.name)}"); // debug
                            if (voiceName.equals(voice.name, ignoreCase = true)) {
                                foundVoice = voice
                                break
                            }
                        }
                    }
                }
                this.voiceName = foundVoice.name
                changed = oldValue != this.voiceName
                tts!!.setVoice(foundVoice)
            }
        }
        return changed
    }

    /**
     * Speech rate. `1.0` is the normal speech rate,
     * lower values slow down the speech (`0.5` is half the normal speech rate),
     * greater values accelerate it (`2.0` is twice the normal speech rate).
     */
    var voiceSpeed: Float = DEFAULT_VOICE_SPEED
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    /**
     * Speech pitch. `1.0` is the normal pitch,
     * lower values lower the tone of the synthesized voice,
     * greater values increase it.
     */
    var voicePitch: Float = DEFAULT_VOICE_PITCH
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    var audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
        get() {
            synchronized(syncLock) { return field }
        }
        private set(value) {
            synchronized(syncLock) {
                field = value
                tts?.setAudioAttributes(field)
            }
        }

    var volumeRelativeToAudioStream: Float = DEFAULT_VOICE_VOLUME
        /**
         * @return 0 (silence) to 1 (maximum)
         */
        get() {
            synchronized(syncLock) { return field }
        }
        /**
         * @param volumeRelativeToAudioStream 0 (silence) to 1 (maximum)
         */
        set(volumeRelativeToAudioStream) {
            synchronized(syncLock) { field = volumeRelativeToAudioStream }
        }

    var isStarted: Boolean = false
        get() {
            synchronized(syncLock) { return field }
        }
        private set(value) {
            synchronized(syncLock) {
                field = value
            }
        }

    var isInitialized: Boolean = false
        get() {
            synchronized(syncLock) { return field }
        }
        private set(value) {
            synchronized(syncLock) {
                field = value
            }
        }

    init {
        FooLog.v(TAG, "#TTS +FooTextToSpeech()")
        audioFocusController = FooAudioFocusController.instance
        audioFocusControllerCallbacks = object : FooAudioFocusController.Callbacks() {
            override fun onFocusGained(
                audioFocusController: FooAudioFocusController,
                audioFocusRequest: AudioFocusRequest
            ): Boolean {
                return this@FooTextToSpeech.onAudioFocusGained()
            }

            override fun onFocusLost(
                audioFocusController: FooAudioFocusController,
                audioFocusRequest: AudioFocusRequest,
                focusChange: Int
            ): Boolean {
                return this@FooTextToSpeech.onAudioFocusLost(focusChange)
            }
        }
        FooLog.v(TAG, "#TTS -FooTextToSpeech()")
    }

    fun attach(callbacks: FooTextToSpeechCallbacks) {
        synchronized(syncLock) { listeners.attach(callbacks) }
    }

    @Suppress("unused")
    fun detach(callbacks: FooTextToSpeechCallbacks) {
        synchronized(syncLock) { listeners.detach(callbacks) }
    }

    /**
     * Checks whether the TTS engine is busy speaking. Note that a speech item is considered complete once it's audio data has been sent to the audio mixer, or written to a file. There might be a finite lag between this point, and when the audio hardware completes playback.
     *
     * @return tts?.isSpeaking ?: false
     *
     * @see [android.speech.tts.TextToSpeech.isSpeaking]
     */
    @Suppress("unused")
    val isSpeaking: Boolean = synchronized(syncLock) { tts?.isSpeaking ?: false }

    /**
     * Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue.
     *
     * @return tts?.stop() == TextToSpeech.SUCCESS
     *
     * @see [android.speech.tts.TextToSpeech.stop]
     */
    @Suppress("unused")
    fun stopSpeaking(): Boolean = synchronized(syncLock) { tts?.stop() == TextToSpeech.SUCCESS }

    fun stop() {
        synchronized(syncLock) {
            clear()
            isStarted = false
            tts?.let {
                it.stop()
                it.shutdown()
                tts = null
            }
            isInitialized = false
        }
    }

    @JvmOverloads
    fun start(
        context: Context,
        callbacks: FooTextToSpeechCallbacks? = null
    ): FooTextToSpeech {
        FooLog.v(TAG, "#TTS +start(context=$context, callbacks=$callbacks)")
        synchronized(syncLock) {
            if (applicationContext == null) {
                applicationContext = context.applicationContext
            }
            if (callbacks != null) {
                attach(callbacks)
            }
            if (isStarted) {
                if (isInitialized) {
                    callbacks?.onTextToSpeechInitialized(TextToSpeech.SUCCESS)
                }
                FooLog.v(TAG, "#TTS start: already started; ignoring")
            } else {
                isStarted = true
                tts = TextToSpeech(applicationContext) { status -> onTextToSpeechInitialized(status) }
            }
        }
        FooLog.v(TAG, "#TTS -start(context=$context, callbacks=$callbacks)")
        return this
    }

    private fun onTextToSpeechInitialized(status: Int) {
        try {
            FooLog.v(TAG, "#TTS +onTextToSpeechInitialized(status=${statusToString(status)})")
            synchronized(syncLock) {
                if (!isStarted) {
                    return
                }
                val success = status == TextToSpeech.SUCCESS
                if (!success) {
                    FooLog.w(TAG, "#TTS onTextToSpeechInitialized: TextToSpeech failed to initialize: status == ${statusToString(status)}")
                } else {
                    tts?.let {
                        //it.language = Locale.getDefault()
                        it.setAudioAttributes(audioAttributes)
                        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                this@FooTextToSpeech.onUtteranceStart(utteranceId)
                            }

                            override fun onDone(utteranceId: String?) {
                                this@FooTextToSpeech.onUtteranceDone(utteranceId)
                            }

                            @Deprecated(
                                "Deprecated in Java", ReplaceWith(
                                    "onError(utteranceId, TextToSpeech.ERROR)",
                                    "android.speech.tts.TextToSpeech"
                                )
                            )
                            // Kotlin 2.2.20 bug falsely warns:
                            // "This declaration overrides a deprecated member but is not marked as deprecated itself. Add the '@Deprecated' annotation or suppress the diagnostic."
                            // https://youtrack.jetbrains.com/issue/KT-80399/Anonymous-Kotlin-class-incorrectly-warns-about-deprecated-java-override-despite-Deprecated-annotation
                            // Available in: 2.3.0-Beta1, State: Fixed, Fix in builds: 2.3.0-dev-5366
                            @SuppressWarnings("OVERRIDE_DEPRECATION")
                            override fun onError(utteranceId: String?) {
                                onError(utteranceId, TextToSpeech.ERROR)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                this@FooTextToSpeech.onUtteranceError(utteranceId, errorCode)
                            }
                        })
                    }

                    setVoiceName(voiceName)
                    voiceSpeed = voiceSpeed
                    voicePitch = voicePitch

                    isInitialized = true
                }
                for (callbacks in listeners.beginTraversing()) {
                    callbacks.onTextToSpeechInitialized(status)
                }
                listeners.endTraversing()

                if (!isInitialized) {
                    stop()
                } else {
                    val initQueueSize = initQueue.size
                    if (initQueueSize > 0) {
                        if (VERBOSE_LOG_INIT_QUEUE) {
                            FooLog.v(TAG, "#TTS_INIT onTextToSpeechInitialized: speaking initQueue($initQueueSize) items...")
                        }
                        val iterator = initQueue.iterator()
                        var utterance: Utterance
                        while (iterator.hasNext()) {
                            utterance = iterator.next()
                            iterator.remove()
                            when (utterance) {
                                is UtteranceText -> speakInternal(true, utterance.text, utterance.runAfter)
                                is UtteranceSilence -> silence(true, utterance.durationMillis, utterance.runAfter)
                                is UtteranceEarcon -> playEarcon(true, utterance.earcon, utterance.runAfter)
                            }
                        }
                    }
                }
            } // syncLock
        } finally {
            FooLog.v(TAG, "#TTS -onTextToSpeechInitialized(status=${statusToString(status)})")
        }
    }

    private fun onUtteranceStart(utteranceId: String?) {
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.v(TAG, "#TTS_UTTERANCE_PROGRESS +onUtteranceStart(utteranceId=${FooString.quote(utteranceId)})")
        }
        audioFocusAcquireTry(audioAttributes)
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.v(TAG, "#TTS_UTTERANCE_PROGRESS -onUtteranceStart(utteranceId=${FooString.quote(utteranceId)})")
        }
    }

    private fun onUtteranceDone(utteranceId: String?) {
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.v(TAG, "#TTS_UTTERANCE_PROGRESS +onUtteranceDone(utteranceId=${FooString.quote(utteranceId)})")
        }
        val runAfter: Runnable?
        synchronized(syncLock) {
            runAfter = utteranceCallbacks.remove(utteranceId)
            if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
                FooLog.e(TAG, "#TTS_UTTERANCE_PROGRESS onUtteranceDone: mUtteranceCallbacks.size() == ${utteranceCallbacks.size}")
            }
        }
        //FooLog.v(TAG, "onUtteranceDone: runAfter=$runAfter");
        runAfter?.run()
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.v(TAG, "#TTS_UTTERANCE_PROGRESS -onUtteranceDone(utteranceId=${FooString.quote(utteranceId)})")
        }
    }

    private fun onUtteranceError(utteranceId: String?, errorCode: Int) {
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.w(TAG, "#TTS_UTTERANCE_PROGRESS +onUtteranceError(utteranceId=${FooString.quote(utteranceId)}, errorCode=$errorCode)")
        }
        val runAfter: Runnable?
        synchronized(syncLock) {
            runAfter = utteranceCallbacks.remove(utteranceId)
            if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
                FooLog.e(TAG, "#TTS_UTTERANCE_PROGRESS onUtteranceError: mUtteranceCallbacks.size() == ${utteranceCallbacks.size}")
            }
        }
        //FooLog.w(TAG, "onUtteranceError: runAfter=$runAfter");
        runAfter?.run()
        if (VERBOSE_LOG_UTTERANCE_PROGRESS) {
            FooLog.w(TAG, "#TTS_UTTERANCE_PROGRESS -onUtteranceError(utteranceId=${FooString.quote(utteranceId)}), errorCode=$errorCode)")
        }
    }

    private fun onRunAfterSpeak() {
        if (VERBOSE_LOG_AUDIO_FOCUS) {
            FooLog.v(TAG, "#TTS_AUDIO_FOCUS +onRunAfterSpeak()")
        }
        synchronized(syncLock) {
            val size = utteranceCallbacks.size
            if (size == 0) {
                if (VERBOSE_LOG_AUDIO_FOCUS) {
                    FooLog.v(TAG, "#TTS_AUDIO_FOCUS onRunAfterSpeak: mUtteranceCallbacks.size() == 0; audioFocusStop()")
                }
                audioFocusHandleClearLocked()
            } else {
                if (VERBOSE_LOG_AUDIO_FOCUS) {
                    FooLog.v(TAG, "#TTS_AUDIO_FOCUS onRunAfterSpeak: mUtteranceCallbacks.size()($size) > 0; ignoring (not calling `audioFocusStop()`)")
                }
                null
            }
        }?.let {
            it.release()
            onAudioFocusStop()
        }
        if (VERBOSE_LOG_AUDIO_FOCUS) {
            FooLog.v(TAG, "#TTS_AUDIO_FOCUS -onRunAfterSpeak()")
        }
    }

    fun clear() {
        FooLog.d(TAG, "#TTS +clear()")
        synchronized(syncLock) {
            initQueue.clear()

            if (isInitialized) {
                tts!!.stop()
            }
            utteranceCallbacks.clear()

            audioFocusHandleClearLocked()
        }?.let {
            it.release()
            onAudioFocusStop()
        }
        FooLog.d(TAG, "#TTS -clear()")
    }

    private fun onAudioFocusGained(): Boolean {
        if (VERBOSE_LOG_AUDIO_FOCUS) {
            FooLog.e(TAG, "#TTS_AUDIO_FOCUS onAudioFocusGained()")
        }
        return false
    }

    private fun onAudioFocusLost(focusChange: Int): Boolean {
        if (VERBOSE_LOG_AUDIO_FOCUS) {
            FooLog.e(TAG, "#TTS_AUDIO_FOCUS onAudioFocusLost(focusChange=${FooAudioUtils.audioFocusGainLossToString(focusChange)})")
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                //...
            }
        }
        return false
    }

    private fun onAudioFocusStop() {
        if (VERBOSE_LOG_AUDIO_FOCUS) {
            FooLog.e(TAG, "#TTS_AUDIO_FOCUS onAudioFocusStop()")
        }
    }

    fun speak(text: String): Boolean {
        return speak(false, text)
    }

    @Suppress("unused")
    fun speak(text: String, runAfter: Runnable?): Boolean {
        return speak(false, text, runAfter)
    }

    @JvmOverloads
    fun speak(clear: Boolean, text: String, runAfter: Runnable? = null): Boolean {
        return speak(clear, FooTextToSpeechBuilder(text), runAfter)
    }

    @Suppress("unused")
    fun speak(builder: FooTextToSpeechBuilder): Boolean {
        return speak(false, builder, null)
    }

    private inner class Runnables(private vararg val runnables: Runnable) : Runnable {
        override fun run() {
            for (runnable in runnables) {
                runnable.run()
            }
        }
    }

    /**
     * ALWAYS ALSO CALLS builder?.[FooTextToSpeechBuilder.appendSilenceSentenceBreak]
     * so that there is ALWAYS a clear break before the NEXT speech.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun speak(clear: Boolean, builder: FooTextToSpeechBuilder?, runAfter: Runnable?): Boolean {

        //
        // Always suffix runAfterSpeak to release any audio focus acquired in onUtteranceStart.
        //
        @Suppress("NAME_SHADOWING")
        val runAfter = if (runAfter == null) {
            runAfterSpeak
        } else {
            Runnables(runAfterSpeak, runAfter)
        }

        var anySuccess = false
        if (builder != null) {

            val parts = builder.build(ensureEndsWithSilence = true)
            //FooLog.e(TAG, "speak: parts(${parts.size})=$parts")

            val last = parts.size - 1
            for ((i, part) in parts.withIndex()) {
                anySuccess = anySuccess or speakInternal(
                    part,
                    // TODO: Confirm this code works when passed in an empty builder.
                    //  In that case appendSilenceSentenceBreak() adds one part.
                    //  Meaning, does this code logic work when there is only one part?
                    if (i == 0) clear else null,
                    if (i == last) runAfter else null
                )
            }
        } else {
            anySuccess = true
        }
        if (!anySuccess) {
            runAfter.run()
        }
        return anySuccess
    }

    /**
     * Only ever called by private [speak]`(clear: Boolean, builder: FooTextToSpeechBuilder?, runAfter: Runnable?)`
     * as part of [FooTextToSpeechBuilder.build] sequence that ALWAYS calls [FooTextToSpeechBuilder.appendSilenceSentenceBreak].
     *
     * @param part FooTextToSpeechPart of the calling sequence
     * @param clear true or false if the first part of the calling sequence, otherwise null
     * @param runAfter Runnable to call if the last part of the calling sequence, otherwise null
     */
    private fun speakInternal(part: FooTextToSpeechPart, clear: Boolean?, runAfter: Runnable?): Boolean {
        when (part) {
            is FooTextToSpeechPartSpeech -> {
                val text = part.text
                return if (clear != null) {
                    // Keep runAfter reserved for the trailing silence so audio-focus cleanup fires after the full sequence.
                    speakInternal(clear, text, null)
                } else {
                    speakInternal(false, text, runAfter)
                }
            }
            is FooTextToSpeechPartSilence -> {
                    val durationMillis = part.durationMillis
                    return if (clear != null) {
                        // Keep runAfter reserved for the trailing silence so audio-focus cleanup fires after the full sequence.
                        silence(clear, durationMillis, null)
                    } else {
                        silence(false, durationMillis, runAfter)
                    }
                }
            is FooTextToSpeechPartEarcon -> {
                val earcon = part.earcon
                return if (clear != null) {
                    // Keep runAfter reserved for the trailing silence so audio-focus cleanup fires after the full sequence.
                    playEarcon(clear, earcon, null)
                } else {
                    playEarcon(false, earcon, runAfter)
                }
            }
            else -> throw IllegalArgumentException("Unhandled part type ${part.javaClass}")
        }
    }

    /**
     * Only ever called by private [speakInternal]`(part: FooTextToSpeechPart, clear: Boolean?, runAfter: Runnable?)`
     *
     * @return true if isInitialized == false (in which case the text is enqueued) or if tts.speak(...) == TextToSpeech.SUCCESS, otherwise false
     */
    private fun speakInternal(clear: Boolean, text: String, runAfter: Runnable?): Boolean {
        if (VERBOSE_LOG_SPEAK) {
            FooLog.d(TAG, "#TTS_SPEAK +speakInternal(clear=$clear, text=${FooString.quote(text)}, runAfter=$runAfter)")
        }
        var success = false
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            if (isInitialized) {
                if (!audioFocusAcquireTry(audioAttributes)) {
                    // Being denied an audio focus request poses an interesting dilemma.
                    // Something else has exclusive audio focus; The right thing to do is to either queue this utterance or drop it [and call runAfter]...
                    //FooLog.w(TAG, "speak: audio focus denied; skipping utteranceId=${FooString.quote(utteranceId)}")
                    //runAfter?.run()
                    //return false
                    // BUT FOR NOW we will do neither and INTENTIONALLY CONTINUE to speak even if audio focus is denied!
                    // If this causes any usability problem (ex: TTS speaking while we are in a call) then it should be obvious and we can address it then.
                    // If it is never obvious then there is nothing to fix here and we are already doing the right thing.
                    FooLog.w(TAG, "#TTS_AUDIO_FOCUS speakInternal: audio focus denied; speaking anyway; TODO: fix any usability problem this exposes")
                }

                val queueMode = if (clear) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

                val params = Bundle()
                /** NOTE: Setting KEY_PARAM_UTTERANCE_ID is deprecated by calling [android.speech.tts.TextToSpeech.speak] with utteranceId */
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeRelativeToAudioStream)
                // TODO: KEY_PARAM_STREAM and KEY_PARAM_SESSION_ID
                // TODO: KEY_PARAM_PAN?

                val utteranceId = "${nextUtteranceId}_text"
                if (runAfter != null) {
                    utteranceCallbacks[utteranceId] = runAfter
                }

                if (VERBOSE_LOG_UTTERANCE) {
                    FooLog.d(TAG, "#TTS_UTTERANCE speakInternal: tts.speak(utteranceId=${FooString.quote(utteranceId)}, queueMode=${queueModeToString(queueMode)}, params=${FooPlatformUtils.toString(params)}, text=${FooString.quote(text)})")
                }
                val result = tts!!.speak(
                    text,
                    queueMode,
                    params,
                    utteranceId
                )
                if (result == TextToSpeech.SUCCESS) {
                    nextUtteranceId++
                    success = true
                } else {
                    utteranceCallbacks.remove(utteranceId)
                    runAfter?.run()
                }
            } else {
                if (VERBOSE_LOG_INIT_QUEUE) {
                    FooLog.d(TAG, "#TTS_INIT speakInternal: isInitialized == false; enqueuing")
                }
                val utteranceInfo = UtteranceText(text, runAfter)
                initQueue.add(utteranceInfo)
                success = true
            }
        }
        if (VERBOSE_LOG_SPEAK) {
            FooLog.d(TAG, "#TTS_SPEAK -speakInternal(text=${FooString.quote(text)}, clear=$clear, runAfter=$runAfter)")
        }
        return success
    }

    @JvmOverloads
    fun silence(clear: Boolean, durationMillis: Int, runAfter: Runnable? = null): Boolean {
        if (VERBOSE_LOG_SILENCE) {
            FooLog.d(TAG, "#TTS_SILENCE +silence(clear=$clear, durationMillis=$durationMillis, runAfter=$runAfter)")
        }
        var success = false
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            if (isInitialized) {
                val durationMillis = durationMillis.toLong()
                val queueMode = if (clear) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utteranceId = "${nextUtteranceId}_silence"
                if (runAfter != null) {
                    utteranceCallbacks[utteranceId] = runAfter
                }

                if (VERBOSE_LOG_UTTERANCE) {
                    FooLog.i(TAG, "#TTS_UTTERANCE silence: tts.playSilentUtterance(utteranceId=${FooString.quote(utteranceId)}, queueMode=${queueModeToString(queueMode)}, durationMillis=$durationMillis)")
                }
                val result = tts!!.playSilentUtterance(
                    durationMillis,
                    queueMode,
                    utteranceId
                )
                if (result == TextToSpeech.SUCCESS) {
                    nextUtteranceId++
                    success = true
                } else {
                    utteranceCallbacks.remove(utteranceId)
                    runAfter?.run()
                }
            } else {
                if (VERBOSE_LOG_INIT_QUEUE) {
                    FooLog.d(TAG, "#TTS_INIT silence: isInitialized == false; enqueuing")
                }
                val utteranceInfo = UtteranceSilence(durationMillis, runAfter)
                initQueue.add(utteranceInfo)
                success = true
            }
        }
        if (VERBOSE_LOG_SILENCE) {
            FooLog.d(TAG, "#TTS_SILENCE -silence(clear=$clear, durationMillis=$durationMillis, runAfter=$runAfter)")
        }
        return success
    }

    /**
     * @see [android.speech.tts.TextToSpeech.addEarcon]
     */
    @Suppress("unused")
    fun addEarcon(earcon: String, packageName: String, resourceId: Int) {
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            tts!!.addEarcon(earcon, packageName, resourceId)
        }
    }

    /**
     * @see [android.speech.tts.TextToSpeech.addEarcon]
     */
    @Suppress("unused")
    fun addEarcon(earcon: String, file: File) {
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            tts!!.addEarcon(earcon, file)
        }
    }

    /**
     * @see [android.speech.tts.TextToSpeech.addEarcon]
     */
    @Suppress("unused")
    fun addEarcon(earcon: String, uri: Uri) {
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            tts!!.addEarcon(earcon, uri)
        }
    }

    /**
     * @see [android.speech.tts.TextToSpeech.playEarcon]
     */
    fun playEarcon(clear: Boolean, earcon: String, runAfter: Runnable? = null): Boolean {
        if (VERBOSE_LOG_EARCON) {
            FooLog.d(TAG, "#TTS_EARCON +playEarcon(clear=$clear, earcon=${FooString.quote(earcon)}, runAfter=$runAfter)")
        }
        var success = false
        synchronized(syncLock) {
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            if (isInitialized) {
                if (!audioFocusAcquireTry(audioAttributes)) {
                    // Being denied an audio focus request poses an interesting dilemma.
                    // Something else has exclusive audio focus; The right thing to do is to either queue this utterance or drop it [and call runAfter]...
                    //FooLog.w(TAG, "speak: audio focus denied; skipping utteranceId=${FooString.quote(utteranceId)}")
                    //runAfter?.run()
                    //return false
                    // BUT FOR NOW we will do neither and INTENTIONALLY CONTINUE to speak even if audio focus is denied!
                    // If this causes any usability problem (ex: TTS speaking while we are in a call) then it should be obvious and we can address it then.
                    // If it is never obvious then there is nothing to fix here and we are already doing the right thing.
                    FooLog.w(TAG, "#TTS_AUDIO_FOCUS playEarcon: audio focus denied; speaking anyway; TODO: fix any usability problem this exposes")
                }

                val queueMode = if (clear) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

                val params = Bundle()
                /** NOTE: Setting KEY_PARAM_UTTERANCE_ID is deprecated by calling [android.speech.tts.TextToSpeech.speak] with utteranceId */
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeRelativeToAudioStream)
                // TODO: KEY_PARAM_STREAM and KEY_PARAM_SESSION_ID
                // TODO: KEY_PARAM_PAN?

                val utteranceId = "${nextUtteranceId}_earcon"
                if (runAfter != null) {
                    utteranceCallbacks[utteranceId] = runAfter
                }

                if (VERBOSE_LOG_UTTERANCE) {
                    FooLog.i(TAG, "#TTS_UTTERANCE playEarcon: tts.playEarcon(utteranceId=${FooString.quote(utteranceId)}, queueMode=${queueModeToString(queueMode)}, params=${FooPlatformUtils.toString(params)}, earcon=${FooString.quote(earcon)})")
                }
                val result = tts!!.playEarcon(earcon, queueMode, params, utteranceId)
                if (result == TextToSpeech.SUCCESS) {
                    nextUtteranceId++
                    success = true
                } else {
                    utteranceCallbacks.remove(utteranceId)
                    runAfter?.run()
                }
            } else {
                if (VERBOSE_LOG_INIT_QUEUE) {
                    FooLog.d(TAG, "#TTS_INIT playEarcon: isInitialized == false; enqueuing")
                }
                val utteranceInfo = UtteranceEarcon(earcon, runAfter)
                initQueue.add(utteranceInfo)
                success = true
            }
        }
        if (VERBOSE_LOG_EARCON) {
            FooLog.d(TAG, "#TTS_EARCON -playEarcon(clear=$clear, earcon=${FooString.quote(earcon)}, runAfter=$runAfter)")
        }
        return success
    }
}
