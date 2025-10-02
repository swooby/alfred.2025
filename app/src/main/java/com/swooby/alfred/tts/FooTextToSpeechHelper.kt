package com.swooby.alfred.tts

import android.app.Activity
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import com.swooby.alfred.util.FooLog
import com.swooby.alfred.util.FooPlatformUtils
import java.util.Collections
import java.util.Locale
import java.util.MissingResourceException

/**
 * From:
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/speech/tts/TtsEngines.java
 */
object FooTextToSpeechHelper {
    private val TAG = FooLog.TAG(FooTextToSpeechHelper::class.java)

    const val SETTINGS_ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

    @JvmStatic
    val IntentTextToSpeechSettings: Intent
        get() = Intent().setAction(SETTINGS_ACTION_TTS_SETTINGS)

    @JvmStatic
    val IntentRequestTextToSpeechData: Intent
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
        val intent = IntentRequestTextToSpeechData
        val signature =
            "activity.startActivityForResult(intent=" + FooPlatformUtils.toString(intent) +
                    ", requestCode=" + requestCode + ')'
        FooLog.v(TAG, "requestTextToSpeechData: +$signature")
        activity.startActivityForResult(intent, requestCode)
        FooLog.v(TAG, "requestTextToSpeechData: -$signature")
    }
}
