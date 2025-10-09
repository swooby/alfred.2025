package com.swooby.alfred.core.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@JvmInline
value class HeadsetId(val value: String) {
    init {
        require(value.isNotBlank()) { "HeadsetId must not be blank." }
    }

    override fun toString(): String = value
}

enum class HeadsetKind {
    WIRED,
    BLUETOOTH
}

@Stable
sealed class HeadsetDevice {
    abstract val id: HeadsetId
    abstract val displayName: String
    abstract val kind: HeadsetKind
    abstract val supportsMicrophone: Boolean
    abstract val supportsOutput: Boolean
    abstract val rawName: String?

    val safeDisplayName: String
        get() = displayName.ifBlank { fallbackName() }

    protected open fun fallbackName(): String = when (kind) {
        HeadsetKind.WIRED -> "Wired headset"
        HeadsetKind.BLUETOOTH -> "Bluetooth headset"
    }

    @Immutable
    data class Wired(
        override val id: HeadsetId,
        override val displayName: String,
        override val supportsMicrophone: Boolean,
        override val supportsOutput: Boolean,
        override val rawName: String? = null,
        val portAddress: String? = null
    ) : HeadsetDevice() {
        override val kind: HeadsetKind = HeadsetKind.WIRED
    }

    @Immutable
    data class Bluetooth(
        override val id: HeadsetId,
        override val displayName: String,
        override val supportsMicrophone: Boolean,
        override val supportsOutput: Boolean,
        override val rawName: String?,
        val address: String?,
        val isLeAudio: Boolean,
        val profileState: Int? = null
    ) : HeadsetDevice() {
        override val kind: HeadsetKind = HeadsetKind.BLUETOOTH
    }
}

@Immutable
data class ConnectedHeadsets(
    val wired: Set<HeadsetDevice.Wired> = emptySet(),
    val bluetooth: Set<HeadsetDevice.Bluetooth> = emptySet()
) {
    val all: Set<HeadsetDevice> = (wired + bluetooth)
}
