package com.swooby.alfred.core.profile

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

sealed interface HeadsetEvent {
    val timestamp: Instant

    @Immutable
    data class Connected(
        override val timestamp: Instant,
        val device: HeadsetDevice,
    ) : HeadsetEvent

    @Immutable
    data class Disconnected(
        override val timestamp: Instant,
        val device: HeadsetDevice,
    ) : HeadsetEvent

    @Immutable
    data class PermissionMissing(
        override val timestamp: Instant,
        val permission: String,
    ) : HeadsetEvent

    @Immutable
    data class Error(
        override val timestamp: Instant,
        val message: String,
        val throwableMessage: String? = null,
    ) : HeadsetEvent
}
