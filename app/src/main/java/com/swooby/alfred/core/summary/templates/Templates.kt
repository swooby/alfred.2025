package com.swooby.alfred.core.summary.templates

import android.content.Context
import com.smartfoo.android.core.FooString
import com.swooby.alfred.core.summary.PhraseTemplate
import com.swooby.alfred.core.summary.Utterance
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class GenericMediaTemplate : PhraseTemplate {
    override val priority = 10

    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        if (!e.eventType.startsWith("media.")) return null
        val title = e.attributes["title"]?.jsonPrimitive?.contentOrNull
        val artist = e.attributes["artist"]?.jsonPrimitive?.contentOrNull
        return when (e.eventType) {
            SourceEventTypes.MEDIA_START -> {
                val s =
                    buildString {
                        append("Now playing")
                        if (!title.isNullOrBlank()) {
                            append(": ")
                            append(title)
                        }
                        if (!artist.isNullOrBlank()) append(" by $artist")
                        append(".")
                    }
                Utterance.Live(10, s)
            }
            SourceEventTypes.MEDIA_STOP -> {
                val played = e.metrics["played_ms"]?.jsonPrimitive?.intOrNull ?: e.durationMs?.toInt()
                val s =
                    if (!title.isNullOrBlank() && played != null) {
                        "Finished $title after ${played / 1000} seconds."
                    } else {
                        "Playback stopped."
                    }
                Utterance.Live(9, s)
            }
            else -> null
        }
    }
}

class SpotifyTemplate : PhraseTemplate {
    override val priority = 20

    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        if (e.appPkg != "com.spotify.music") return null
        if (e.eventType == SourceEventTypes.MEDIA_START) {
            val title = e.attributes["title"]?.jsonPrimitive?.contentOrNull ?: "a track"
            val artist = e.attributes["artist"]?.jsonPrimitive?.contentOrNull
            val s =
                if (artist.isNullOrBlank()) {
                    "Spotify: $title."
                } else {
                    "Spotify: $title by $artist."
                }
            return Utterance.Live(20, s)
        }
        return null
    }
}

class NotificationTemplate : PhraseTemplate {
    override val priority = 5

    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        if (e.eventType != SourceEventTypes.NOTIFICATION_POST) return null
        val attributes = e.attributes
        val subject = attributes["subject"] as? JsonObject
        val subjectLines = attributes["subjectLines"] as? JsonArray
        val actor = attributes["actor"] as? JsonObject

        val title =
            subject.firstNonBlankString("title", "conversationTitle", "template")
                ?: e.subjectEntity.takeIf { it.isNotBlank() }
        val body =
            subject.firstNonBlankString("text", "summaryText", "subText", "infoText")
                ?: subjectLines?.firstStringOrNull()
        val appLabel =
            actor.firstNonBlankString("appLabel")
                ?: e.appPkg

        val spoken =
            when {
                !title.isNullOrBlank() && !body.isNullOrBlank() ->
                    "Notification from ${appLabel ?: "an app"}: $title — $body"
                !title.isNullOrBlank() ->
                    "Notification from ${appLabel ?: "an app"}: $title"
                !body.isNullOrBlank() ->
                    "New notification from ${appLabel ?: "an app"}: $body"
                else -> "New notification"
            }
        return Utterance.Live(5, spoken.ensureSentenceTerminator())
    }
}

class DeviceStateTemplate(
    private val context: Context,
) : PhraseTemplate {
    override val priority = 5

    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? =
        when (e.eventType) {
            SourceEventTypes.DISPLAY_ON -> Utterance.Live(5, buildScreenPhrase("on", "off", e))
            SourceEventTypes.DISPLAY_OFF -> Utterance.Live(5, buildScreenPhrase("off", "on", e))
            SourceEventTypes.DEVICE_UNLOCK -> Utterance.Live(5, "Device unlocked.")
            SourceEventTypes.DEVICE_BOOT -> Utterance.Live(5, "Device booted.")
            SourceEventTypes.DEVICE_SHUTDOWN -> Utterance.Live(5, "Device shutting down.")
            SourceEventTypes.POWER_CONNECTED -> Utterance.Live(5, buildPowerConnectedPhrase(e).ensureSentenceTerminator())
            SourceEventTypes.POWER_DISCONNECTED -> Utterance.Live(5, buildPowerDisconnectedPhrase(e).ensureSentenceTerminator())
            SourceEventTypes.POWER_CHARGING_STATUS -> buildPowerStatusPhrase(e)?.let { Utterance.Live(5, it.ensureSentenceTerminator()) }
            else -> null
        }

    private fun buildScreenPhrase(
        currentState: String,
        previousState: String,
        event: EventEntity,
    ): String {
        val durationMs =
            event.metrics["previous_state_duration_ms"]
                ?.jsonPrimitive
                ?.longOrNull
        val durationText =
            durationMs
                ?.takeIf { it > 0 }
                ?.let { FooString.getTimeDurationString(context, it) }
                ?.takeIf { it.isNotBlank() }
        return durationText
            ?.let { "Screen $currentState. Was $previousState for $it." }
            ?: "Screen $currentState."
    }
}

private fun buildPowerConnectedPhrase(e: EventEntity): String {
    val attrs = e.attributes
    val plug = attrs["plugType"]?.jsonPrimitive?.contentOrNull.formatPlugType()
    val status = attrs["chargingStatus"]?.jsonPrimitive?.contentOrNull.formatChargingStatus()
    val pct = attrs["batteryPercent"]?.jsonPrimitive?.intOrNull
    return buildString {
        append("Power connected")
        if (!plug.isNullOrBlank()) append(" via ").append(plug)
        if (pct != null) append(" at ").append(pct).append(" percent")
        if (!status.isNullOrBlank() && status != "charging") append(" (").append(status).append(")")
        append(".")
    }
}

private fun buildPowerDisconnectedPhrase(e: EventEntity): String {
    val attrs = e.attributes
    val plug = attrs["plugType"]?.jsonPrimitive?.contentOrNull.formatPlugType()
    val pct = attrs["batteryPercent"]?.jsonPrimitive?.intOrNull
    return buildString {
        append("Power disconnected")
        if (!plug.isNullOrBlank() && plug != "power") append(" from ").append(plug)
        if (pct != null) append(" at ").append(pct).append(" percent")
        append(".")
    }
}

private fun buildPowerStatusPhrase(e: EventEntity): String? {
    val attrs = e.attributes
    val statusRaw = attrs["chargingStatus"]?.jsonPrimitive?.contentOrNull ?: return null
    val status = statusRaw.formatChargingStatus() ?: return null
    val pct = attrs["batteryPercent"]?.jsonPrimitive?.intOrNull
    val plug = attrs["plugType"]?.jsonPrimitive?.contentOrNull.formatPlugType()
    return buildString {
        append("Battery ").append(status)
        if (pct != null) append(" at ").append(pct).append(" percent")
        if (!plug.isNullOrBlank()) append(" via ").append(plug)
        append(".")
    }
}

private fun String?.formatPlugType(): String? =
    when (this?.lowercase()) {
        "ac" -> "AC power"
        "usb" -> "USB power"
        "wireless" -> "wireless charging"
        "car" -> "car dock"
        "other" -> "external power"
        "none" -> "battery"
        else -> null
    }

private fun String?.formatChargingStatus(): String? =
    when (this?.lowercase()) {
        "charging" -> "charging"
        "full" -> "fully charged"
        "not_charging" -> "not charging"
        "discharging" -> "discharging"
        "unknown" -> null
        else -> this
    }

private fun JsonObject?.firstNonBlankString(vararg keys: String): String? {
    if (this == null) return null
    return keys
        .asSequence()
        .mapNotNull { key -> get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
        .firstOrNull()
}

private fun JsonArray.firstStringOrNull(): String? =
    asSequence()
        .mapNotNull(JsonElement::asNonBlankString)
        .firstOrNull()

private fun JsonElement.asNonBlankString(): String? = jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }

private fun String.ensureSentenceTerminator(): String {
    val trimmed = trimEnd()
    if (trimmed.isEmpty()) return this
    val lastChar = trimmed.last()
    return if (lastChar == '.' || lastChar == '!' || lastChar == '?') trimmed else "$trimmed."
}
