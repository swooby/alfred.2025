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
}

object SourceEventTypes {
    const val NOTIFICATION_POST = "notification.post"
    const val MEDIA_START = "media.start"
    const val MEDIA_STOP = "media.stop"
    const val DISPLAY_ON = "display.on"
    const val DISPLAY_OFF = "display.off"
    const val NETWORK_WIFI_CONNECT = "network.wifi.connect"
    const val NETWORK_WIFI_DISCONNECT = "network.wifi.disconnect"
}
