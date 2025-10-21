package com.swooby.alfred.sources.system

import kotlin.time.Instant

/**
 * Strongly typed system events emitted by [SystemEventIngestion].
 *
 * These data classes allow consumers (pipeline, UI, tests) to react to state changes in a
 * type-safe way before they are normalized into [com.swooby.alfred.data.EventEntity] instances.
 */
sealed interface SystemEvent {
    val timestamp: Instant
    val source: String
}

enum class DisplayState { SCREEN_ON, SCREEN_OFF, UNLOCKED }

data class DisplayEvent(
    val state: DisplayState,
    val interactive: Boolean?,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent

enum class DeviceLifecycle { BOOT_COMPLETED, SHUTDOWN }

data class DeviceEvent(
    val lifecycle: DeviceLifecycle,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent

enum class PlugType { AC, USB, WIRELESS, CAR, OTHER, NONE }

enum class ChargingStatus { CHARGING, FULL, NOT_CHARGING, DISCHARGING, UNKNOWN }

enum class CallStatus { UNKNOWN, IDLE, RINGING, ACTIVE }

data class CallStateEvent(
    val status: CallStatus,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent {
    val inCall: Boolean
        get() = status == CallStatus.ACTIVE
}

data class PowerConnectedEvent(
    val plugType: PlugType,
    val status: ChargingStatus,
    val batteryPercent: Int?,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent

data class PowerDisconnectedEvent(
    val previousPlugType: PlugType,
    val batteryPercent: Int?,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent

data class PowerStatusEvent(
    val status: ChargingStatus,
    val plugType: PlugType,
    val batteryPercent: Int?,
    override val timestamp: Instant,
    override val source: String,
) : SystemEvent
