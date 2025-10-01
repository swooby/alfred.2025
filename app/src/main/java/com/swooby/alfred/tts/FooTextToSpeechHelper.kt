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

    private const val DBG = false

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

    /**
     * Locale delimiter used by the old-style 3 char locale string format (like "eng-usa")
     */
    private const val LOCALE_DELIMITER_OLD = "-"

    /**
     * Locale delimiter used by the new-style locale string format (Locale.toString() results,
     * like "en_US")
     */
    private const val LOCALE_DELIMITER_NEW = "_"

    /**
     * Mapping of various language strings to the normalized Locale form
     */
    private val sNormalizeLanguage: Map<String, String>

    /**
     * Mapping of various country strings to the normalized Locale form
     */
    private val sNormalizeCountry: Map<String, String>

    init {
        val normalizeLanguage = HashMap<String, String>()
        for (language in Locale.getISOLanguages()) {
            try {
                normalizeLanguage[Locale(language).isO3Language] = language
            } catch (e: MissingResourceException) {
                continue
            }
        }
        sNormalizeLanguage = Collections.unmodifiableMap(normalizeLanguage)
        val normalizeCountry = HashMap<String, String>()
        for (country in Locale.getISOCountries()) {
            try {
                normalizeCountry[Locale("", country).isO3Country] = country
            } catch (e: MissingResourceException) {
                continue
            }
        }
        sNormalizeCountry = Collections.unmodifiableMap(normalizeCountry)
    }

    fun parseLocaleString(localeString: String): Locale? {
        var language = ""
        var country = ""
        var variant = ""
        if (!TextUtils.isEmpty(localeString)) {
            val split =
                localeString.split(("[$LOCALE_DELIMITER_OLD$LOCALE_DELIMITER_NEW]").toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            language = split[0].lowercase(Locale.getDefault())
            if (split.isEmpty()) {
                FooLog.w(
                    TAG,
                    "Failed to convert $localeString to a valid Locale object. Only separators"
                )
                return null
            }
            if (split.size > 3) {
                FooLog.w(
                    TAG,
                    "Failed to convert $localeString to a valid Locale object. Too many separators"
                )
                return null
            }
            if (split.size >= 2) {
                country = split[1].uppercase(Locale.getDefault())
            }
            if (split.size >= 3) {
                variant = split[2]
            }
        }
        val normalizedLanguage = sNormalizeLanguage[language]
        if (normalizedLanguage != null) {
            language = normalizedLanguage
        }
        val normalizedCountry = sNormalizeCountry[country]
        if (normalizedCountry != null) {
            country = normalizedCountry
        }
        if (DBG) {
            FooLog.d(TAG, "parseLocaleString($language,$country,$variant)")
        }
        val result = Locale(language, country, variant)
        return try {
            result.isO3Language
            result.isO3Country
            result
        } catch (e: MissingResourceException) {
            FooLog.w(TAG, "Failed to convert $localeString to a valid Locale object.")
            null
        }
    }
}
