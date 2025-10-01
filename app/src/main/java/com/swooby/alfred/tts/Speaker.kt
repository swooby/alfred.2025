package com.swooby.alfred.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.LinkedList
import java.util.Locale

interface Speaker {
    suspend fun speak(text: String)
    fun shutdown()
}

class SpeakerImpl(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tts = TextToSpeech(app, this)
    private val syncLock = Any()
    private val speechQueue = LinkedList<String>()
    private val audioFocusRequests = mutableMapOf<String, AudioFocusRequest>()
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
                
                // Set utterance progress listener once during initialization
                tts.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener(){
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id ->
                            synchronized(syncLock) {
                                audioFocusRequests.remove(id)?.let { afr ->
                                    am.abandonAudioFocusRequest(afr)
                                }
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id ->
                            synchronized(syncLock) {
                                audioFocusRequests.remove(id)?.let { afr ->
                                    am.abandonAudioFocusRequest(afr)
                                }
                            }
                        }
                    }
                })
            }
            
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
    }

    private fun speakInternal(text: String) {
        val utteranceId = "alfred-" + System.nanoTime()
        val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            ).build()
        am.requestAudioFocus(afr)
        
        synchronized(syncLock) {
            audioFocusRequests[utteranceId] = afr
        }
        
        try {
            val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                synchronized(syncLock) {
                    audioFocusRequests.remove(utteranceId)
                }
                am.abandonAudioFocusRequest(afr)
            }
        } catch (e: Exception) {
            synchronized(syncLock) {
                audioFocusRequests.remove(utteranceId)
            }
            am.abandonAudioFocusRequest(afr)
        }
    }

    override fun shutdown() {
        synchronized(syncLock) {
            speechQueue.clear()
            audioFocusRequests.values.forEach { afr ->
                am.abandonAudioFocusRequest(afr)
            }
            audioFocusRequests.clear()
            tts.shutdown()
        }
    }
}