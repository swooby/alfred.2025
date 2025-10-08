package com.smartfoo.android.core.texttospeech

import android.app.Activity
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils

/**
 * From:
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/speech/tts/TtsEngines.java
 */
object FooTextToSpeechHelper {
    private val TAG = FooLog.TAG(FooTextToSpeechHelper::class.java)

    const val SETTINGS_ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

    @JvmStatic
    val intentTextToSpeechSettings: Intent
        get() = Intent().setAction(SETTINGS_ACTION_TTS_SETTINGS)

    @JvmStatic
    fun showTextToSpeechSettings(activity: Activity) {
        FooPlatformUtils.startActivity(activity, intentTextToSpeechSettings)
    }

    val intentRequestTextToSpeechData: Intent
        get() = Intent().setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)

    /**
     * The Activity should implement [Activity.onActivityResult] similar to:
     * <pre>
     * @Override
     * protected fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
     *   super.onActivityResult(requestCode, resultCode, data);
     *   when (requestCode) {
     *     REQUEST_ACTION_CHECK_TTS_DATA -> {
     *       when (resultCode) {
     *         TextToSpeech.Engine.CHECK_VOICE_DATA_PASS -> {
     *           val availableVoices = data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
     *           FooLog.d(TAG, "onActivityResult: availableVoices=" + availableVoices);
     *           val spinnerVoicesAdapter = ArrayAdapter(this, R.layout.simple_spinner_dropdown_item, availableVoices);
     *           mSpinnerVoices.setAdapter(spinnerVoicesAdapter);
     *         }
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param activity    activity
     * @param requestCode requestCode
     */
    @JvmStatic
    fun requestTextToSpeechData(activity: Activity, requestCode: Int) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        val intent = intentRequestTextToSpeechData
        val signature = "activity.startActivityForResult(intent=${FooPlatformUtils.toString(intent)}, requestCode=$requestCode)"
        FooLog.v(TAG, "requestTextToSpeechData: +$signature")
        activity.startActivityForResult(intent, requestCode)
        FooLog.v(TAG, "requestTextToSpeechData: -$signature")
    }
}
