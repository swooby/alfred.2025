package com.swooby.alfred2017.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swooby.alfred2017.core.rules.QuietHours
import com.swooby.alfred2017.core.rules.RateLimit
import com.swooby.alfred2017.core.rules.RulesConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

private val Context.settingsDataStore by preferencesDataStore("alfred_settings")

class SettingsRepository(private val app: Context) {
    private object K {
        val SPEAK_SCREEN_OFF_ONLY = booleanPreferencesKey("speak_screen_off_only")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END   = stringPreferencesKey("quiet_end")
        val DISABLED_APPS = stringPreferencesKey("disabled_apps_csv")
        val ENABLED_TYPES = stringPreferencesKey("enabled_types_csv")
    }
    private val defaultEnabled = setOf("media.start","media.stop","notif.post","display.on","display.off","network.wifi.connect","network.wifi.disconnect")
    val rulesConfigFlow: Flow<RulesConfig> =
        app.settingsDataStore.data.map { p ->
            val speakOff = p[K.SPEAK_SCREEN_OFF_ONLY] ?: false
            val qs = (p[K.QUIET_START] ?: "").trim()
            val qe = (p[K.QUIET_END] ?: "").trim()
            val quiet = if (qs.isNotEmpty() && qe.isNotEmpty()) QuietHours(LocalTime.parse(qs), LocalTime.parse(qe)) else null
            val disabledApps = (p[K.DISABLED_APPS] ?: "").split(',').mapNotNull { it.trim().ifEmpty { null } }.toSet()
            val enabledTypes = (p[K.ENABLED_TYPES] ?: defaultEnabled.joinToString(",")).split(',').mapNotNull { it.trim().ifEmpty { null } }.toSet()
            RulesConfig(enabledTypes, disabledApps, quiet, speakOff, listOf(RateLimit("media.start",30,4), RateLimit("notif.post",10,6)))
        }
    suspend fun setQuietHours(startHHmm: String?, endHHmm: String?) {
        app.settingsDataStore.edit { e -> e[K.QUIET_START] = startHHmm ?: ""; e[K.QUIET_END] = endHHmm ?: "" }
    }
    suspend fun setSpeakWhenScreenOffOnly(enabled: Boolean) { app.settingsDataStore.edit { it[K.SPEAK_SCREEN_OFF_ONLY] = enabled } }
    suspend fun setDisabledAppsCsv(csv: String) { app.settingsDataStore.edit { it[K.DISABLED_APPS] = csv } }
    suspend fun setEnabledTypesCsv(csv: String) { app.settingsDataStore.edit { it[K.ENABLED_TYPES] = csv } }
}