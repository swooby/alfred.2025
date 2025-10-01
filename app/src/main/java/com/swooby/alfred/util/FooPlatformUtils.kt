package com.swooby.alfred.util

import android.content.Intent
import android.os.Bundle
import java.util.Locale

object FooPlatformUtils {
    fun toString(intent: Intent?): String {
        if (intent == null) {
            return "null"
        }

        val sb = StringBuilder()

        sb.append(intent)

        val bundle = intent.extras
        sb.append(", extras=").append(toString(bundle))

        return sb.toString()
    }

    fun toString(bundle: Bundle?): String {
        if (bundle == null) {
            return "null"
        }

        val sb = StringBuilder()

        val keys = bundle.keySet()
        val it: Iterator<String> = keys.iterator()

        sb.append('{')
        while (it.hasNext()) {
            val key = it.next()
            var value: Any?
            value = try {
                /**
                 * [android.os.BaseBundle.get] calls hidden method [android.os.BaseBundle.getValue].
                 * `android.os.BaseBundle#getValue(java.lang.String)` says:
                 * "Deprecated: Use `getValue(String, Class, Class[])`. This method should only be used in other deprecated APIs."
                 * That first sentence does not help this method that dynamically enumerates the Bundle entries without awareness/concern of any types.
                 * That second sentence tells me they probably won't be getting rid of android.os.BaseBundle#get(java.lang.String) any time soon.
                 * So marking deprecated `android.os.BaseBundle#get(java.lang.String)` as safe to call... for awhile.
                 */
                @Suppress("DEPRECATION")
                bundle.get(key)
            } catch (e: RuntimeException) {
                // Known issue if a Bundle (Parcelable) incorrectly implements writeToParcel
                "[Error retrieving \"" + key + "\" value: " + e.message + "]"
            }

            sb.append(FooString.quote(key)).append('=')

            if (key.lowercase(Locale.getDefault()).contains("password")) {
                value = "*CENSORED*"
            }

            if (value is Bundle) {
                sb.append(toString(value))
            } else if (value is Intent) {
                sb.append(toString(value))
            } else {
                sb.append(FooString.quote(value))
            }

            if (it.hasNext()) {
                sb.append(", ")
            }
        }
        sb.append('}')

        return sb.toString()
    }
}
