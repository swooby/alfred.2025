package com.swooby.alfred.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts.language = Locale.getDefault()
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    override suspend fun speak(text: String) {
        if (!ready || text.isBlank()) return
        val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            ).build()
        am.requestAudioFocus(afr)
        try {
            suspendCancellableCoroutine { cont ->
                val id = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "alfred-" + System.nanoTime())
                if (id == TextToSpeech.ERROR) cont.resume(Unit)
                tts.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener(){
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                })
            }
        } finally {
            am.abandonAudioFocusRequest(afr)
        }
    }

    override fun shutdown() { tts.shutdown() }
}