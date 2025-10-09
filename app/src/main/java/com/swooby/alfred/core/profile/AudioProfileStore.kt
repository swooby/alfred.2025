package com.swooby.alfred.core.profile

import kotlinx.coroutines.flow.Flow

data class StoredAudioProfile(
    val id: AudioProfileId,
    val displayName: String? = null,
    val category: ProfileCategory? = null,
    val deviceAddress: String? = null
)

interface AudioProfileStore {
    fun selectedProfile(): Flow<StoredAudioProfile?>

    suspend fun persistSelectedProfile(profile: StoredAudioProfile?)
}
