package com.swooby.alfred.core.summary.templates

import com.swooby.alfred.core.summary.PhraseTemplate
import com.swooby.alfred.core.summary.Utterance
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class GenericMediaTemplate : PhraseTemplate {
    override val priority = 10
    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        if (!e.eventType.startsWith("media.")) return null
        val title = e.attributes["title"]?.jsonPrimitive?.contentOrNull
        val artist = e.attributes["artist"]?.jsonPrimitive?.contentOrNull
        return when (e.eventType) {
            SourceEventTypes.MEDIA_START -> {
                val s = buildString {
                    append("Now playing")
                    if (!title.isNullOrBlank()) { append(": "); append(title) }
                    if (!artist.isNullOrBlank()) append(" by " + artist)
                    append(".")
                }
                Utterance.Live(10, s)
            }
            SourceEventTypes.MEDIA_STOP -> {
                val played = e.metrics["played_ms"]?.jsonPrimitive?.intOrNull ?: e.durationMs?.toInt()
                val s = if (!title.isNullOrBlank() && played != null)
                    "Finished " + title + " after " + (played/1000) + " seconds."
                else "Playback stopped."
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
            val s = if (artist.isNullOrBlank()) "Spotify: " + title + "."
                    else "Spotify: " + title + " by " + artist + "."
            return Utterance.Live(20, s)
        }
        return null
    }
}

class GenericNotifTemplate : PhraseTemplate {
    override val priority = 5
    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        if (e.eventType != SourceEventTypes.NOTIFICATION_POST) return null
        val title = e.attributes["title"]?.jsonPrimitive?.contentOrNull
        return Utterance.Live(5, if (title.isNullOrBlank()) "New notification." else "Notification: " + title + ".")
    }
}

class ScreenTemplate : PhraseTemplate {
    override val priority = 5
    override fun livePhraseOrNull(e: EventEntity): Utterance.Live? {
        return when (e.eventType) {
            SourceEventTypes.DISPLAY_ON  -> Utterance.Live(5, "Screen on.")
            SourceEventTypes.DISPLAY_OFF -> Utterance.Live(5, "Screen off.")
            else -> null
        }
    }
}
