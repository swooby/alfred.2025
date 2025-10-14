package com.smartfoo.android.core.media

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.R
import kotlin.math.roundToInt

@Suppress("unused")
object FooAudioUtils {
    private val TAG = FooLog.TAG(FooAudioUtils::class.java)

    @JvmStatic
    val audioStreamTypes =
        intArrayOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
        )

    @JvmStatic
    fun audioStreamTypeToString(audioStreamType: Int): String = audioStreamTypeToString(null, audioStreamType)

    @JvmStatic
    fun audioStreamTypeToString(
        context: Context?,
        audioStreamType: Int,
    ): String {
        @Suppress("KDocUnresolvedReference")
        val s =
            when (audioStreamType) {
                AudioManager.STREAM_VOICE_CALL -> context?.getString(R.string.audio_stream_voice_call) ?: "STREAM_VOICE_CALL"
                AudioManager.STREAM_SYSTEM -> context?.getString(R.string.audio_stream_system) ?: "STREAM_SYSTEM"
                AudioManager.STREAM_RING -> context?.getString(R.string.audio_stream_ring) ?: "STREAM_RING"
                AudioManager.STREAM_MUSIC -> context?.getString(R.string.audio_stream_media) ?: "STREAM_MUSIC"
                AudioManager.STREAM_ALARM -> context?.getString(R.string.audio_stream_alarm) ?: "STREAM_ALARM"
                AudioManager.STREAM_NOTIFICATION -> context?.getString(R.string.audio_stream_notification) ?: "STREAM_NOTIFICATION"
                /** [android.media.AudioManager.STREAM_BLUETOOTH_SCO] is hidden */
                6 -> context?.getString(R.string.audio_stream_bluetooth_sco) ?: "STREAM_BLUETOOTH_SCO"
                /** [android.media.AudioManager.STREAM_SYSTEM_ENFORCED] is hidden */
                7 -> context?.getString(R.string.audio_stream_system_enforced) ?: "STREAM_SYSTEM_ENFORCED"
                AudioManager.STREAM_DTMF -> context?.getString(R.string.audio_stream_dtmf) ?: "STREAM_DTMF"
                /** [android.media.AudioManager.STREAM_TTS] is hidden */
                9 -> context?.getString(R.string.audio_stream_text_to_speech) ?: "STREAM_TTS"
                else -> context?.getString(R.string.audio_stream_unknown) ?: "STREAM_UNKNOWN"
            }
        return if (context != null) s else "$s($audioStreamType)"
    }

    @JvmStatic
    fun audioFocusGainLossToString(audioFocusGainLoss: Int): String {
        val s =
            when (audioFocusGainLoss) {
                AudioManager.AUDIOFOCUS_NONE -> "AUDIOFOCUS_NONE"
                AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
                else -> "UNKNOWN"
            }
        return "$s($audioFocusGainLoss)"
    }

    @JvmStatic
    fun audioFocusRequestToString(audioFocusRequest: Int): String {
        val s =
            when (audioFocusRequest) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "AUDIOFOCUS_REQUEST_FAILED"
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "AUDIOFOCUS_REQUEST_GRANTED"
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "AUDIOFOCUS_REQUEST_DELAYED"
                else -> "UNKNOWN"
            }
        return "$s($audioFocusRequest)"
    }

    @JvmStatic
    fun getVolumePercentFromAbsolute(
        audioManager: AudioManager,
        audioStreamType: Int,
        volume: Int,
    ): Float {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return volume / volumeMax.toFloat()
    }

    @JvmStatic
    fun getVolumeAbsoluteFromPercent(
        audioManager: AudioManager,
        audioStreamType: Int,
        volumePercent: Float,
    ): Int {
        val volumeMax = audioManager.getStreamMaxVolume(audioStreamType)
        return (volumeMax * volumePercent).roundToInt()
    }

    @JvmStatic
    fun getVolumeAbsolute(
        audioManager: AudioManager,
        audioStreamType: Int,
    ): Int = audioManager.getStreamVolume(audioStreamType)

    @JvmStatic
    fun getVolumePercent(
        audioManager: AudioManager,
        audioStreamType: Int,
    ): Float {
        val volume = getVolumeAbsolute(audioManager, audioStreamType)
        return getVolumePercentFromAbsolute(audioManager, audioStreamType, volume)
    }

    @JvmStatic
    fun getRingtone(
        context: Context?,
        ringtoneUri: Uri?,
    ): Ringtone? {
        if (FooString.isNullOrEmpty(FooString.toString(ringtoneUri))) {
            return null
        }
        return RingtoneManager.getRingtone(context, ringtoneUri)
    }
}
