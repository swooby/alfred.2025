package com.swooby.alfred.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.swooby.alfred.tts.FooTextToSpeechBuilder.FooTextToSpeechPart
import com.swooby.alfred.tts.FooTextToSpeechBuilder.FooTextToSpeechPartSilence
import com.swooby.alfred.tts.FooTextToSpeechBuilder.FooTextToSpeechPartSpeech
import com.swooby.alfred.util.FooAudioFocusController
import com.swooby.alfred.util.FooAudioUtils
import com.swooby.alfred.util.FooListenerManager
import com.swooby.alfred.util.FooLog
import com.swooby.alfred.util.FooString

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
 */
class FooTextToSpeech {
    companion object {
        private val TAG = FooLog.TAG(FooTextToSpeech::class.java)

        var VERBOSE_LOG_SPEAK = true
        var VERBOSE_LOG_SILENCE = false
        var VERBOSE_LOG_UTTERANCE_IDS = false
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
                TextToSpeech.SUCCESS -> "SUCCESS($status)"
                TextToSpeech.ERROR -> "ERROR($status)"
                else -> "UNKNOWN($status)"
            }
        }
    }

    interface FooTextToSpeechCallbacks {
        fun onTextToSpeechInitialized(status: Int)
    }

    private inner class UtteranceInfo(val text: String?, val runAfter: Runnable?)

    private val syncLock = Any()
    private val listeners = FooListenerManager<FooTextToSpeechCallbacks>(TAG)
    private val speechQueue = mutableListOf<UtteranceInfo>()
    private val utteranceCallbacks = mutableMapOf<String, Runnable>()
    private val runAfterSpeak = Runnable { onRunAfterSpeak() }

    private val audioFocusController: FooAudioFocusController
    private val audioFocusControllerCallbacks: FooAudioFocusController.Callbacks
    @Volatile
    private var audioFocusControllerHandle: FooAudioFocusController.FocusHandle? = null

    /**
     * Audio‑focus acquisition strategy:
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
    private var nextUtteranceId = 0

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

    var voiceSpeed: Float = DEFAULT_VOICE_SPEED
        /**
         * @param value Speech rate. `1.0` is the normal speech rate,
         * lower values slow down the speech (`0.5` is half the normal speech rate),
         * greater values accelerate it (`2.0` is twice the normal speech rate).
         */
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var voicePitch: Float = DEFAULT_VOICE_PITCH
        /**
         * @param value Speech pitch. `1.0` is the normal pitch,
         * lower values lower the tone of the synthesized voice,
         * greater values increase it.
         */
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

    fun detach(callbacks: FooTextToSpeechCallbacks) {
        synchronized(syncLock) { listeners.detach(callbacks) }
    }

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
                            @SuppressWarnings("Deprecated")
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
                    val speechQueueSize = speechQueue.size
                    if (speechQueueSize > 0) {
                        FooLog.v(TAG, "#TTS onTextToSpeechInitialized: speaking speechQueue($speechQueueSize) items...")
                        val iterator = speechQueue.iterator()
                        var utteranceInfo: UtteranceInfo
                        while (iterator.hasNext()) {
                            utteranceInfo = iterator.next()
                            iterator.remove()
                            speak(false, utteranceInfo.text, utteranceInfo.runAfter)
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
        var runAfter: Runnable?
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
        var runAfter: Runnable?
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
            speechQueue.clear()

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
    fun speak(text: String?, runAfter: Runnable?): Boolean {
        return speak(false, text, runAfter)
    }

    @JvmOverloads
    fun speak(clear: Boolean, text: String?, runAfter: Runnable? = null): Boolean {
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

        //
        // Always suffix w/ 500ms so that there is a clear break before the next speech.
        //
        var anySuccess = false
        if (builder != null) {
            builder.appendSilenceSentenceBreak()
            val parts = builder.build()
            val last = parts.size - 1
            for ((i, part) in parts.withIndex()) {
                anySuccess = anySuccess or speak(
                    part,
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

    private fun speak(part: FooTextToSpeechPart, clear: Boolean?, runAfter: Runnable?): Boolean {
        if (part is FooTextToSpeechPartSpeech) {
            val text = part.mText
            return if (clear != null) {
                speakInternal(text, clear, null)
            } else {
                speakInternal(text, false, runAfter)
            }
        }
        if (part is FooTextToSpeechPartSilence) {
            val durationMillis = part.mSilenceDurationMillis
            return silence(durationMillis, runAfter)
        }
        throw IllegalArgumentException("Unhandled part type ${part.javaClass}")
    }

    private fun speakInternal(text: String?, clear: Boolean, runAfter: Runnable?): Boolean {
        var logPrefix = ""
        return try {
            var success = false
            synchronized(syncLock) {
                if (VERBOSE_LOG_SPEAK) {
                    if (!isInitialized) logPrefix = " *ENQUEUE*"
                    FooLog.d(TAG, "#TTS_SPEAK$logPrefix +speakInternal(text=${FooString.quote(text)}, clear=$clear, runAfter=$runAfter)")
                }
                if (tts == null) {
                    throw IllegalStateException("start(context) must be called first")
                }
                if (isInitialized) {
                    val utteranceId = "text_$nextUtteranceId"
                    val params = Bundle()

                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeRelativeToAudioStream)

                    if (VERBOSE_LOG_UTTERANCE_IDS) {
                        FooLog.v(TAG, "#TTS_UTTERANCE_IDS speakInternal: utteranceId=${FooString.quote(utteranceId)}, text=${FooString.quote(text)}")
                    }

                    if (!audioFocusAcquireTry(audioAttributes)) {
                        // Being denied an audio focus request poses an interesting dilemma.
                        // Something else has exclusive audio focus; The right thing to do is to either queue this utterance or drop it [and call runAfter].
                        // For now we will do neither and intentionally continue to speak even if audio focus is denied.
                        // If this causes any usability problem (ex: TTS speaking while we are in a call) then it will be obvious and we can address it then.
                        FooLog.w(TAG, "#TTS_SPEAK speakInternal: audio focus denied; speaking anyway; TODO: fix any usability problem this exposes")
                        /*
                        FooLog.w(TAG, "speakInternal: audio focus denied; skipping utteranceId=${FooString.quote(utteranceId)}")
                        runAfter?.run()
                        return false
                        */
                    }

                    if (runAfter != null) {
                        utteranceCallbacks[utteranceId] = runAfter
                    }
                    val result = tts!!.speak(
                        text,
                        if (clear) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
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
                    if (VERBOSE_LOG_SPEAK) {
                        FooLog.d(TAG, "#TTS_SPEAK speakInternal: isInitialized == false; enqueuing")
                    }
                    val utteranceInfo = UtteranceInfo(text, runAfter)
                    speechQueue.add(utteranceInfo)
                    success = true
                }
            }
            success
        } finally {
            if (VERBOSE_LOG_SPEAK) {
                FooLog.d(TAG, "#TTS_SPEAK$logPrefix -speakInternal(text=${FooString.quote(text)}, clear=$clear, runAfter=$runAfter)")
            }
        }
    }

    // TODO: Need to add clear parameter?
    @JvmOverloads
    fun silence(durationMillis: Int, runAfter: Runnable? = null): Boolean {
        var success = false
        synchronized(syncLock) {
            if (VERBOSE_LOG_SILENCE) {
                FooLog.d(TAG, "#TTS_SILENCE +silence(durationMillis=$durationMillis, runAfter=$runAfter)")
            }
            if (tts == null) {
                throw IllegalStateException("start(context) must be called first")
            }
            if (isInitialized) {
                val utteranceId = "silence_$nextUtteranceId"
                if (VERBOSE_LOG_UTTERANCE_IDS) {
                    FooLog.v(TAG, "#TTS_UTTERANCE_IDS silence: utteranceId=${FooString.quote(utteranceId)}, text=$durationMillis")
                }
                if (runAfter != null) {
                    utteranceCallbacks[utteranceId] = runAfter
                }
                val result = tts!!.playSilentUtterance(
                    durationMillis.toLong(),
                    TextToSpeech.QUEUE_ADD,
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
                FooLog.v(TAG, "#TTS_SILENCE silence: isInitialized == false; ignoring; TODO: need to enqueue?")
            }
        }
        if (VERBOSE_LOG_SILENCE) {
            FooLog.d(TAG, "#TTS_SPEAK -silence(durationMillis=$durationMillis, runAfter=$runAfter)")
        }
        return success
    }
}
