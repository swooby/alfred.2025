package com.swooby.alfred.core.profile

import androidx.compose.runtime.Immutable

@Immutable
data class AudioProfileUiState(
    val profiles: List<AudioProfileSnapshot> = emptyList(),
    val selectedProfileId: AudioProfileId? = null,
    val effectiveProfile: EffectiveAudioProfile? = null,
    val connectedHeadsets: ConnectedHeadsets = ConnectedHeadsets(),
    val missingPermissions: Set<String> = emptySet(),
) {
    val hasHeadset: Boolean = connectedHeadsets.all.isNotEmpty()
}

enum class BluetoothPermissionState {
    Unknown,
    Granted,
    Missing,
    Unavailable,
}
