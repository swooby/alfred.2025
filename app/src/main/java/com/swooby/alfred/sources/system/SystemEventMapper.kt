package com.swooby.alfred.sources.system

import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SourceEventTypes
import com.swooby.alfred.util.StateDurationTracker
import com.swooby.alfred.util.Ulids
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class SystemEventMapper(
    private val defaultUserId: String = "u_local",
    private val defaultDeviceId: String = "android:device",
    private val eventIdProvider: () -> String = { Ulids.newUlid() },
) {
    companion object {
        private const val DISPLAY_TRACKER_KEY = "display:primary"
    }

    private val displayDurations = StateDurationTracker<String, DisplayState>()

    private data class EventMetadata(
        val eventType: String,
        val category: String,
        val action: String,
        val subject: String,
        val attributes: JsonObject = JsonObject(emptyMap()),
        val metrics: JsonObject = JsonObject(emptyMap()),
        val userInteractive: Boolean? = null,
        val fingerprint: String? = null,
        val coalesceKey: String? = null,
    )

    /**
     * Normalizes [SystemEvent]s into [RawEvent] envelopes for the ingest pipeline.
     */
    fun map(
        event: SystemEvent,
        userId: String = defaultUserId,
        deviceId: String = defaultDeviceId,
        eventIdProvider: () -> String = this.eventIdProvider,
    ): RawEvent {
        val metadata =
            when (event) {
                is DisplayEvent -> buildDisplayMetadata(event)
                is DeviceEvent -> buildDeviceMetadata(event)
                is PowerConnectedEvent -> buildPowerConnectedMetadata(event)
                is PowerDisconnectedEvent -> buildPowerDisconnectedMetadata(event)
                is PowerStatusEvent -> buildPowerStatusMetadata(event)
                is CallStateEvent -> buildCallStateMetadata(event)
            }

        val entity =
            EventEntity(
                eventId = eventIdProvider(),
                schemaVer = 1,
                userId = userId,
                deviceId = deviceId,
                component = SourceComponentIds.SYSTEM_EVENT_SOURCE,
                eventType = metadata.eventType,
                eventCategory = metadata.category,
                eventAction = metadata.action,
                subjectEntity = metadata.subject,
                tsStart = event.timestamp,
                api = event.source,
                userInteractive = metadata.userInteractive,
                attributes = metadata.attributes,
                metrics = metadata.metrics,
            )
        return RawEvent(
            event = entity,
            fingerprint = metadata.fingerprint,
            coalesceKey = metadata.coalesceKey,
        )
    }

    private fun buildDisplayMetadata(event: DisplayEvent): EventMetadata {
        val eventType =
            when (event.state) {
                DisplayState.SCREEN_ON -> SourceEventTypes.DISPLAY_ON
                DisplayState.SCREEN_OFF -> SourceEventTypes.DISPLAY_OFF
                DisplayState.UNLOCKED -> SourceEventTypes.DEVICE_UNLOCK
            }
        val action =
            when (event.state) {
                DisplayState.SCREEN_ON -> "on"
                DisplayState.SCREEN_OFF -> "off"
                DisplayState.UNLOCKED -> "unlock"
            }
        val subject =
            when (event.state) {
                DisplayState.UNLOCKED -> "device"
                else -> "display"
            }
        val duration =
            when (event.state) {
                DisplayState.SCREEN_ON,
                DisplayState.SCREEN_OFF,
                -> displayDurations.record(DISPLAY_TRACKER_KEY, event.state, event.timestamp)
                DisplayState.UNLOCKED -> null
            }
        val metrics =
            duration
                ?.let {
                    buildJsonObject { put("previous_state_duration_ms", JsonPrimitive(it.durationMs)) }
                } ?: JsonObject(emptyMap())
        return EventMetadata(
            eventType = eventType,
            category = subject,
            action = action,
            subject = subject,
            userInteractive = event.interactive,
            fingerprint = eventType,
            coalesceKey =
                when (event.state) {
                    DisplayState.UNLOCKED -> "device_unlock"
                    else -> "display_state"
                },
            attributes =
                buildJsonObject {
                    put("source", JsonPrimitive(event.source))
                    event.interactive?.let { put("interactive", JsonPrimitive(it)) }
                    duration?.let { put("previous_state", JsonPrimitive(it.previousState.name.lowercase(Locale.US))) }
                },
            metrics = metrics,
        )
    }

    private fun buildDeviceMetadata(event: DeviceEvent): EventMetadata {
        val eventType =
            when (event.lifecycle) {
                DeviceLifecycle.BOOT_COMPLETED -> SourceEventTypes.DEVICE_BOOT
                DeviceLifecycle.SHUTDOWN -> SourceEventTypes.DEVICE_SHUTDOWN
            }
        val action =
            when (event.lifecycle) {
                DeviceLifecycle.BOOT_COMPLETED -> "boot"
                DeviceLifecycle.SHUTDOWN -> "shutdown"
            }
        return EventMetadata(
            eventType = eventType,
            category = "device",
            action = action,
            subject = "device",
            fingerprint = eventType,
            coalesceKey = "device_state",
            attributes = buildJsonObject { put("source", JsonPrimitive(event.source)) },
        )
    }

    private fun buildPowerConnectedMetadata(event: PowerConnectedEvent): EventMetadata =
        EventMetadata(
            eventType = SourceEventTypes.POWER_CONNECTED,
            category = "power",
            action = "connected",
            subject = "power",
            attributes =
                buildJsonObject {
                    put("plugType", JsonPrimitive(event.plugType.serialized()))
                    put("chargingStatus", JsonPrimitive(event.status.serialized()))
                    event.batteryPercent?.let { put("batteryPercent", JsonPrimitive(it)) }
                },
            fingerprint = "${SourceEventTypes.POWER_CONNECTED}:${event.plugType.serialized()}",
            coalesceKey = "power_connection",
        )

    private fun buildPowerDisconnectedMetadata(event: PowerDisconnectedEvent): EventMetadata =
        EventMetadata(
            eventType = SourceEventTypes.POWER_DISCONNECTED,
            category = "power",
            action = "disconnected",
            subject = "power",
            attributes =
                buildJsonObject {
                    put("plugType", JsonPrimitive(event.previousPlugType.serialized()))
                    event.batteryPercent?.let { put("batteryPercent", JsonPrimitive(it)) }
                },
            fingerprint = "${SourceEventTypes.POWER_DISCONNECTED}:${event.previousPlugType.serialized()}",
            coalesceKey = "power_connection",
        )

    private fun buildPowerStatusMetadata(event: PowerStatusEvent): EventMetadata =
        EventMetadata(
            eventType = SourceEventTypes.POWER_CHARGING_STATUS,
            category = "power",
            action = event.status.serialized(),
            subject = "battery",
            attributes =
                buildJsonObject {
                    put("chargingStatus", JsonPrimitive(event.status.serialized()))
                    put("plugType", JsonPrimitive(event.plugType.serialized()))
                    event.batteryPercent?.let { put("batteryPercent", JsonPrimitive(it)) }
                },
            fingerprint = "${SourceEventTypes.POWER_CHARGING_STATUS}:${event.status.serialized()}:${event.plugType.serialized()}",
            coalesceKey = "power_status",
        )

    private fun buildCallStateMetadata(event: CallStateEvent): EventMetadata {
        val (eventType, action) =
            when (event.status) {
                CallStatus.ACTIVE -> SourceEventTypes.CALL_ACTIVE to "active"
                CallStatus.IDLE -> SourceEventTypes.CALL_IDLE to "idle"
                CallStatus.RINGING -> SourceEventTypes.CALL_RINGING to "ringing"
                CallStatus.UNKNOWN -> SourceEventTypes.CALL_IDLE to "unknown"
            }
        val subject = "call"
        val attributes =
            buildJsonObject {
                put("status", JsonPrimitive(event.status.name.lowercase(Locale.US)))
                put("source", JsonPrimitive(event.source))
            }
        return EventMetadata(
            eventType = eventType,
            category = subject,
            action = action,
            subject = subject,
            attributes = attributes,
            fingerprint = "$eventType:${event.status.name.lowercase(Locale.US)}",
            coalesceKey = "call_state",
        )
    }
}

private fun PlugType.serialized(): String = name.lowercase(Locale.US)

private fun ChargingStatus.serialized(): String = name.lowercase(Locale.US)
