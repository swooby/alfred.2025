package com.swooby.alfred.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import java.util.Locale
import androidx.core.net.toUri

object FooPlatformUtils {
    fun toString(intent: Intent?): String {
        if (intent == null) return "null"
        val sb = StringBuilder()
        sb.append(intent) // only prints "(has extras)" for extras
        sb.append(", extras=").append(toString(intent.extras)) // show extras
        return sb.toString()
    }

    /**
     * May be unnecessary; [android.os.Bundle]`.toString` output seems almost acceptable nowadays.
     */
    fun toString(bundle: Bundle?): String {
        if (bundle == null) return "null"

        val sb = StringBuilder()

        val keys = bundle.keySet()
        val it = keys.iterator()

        sb.append('{')
        while (it.hasNext()) {
            val key = it.next()
            var value = try {
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
                value = "*REDACTED*"
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

    @JvmOverloads
    fun startActivity(context: Context, intent: Intent, bundle: Bundle? = null) {
        /*
        if (context is Application) {
            // TODO Use Application.ActivityLifecycleCallbacks (like in AlfredAI) to actually test for background or not
            /*
            // Background startActivity requires FLAG_ACTIVITY_NEW_TASK
            if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0)
            {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            */
        }
        */
        context.startActivity(intent, bundle)
    }

    /**
     *
     *
     * I originally just wanted to be able to change the System Development Debug Layout property.
     * I thought that I could duplicate what com.android.settings.DevelopmentSettings does:
     * https://cs.android.com/android/platform/superproject/+/android-7.1.2_r39:packages/apps/Settings/src/com/android/settings/DevelopmentSettings.java
     * Currently (2025/03) that code has moved to under:
     * https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Settings/src/com/android/settings/development/
     *
     * ie: Use Reflection to set the SystemProperty and then pokeSystemProperties
     *
     *
     *
     * After several hours of work I learned that the SystemProperties are ACL protected to only allow the Google
     * Signed Settings app to change them.
     * http://stackoverflow.com/a/11136242 -&gt; http://stackoverflow.com/a/11123609/252308
     *
     *
     *
     * Rather than continue to try to get this to work (if it is even possible),
     * I have chosen to just launch the SettingsActivity DevelopmentSettings fragment.
     *
     *
     *
     * Other references for my wasted efforts:
     * https://github.com/android/platform_packages_apps_settings/blob/master/src/com/android/settings/DevelopmentSettings.java#L1588
     * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/SystemProperties.java#L122
     * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/view/View.java#L706
     * https://github.com/Androguide/CMDProcessorLibrary/blob/master/CMDProcessorLibrary/src/com/androguide/cmdprocessor/SystemPropertiesReflection.java
     *
     *
     * @param context context
     */
    fun showDevelopmentSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // DevelopmentSettings appears to not have any arguments. :(
        // https://github.com/android/platform_packages_apps_settings/blob/master/src/com/android/settings/DevelopmentSettings.java
        startActivity(context, intent)
    }

    /**
     * This has a sometimes annoying side-effect of Back not being able to exit the Activity.
     * Even Android's built in Notification Tile "Wireless debug settings" exhibits the same problem.
     * When this happens just do what you always do to close an Activity:
     *   swipe up from the bottom and then swipe the Activity up to close it.
     *
     * https://stackoverflow.com/a/74859391/252308
     *
     * https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Settings/src/com/android/settings/development/AdbWirelessDialog.java
     * https://cs.android.com/android/platform/superproject/+/android-14.0.0_r61:packages/apps/Settings/src/com/android/settings/development/AdbWirelessDialog.java
     * https://cs.android.com/android/platform/superproject/+/android-14.0.0_r61:packages/apps/Settings/src/com/android/settings/development/AdbWirelessDialogController.java
     *
     * @param context
     */
    fun showAdbWirelessSettings(context: Context) {
        /*

        Hints of how Android Settings -> Development Settings does something similar:
        https://cs.android.com/android/platform/superproject/+/android-15.0.0_r23:packages/apps/Settings/AndroidManifest.xml?q=DevelopmentSettingsActivity

        ```
        <activity
            android:name="Settings$DevelopmentSettingsActivity"
            android:label="@string/development_settings_title"
            android:icon="@drawable/ic_settings_development"
            android:exported="true">
            <intent-filter android:priority="1">
                <action android:name="android.settings.APPLICATION_DEVELOPMENT_SETTINGS" />
                <action android:name="com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS" />
                <action android:name="android.service.quicksettings.action.QS_TILE_PREFERENCES"/>
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data android:name="com.android.settings.FRAGMENT_CLASS"
                       android:value="com.android.settings.development.DevelopmentSettingsDashboardFragment" />
            <meta-data android:name="com.android.settings.HIGHLIGHT_MENU_KEY"
                       android:value="@string/menu_key_system"/>
            <meta-data android:name="com.android.settings.PRIMARY_PROFILE_CONTROLLED"
                       android:value="true" />
        </activity>
        ```

        `Settings$DevelopmentSettingsActivity` is a stub activity that
        https://cs.android.com/android/platform/superproject/+/android-15.0.0_r23:packages/apps/Settings/src/com/android/settings/Settings.java
        reads the meta-data to redirect to DevelopmentSettingsDashboardFragment:
        https://cs.android.com/android/platform/superproject/+/android-15.0.0_r23:packages/apps/Settings/src/com/android/settings/development/DevelopmentSettingsDashboardFragment.java

        */

        val pkg = "com.android.settings"
        val cls = "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
        val componentName = ComponentName(pkg, cls)

        // "android.service.quicksettings.action.QS_TILE_PREFERENCES"
        val intent = Intent(TileService.ACTION_QS_TILE_PREFERENCES)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
        startActivity(context, intent)
    }

    fun showGooglePlay(context: Context, packageName: String?) {
        try {
            val uri = "market://details?id=$packageName".toUri()
            startActivity(context, Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            val uri = "https://play.google.com/store/apps/details?id=$packageName".toUri()
            startActivity(context, Intent(Intent.ACTION_VIEW, uri))
        }
    }

    fun showAppSettings(context: Context, packageName: String?) {
        val uri = "package:$packageName".toUri()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        FooPlatformUtils.startActivity(context, intent)
    }

    fun showAppSettings(context: Context) {
        showAppSettings(context, context.packageName)
    }

    fun showBatterySettings(context: Context) {
        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        if (resolveInfo != null) {
            startActivity(context, intent)
        }
    }

    private fun intentAppNotificationSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }

    fun showAppNotificationSettings(ctx: Context) {
        startActivity(ctx, intentAppNotificationSettings(ctx))
    }
}
