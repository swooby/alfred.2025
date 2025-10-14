package com.swooby.alfred.core.profile

import androidx.compose.runtime.Immutable

@Immutable
data class AudioProfileGate(
    val allow: Boolean,
    val reason: AudioProfileGateReason,
    val snapshot: AudioProfileSnapshot?,
)

enum class AudioProfileGateReason {
    ALLOWED,
    PROFILE_DISABLED,
    NO_ACTIVE_DEVICES,
    UNINITIALIZED,
}
