package com.swooby.alfred.core.summary

import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

class SummaryGeneratorImpl : SummaryGenerator {
    override fun livePhrase(e: EventEntity): Utterance.Live? {
        return null // not used directly when templated generator is used
    }
    override fun digest(title: String, events: List<EventEntity>): Utterance.Digest {
        val lines = mutableListOf<String>()
        val notificationCount = events.count { it.eventType == SourceEventTypes.NOTIFICATION_POST }
        val musicSec = events
            .filter { it.eventType == SourceEventTypes.MEDIA_STOP }
            .mapNotNull { it.metrics["played_ms"]?.jsonPrimitive?.intOrNull ?: it.durationMs?.toInt() }
            .sum() / 1000
        val screenOnCount = events.count { it.eventType == SourceEventTypes.DISPLAY_ON }
        if (notificationCount > 0) lines += "Notifications: {}.".format(notificationCount)
        if (musicSec > 0) lines += "Music time: {} min.".format(musicSec / 60)
        if (screenOnCount > 0) lines += "Screen ons: {}.".format(screenOnCount)
        if (lines.isEmpty()) lines += "Nothing notable."
        return Utterance.Digest(3, title, lines)
    }
}
