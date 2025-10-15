package com.swooby.alfred.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swooby.alfred.core.rules.QuietHours
import com.swooby.alfred.core.rules.RateLimit
import com.swooby.alfred.core.rules.RulesConfig
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

private val Context.settingsDataStore by preferencesDataStore("alfred_settings")

class SettingsRepository(
    private val app: Context,
) {
    private object K {
        val PERSISTENT_NOTIFICATION_ACTION_IGNORED = booleanPreferencesKey("persistent_notification_action_ignored")
        val SPEAK_SCREEN_OFF_ONLY = booleanPreferencesKey("speak_screen_off_only")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")
        val DISABLED_APPS = stringPreferencesKey("disabled_apps_csv")
        val ENABLED_TYPES = stringPreferencesKey("enabled_types_csv")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_SEED = stringPreferencesKey("theme_seed_argb")
    }

    private val defaultEnabled =
        setOf(
            SourceEventTypes.MEDIA_START,
            SourceEventTypes.MEDIA_STOP,
            SourceEventTypes.NOTIFICATION_POST,
            SourceEventTypes.DISPLAY_ON,
            SourceEventTypes.DISPLAY_OFF,
            SourceEventTypes.DEVICE_UNLOCK,
            SourceEventTypes.DEVICE_BOOT,
            SourceEventTypes.DEVICE_SHUTDOWN,
            SourceEventTypes.POWER_CONNECTED,
            SourceEventTypes.POWER_DISCONNECTED,
            SourceEventTypes.POWER_CHARGING_STATUS,
            SourceEventTypes.NETWORK_WIFI_CONNECT,
            SourceEventTypes.NETWORK_WIFI_DISCONNECT,
        )

    val rulesConfigFlow: Flow<RulesConfig> =
        app.settingsDataStore.data.map { p ->
            val speakOff = p[K.SPEAK_SCREEN_OFF_ONLY] ?: false
            val qs = (p[K.QUIET_START] ?: "").trim()
            val qe = (p[K.QUIET_END] ?: "").trim()
            val quiet = if (qs.isNotEmpty() && qe.isNotEmpty()) QuietHours(LocalTime.parse(qs), LocalTime.parse(qe)) else null
            val disabledApps = (p[K.DISABLED_APPS] ?: "").split(',').mapNotNull { it.trim().ifEmpty { null } }.toSet()
            val enabledTypes =
                (
                    p[K.ENABLED_TYPES] ?: defaultEnabled.joinToString(
                        ",",
                    )
                ).split(',').mapNotNull { it.trim().ifEmpty { null } }.toSet()
            RulesConfig(
                enabledTypes,
                disabledApps,
                quiet,
                speakOff,
                listOf(
                    RateLimit(SourceEventTypes.MEDIA_START, perSeconds = 30, maxEvents = 4),
                    RateLimit(SourceEventTypes.NOTIFICATION_POST, perSeconds = 10, maxEvents = 6),
                ),
            )
        }

    val themePreferencesFlow: Flow<ThemePreferences> =
        app.settingsDataStore.data.map { preferences ->
            ThemePreferences(
                mode = ThemeMode.fromPreference(preferences[K.THEME_MODE]),
                seedArgb = preferences[K.THEME_SEED].toArgbOrNull(),
            )
        }

    //val themeModeFlow: Flow<ThemeMode> = themePreferencesFlow.map { it.mode }

    val persistentNotificationActionIgnoredFlow: Flow<Boolean> =
        app.settingsDataStore.data.map { it[K.PERSISTENT_NOTIFICATION_ACTION_IGNORED] ?: false }

    suspend fun setPersistentNotificationActionIgnored(ignored: Boolean) {
        app.settingsDataStore.edit { it[K.PERSISTENT_NOTIFICATION_ACTION_IGNORED] = ignored }
    }

    suspend fun setQuietHours(
        startHHmm: String?,
        endHHmm: String?,
    ) {
        app.settingsDataStore.edit { e ->
            e[K.QUIET_START] = startHHmm ?: ""
            e[K.QUIET_END] = endHHmm ?: ""
        }
    }

    suspend fun setSpeakWhenScreenOffOnly(enabled: Boolean) {
        app.settingsDataStore.edit { it[K.SPEAK_SCREEN_OFF_ONLY] = enabled }
    }

    suspend fun setDisabledAppsCsv(csv: String) {
        app.settingsDataStore.edit { it[K.DISABLED_APPS] = csv }
    }

    suspend fun setEnabledTypesCsv(csv: String) {
        app.settingsDataStore.edit { it[K.ENABLED_TYPES] = csv }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        app.settingsDataStore.edit { it[K.THEME_MODE] = themeMode.asPreferenceString() }
    }

    suspend fun setCustomThemeSeed(argb: Long?) {
        app.settingsDataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(K.THEME_SEED)
            } else {
                prefs[K.THEME_SEED] = argb.toArgbHexString()
            }
        }
    }
}

private fun String?.toArgbOrNull(): Long? {
    val raw = this?.trim()?.removePrefix("#") ?: return null
    val parsed = raw.toLongOrNull(16) ?: return null
    return if (raw.length <= 6) {
        0xFF000000L or parsed
    } else {
        parsed
    }
}

private fun Long.toArgbHexString(): String {
    val normalized =
        if (this and 0xFF000000L == 0L) {
            this or 0xFF000000L
        } else {
            this
        }
    return "#${"%08X".format(normalized)}"
}
