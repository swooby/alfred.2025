package com.swooby.alfred.sources

/**
 * Canonical component identifiers emitted by platform data sources.
 *
 * Centralizing these values keeps the ingest pipeline, UI, and tests in sync when we rename
 * sources or refactor their payloads.
 */
object SourceComponentIds {
    const val NOTIFICATION_SOURCE = "notification_source"
    const val MEDIA_SOURCE = "media_source"
    const val SYSTEM_EVENT_SOURCE = "system_event_source"
}

object SourceEventTypes {
    const val NOTIFICATION_POST = "notification.post"
    const val MEDIA_START = "media.start"
    const val MEDIA_STOP = "media.stop"
    const val DISPLAY_ON = "display.on"
    const val DISPLAY_OFF = "display.off"
    const val DEVICE_UNLOCK = "device.unlock"
    const val DEVICE_BOOT = "device.boot"
    const val DEVICE_SHUTDOWN = "device.shutdown"
    const val NETWORK_WIFI_CONNECT = "network.wifi.connect"
    const val NETWORK_WIFI_DISCONNECT = "network.wifi.disconnect"
    const val POWER_CONNECTED = "power.connected"
    const val POWER_DISCONNECTED = "power.disconnected"
    const val POWER_CHARGING_STATUS = "power.charging.status"
}
