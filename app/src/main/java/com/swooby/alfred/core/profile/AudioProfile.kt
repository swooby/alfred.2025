package com.swooby.alfred.core.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.util.UUID

@JvmInline
value class AudioProfileId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AudioProfileId must not be blank." }
    }

    override fun toString(): String = value

    companion object {
        fun from(raw: String?): AudioProfileId? = raw?.takeIf { it.isNotBlank() }?.let(::AudioProfileId)

        fun random(): AudioProfileId = AudioProfileId(UUID.randomUUID().toString())

        fun static(value: String): AudioProfileId = AudioProfileId(value)

        fun device(token: String): AudioProfileId = AudioProfileId("profile.headset.bluetooth.$token")
    }
}

enum class ProfileCategory {
    DISABLED,
    ALWAYS_ON,
    WIRED_ONLY,
    BLUETOOTH_ANY,
    BLUETOOTH_DEVICE,
    ANY_HEADSET,
}

@Immutable
data class ProfileMetadata(
    val description: String? = null,
    val deviceToken: String? = null,
    val deviceAddress: String? = null,
)

@Stable
sealed interface AudioProfile {
    val id: AudioProfileId
    val displayName: String
    val category: ProfileCategory
    val metadata: ProfileMetadata

    val sortKey: String
        get() = "${category.ordinal}_${displayName.lowercase()}"

    data class Disabled(
        override val displayName: String,
        override val metadata: ProfileMetadata,
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.static("profile.disabled")
        override val category: ProfileCategory = ProfileCategory.DISABLED
    }

    data class AlwaysOn(
        override val displayName: String,
        override val metadata: ProfileMetadata,
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.static("profile.always_on")
        override val category: ProfileCategory = ProfileCategory.ALWAYS_ON
    }

    data class WiredOnly(
        override val displayName: String,
        override val metadata: ProfileMetadata,
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.static("profile.headset.wired")
        override val category: ProfileCategory = ProfileCategory.WIRED_ONLY
    }

    data class BluetoothAny(
        override val displayName: String,
        override val metadata: ProfileMetadata,
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.static("profile.headset.bluetooth.any")
        override val category: ProfileCategory = ProfileCategory.BLUETOOTH_ANY
    }

    data class BluetoothDevice(
        override val displayName: String,
        val deviceToken: String,
        val address: String?,
        override val metadata: ProfileMetadata =
            ProfileMetadata(
                deviceToken = deviceToken,
                deviceAddress = address,
            ),
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.device(deviceToken)
        override val category: ProfileCategory = ProfileCategory.BLUETOOTH_DEVICE
    }

    data class AnyHeadset(
        override val displayName: String,
        override val metadata: ProfileMetadata,
    ) : AudioProfile {
        override val id: AudioProfileId = AudioProfileId.static("profile.headset.any")
        override val category: ProfileCategory = ProfileCategory.ANY_HEADSET
    }
}

@Immutable
data class AudioProfileSnapshot(
    val profile: AudioProfile,
    val isSelected: Boolean,
    val isActive: Boolean,
    val isEffective: Boolean,
    val activeDevices: Set<HeadsetDevice>,
) {
    val id: AudioProfileId = profile.id
}

@Immutable
data class EffectiveAudioProfile(
    val profile: AudioProfile,
    val activeDevices: Set<HeadsetDevice>,
)
