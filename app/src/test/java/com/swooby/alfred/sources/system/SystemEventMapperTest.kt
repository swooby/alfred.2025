package com.swooby.alfred.sources.system

import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SystemEventMapperTest {
    @Test
    fun powerConnectionMapsToRawEvent() {
        val event =
            PowerConnectedEvent(
                plugType = PlugType.USB,
                status = ChargingStatus.CHARGING,
                batteryPercent = 88,
                timestamp = Instant.parse("2025-01-01T00:00:00Z"),
                source = "test.action",
            )

        val mapper = SystemEventMapper()
        val raw = mapper.map(event, userId = "user", deviceId = "device") { "ulid-123" }

        assertEquals("ulid-123", raw.event.eventId)
        assertEquals(SourceComponentIds.SYSTEM_EVENT_SOURCE, raw.event.component)
        assertEquals(SourceEventTypes.POWER_CONNECTED, raw.event.eventType)
        assertEquals("power", raw.event.eventCategory)
        assertEquals("connected", raw.event.eventAction)
        assertEquals("power", raw.event.subjectEntity)
        assertEquals("power_connection", raw.coalesceKey)
        assertEquals("${SourceEventTypes.POWER_CONNECTED}:usb", raw.fingerprint)
        val pct =
            raw.event.attributes["batteryPercent"]
                ?.jsonPrimitive
                ?.intOrNull
        assertEquals(88, pct)
    }

    @Test
    fun displayUnlockCarriesInteractiveFlag() {
        val event =
            DisplayEvent(
                state = DisplayState.UNLOCKED,
                interactive = true,
                timestamp = Instant.parse("2025-02-02T02:02:02Z"),
                source = "intent.user.present",
            )

        val mapper = SystemEventMapper()
        val raw = mapper.map(event) { "ulid-456" }

        assertEquals(SourceEventTypes.DEVICE_UNLOCK, raw.event.eventType)
        assertEquals("device", raw.event.eventCategory)
        assertEquals(true, raw.event.userInteractive)
        assertNotNull(raw.event.attributes["source"])
        assertTrue(
            raw.event.attributes["interactive"]
                ?.jsonPrimitive
                ?.booleanOrNull == true,
        )
    }

    @Test
    fun screenDurationsAreCapturedOnTransitions() {
        val mapper = SystemEventMapper()
        val base = Instant.parse("2025-03-01T12:00:00Z")
        val offEvent =
            DisplayEvent(
                state = DisplayState.SCREEN_OFF,
                interactive = false,
                timestamp = base,
                source = "intent.screen.off",
            )
        val onEvent =
            DisplayEvent(
                state = DisplayState.SCREEN_ON,
                interactive = true,
                timestamp = base.plus(45.seconds),
                source = "intent.screen.on",
            )

        val offRaw = mapper.map(offEvent) { "screen-off" }
        val onRaw = mapper.map(onEvent) { "screen-on" }

        assertNull(offRaw.event.metrics["previous_state_duration_ms"])
        val duration =
            onRaw.event.metrics["previous_state_duration_ms"]
                ?.jsonPrimitive
                ?.longOrNull
        assertEquals(45_000L, duration)
    }
}
