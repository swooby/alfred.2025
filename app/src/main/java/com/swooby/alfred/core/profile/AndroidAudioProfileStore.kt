package com.swooby.alfred.core.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.audioProfileDataStore by preferencesDataStore("audio_profile_preferences")

class AndroidAudioProfileStore(
    private val context: Context
) : AudioProfileStore {

    private object Keys {
        val SELECTED_PROFILE = stringPreferencesKey("selected_audio_profile")
        val SELECTED_PROFILE_NAME = stringPreferencesKey("selected_audio_profile_name")
        val SELECTED_PROFILE_CATEGORY = stringPreferencesKey("selected_audio_profile_category")
        val SELECTED_PROFILE_ADDRESS = stringPreferencesKey("selected_audio_profile_address")
    }

    override fun selectedProfile(): Flow<StoredAudioProfile?> =
        context.audioProfileDataStore.data.map { prefs ->
            val id = AudioProfileId.from(prefs[Keys.SELECTED_PROFILE]) ?: return@map null
            val name = prefs[Keys.SELECTED_PROFILE_NAME]
            val category = prefs[Keys.SELECTED_PROFILE_CATEGORY]?.let { raw ->
                runCatching { ProfileCategory.valueOf(raw) }.getOrNull()
            }
            val address = prefs[Keys.SELECTED_PROFILE_ADDRESS]
            StoredAudioProfile(
                id = id,
                displayName = name,
                category = category,
                deviceAddress = address
            )
        }

    override suspend fun persistSelectedProfile(profile: StoredAudioProfile?) {
        context.audioProfileDataStore.edit { prefs ->
            if (profile == null) {
                prefs.remove(Keys.SELECTED_PROFILE)
                prefs.remove(Keys.SELECTED_PROFILE_NAME)
                prefs.remove(Keys.SELECTED_PROFILE_CATEGORY)
                prefs.remove(Keys.SELECTED_PROFILE_ADDRESS)
            } else {
                prefs[Keys.SELECTED_PROFILE] = profile.id.value
                if (profile.displayName.isNullOrBlank()) {
                    prefs.remove(Keys.SELECTED_PROFILE_NAME)
                } else {
                    prefs[Keys.SELECTED_PROFILE_NAME] = profile.displayName
                }
                profile.category?.let { prefs[Keys.SELECTED_PROFILE_CATEGORY] = it.name }
                    ?: prefs.remove(Keys.SELECTED_PROFILE_CATEGORY)
                profile.deviceAddress?.let { prefs[Keys.SELECTED_PROFILE_ADDRESS] = it }
                    ?: prefs.remove(Keys.SELECTED_PROFILE_ADDRESS)
            }
        }
    }
}
