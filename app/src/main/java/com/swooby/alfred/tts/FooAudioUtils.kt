package com.swooby.alfred.tts

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.swooby.alfred.R
import com.swooby.alfred.util.FooString
import com.swooby.alfred.util.FooString.toString
import kotlin.math.roundToInt

@Suppress("unused")
object FooAudioUtils {
    val audioStreamTypes: IntArray

    init {
        audioStreamTypes = intArrayOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION
        )
    }

    fun audioStreamTypeToString(audioStreamType: Int): String {
        return audioStreamTypeToString(null, audioStreamType)
    }

    fun audioStreamTypeToString(context: Context?, audioStreamType: Int): String {
        val s = when (audioStreamType) {
            AudioManager.STREAM_VOICE_CALL -> if (context != null) context.getString(R.string.audio_stream_voice_call) else "STREAM_VOICE_CALL"
            AudioManager.STREAM_SYSTEM -> if (context != null) context.getString(R.string.audio_stream_system) else "STREAM_SYSTEM"
            AudioManager.STREAM_RING -> if (context != null) context.getString(R.string.audio_stream_ring) else "STREAM_RING"
            AudioManager.STREAM_MUSIC -> if (context != null) context.getString(R.string.audio_stream_media) else "STREAM_MUSIC"
            AudioManager.STREAM_ALARM -> if (context != null) context.getString(R.string.audio_stream_alarm) else "STREAM_ALARM"
            AudioManager.STREAM_NOTIFICATION -> if (context != null) context.getString(R.string.audio_stream_notification) else "STREAM_NOTIFICATION"
            6 -> if (context != null) context.getString(R.string.audio_stream_bluetooth_sco) else "STREAM_BLUETOOTH_SCO"
            7 -> if (context != null) context.getString(R.string.audio_stream_system_enforced) else "STREAM_SYSTEM_ENFORCED"
            AudioManager.STREAM_DTMF -> if (context != null) context.getString(R.string.audio_stream_dtmf) else "STREAM_DTMF"
            9 -> if (context != null) context.getString(R.string.audio_stream_text_to_speech) else "STREAM_TTS"
            else -> if (context != null) context.getString(R.string.audio_stream_unknown) else "STREAM_UNKNOWN"
        }
        return if (context != null) s else "$s($audioStreamType)"
    }

    fun audioFocusToString(audioFocus: Int): String {
        val s = when (audioFocus) {
            0 -> "AUDIOFOCUS_NONE"
            AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
            AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
            else -> "UNKNOWN"
        }
        return "$s($audioFocus)"
    }

    fun audioFocusRequestToString(audioFocus: Int): String {
        val s = when (audioFocus) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "AUDIOFOCUS_REQUEST_FAILED"
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "AUDIOFOCUS_REQUEST_GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "AUDIOFOCUS_REQUEST_DELAYED"
            else -> "UNKNOWN"
        }
        return "$s($audioFocus)"
    }

    fun getVolumePercentFromAbsolute(
        audioManager: AudioManager,
        audioStreamType: Int,
        volume: Int
    ): Float {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return volume / volumeMax.toFloat()
    }

    fun getVolumeAbsoluteFromPercent(
        audioManager: AudioManager,
        audioStreamType: Int,
        volumePercent: Float
    ): Int {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return (volumeMax * volumePercent).roundToInt()
    }

    fun getVolumeAbsolute(audioManager: AudioManager, audioStreamType: Int): Int {
        return audioManager.getStreamVolume(audioStreamType)
    }

    fun getVolumePercent(audioManager: AudioManager, audioStreamType: Int): Float {
        val volume = getVolumeAbsolute(audioManager, audioStreamType)
        return getVolumePercentFromAbsolute(audioManager, audioStreamType, volume)
    }

    fun getRingtone(context: Context?, ringtoneUri: Uri?): Ringtone? {
        if (FooString.isNullOrEmpty(toString(ringtoneUri))) {
            return null
        }

        return RingtoneManager.getRingtone(context, ringtoneUri)
    }
}
