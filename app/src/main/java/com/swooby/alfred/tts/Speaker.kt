package com.swooby.alfred.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.LinkedList
import java.util.Locale
import kotlin.coroutines.resume

interface Speaker {
    suspend fun speak(text: String)
    fun shutdown()
}

class SpeakerImpl(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tts = TextToSpeech(app, this)
    private val syncLock = Any()
    private val initDeferred = CompletableDeferred<Boolean>()
    private val speechQueue = LinkedList<String>()
    private var isInitialized = false

    override fun onInit(status: Int) {
        synchronized(syncLock) {
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                // Defer all settings to after initialization succeeds
                tts.language = Locale.getDefault()
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            initDeferred.complete(isInitialized)
            
            // Process queued speech requests
            if (isInitialized) {
                val iterator = speechQueue.iterator()
                while (iterator.hasNext()) {
                    val queuedText = iterator.next()
                    iterator.remove()
                    speakInternal(queuedText)
                }
            }
        }
    }

    suspend fun awaitInit(): Boolean = initDeferred.await()

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        
        synchronized(syncLock) {
            if (isInitialized) {
                speakInternal(text)
            } else {
                // Queue text if not yet initialized
                speechQueue.add(text)
            }
        }
        
        // Wait for initialization to complete
        initDeferred.await()
    }

    private fun speakInternal(text: String) {
        val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            ).build()
        am.requestAudioFocus(afr)
        try {
            val id = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "alfred-" + System.nanoTime())
            if (id != TextToSpeech.ERROR) {
                tts.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener(){
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { 
                        am.abandonAudioFocusRequest(afr)
                    }
                    override fun onError(utteranceId: String?) { 
                        am.abandonAudioFocusRequest(afr)
                    }
                })
            } else {
                am.abandonAudioFocusRequest(afr)
            }
        } catch (e: Exception) {
            am.abandonAudioFocusRequest(afr)
        }
    }

    override fun shutdown() {
        synchronized(syncLock) {
            speechQueue.clear()
            tts.shutdown()
        }
    }
}