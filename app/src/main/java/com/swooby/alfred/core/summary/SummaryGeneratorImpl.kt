package com.swooby.alfred.core.summary

import com.swooby.alfred.data.EventEntity
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

class SummaryGeneratorImpl : SummaryGenerator {
    override fun livePhrase(e: EventEntity): Utterance.Live? {
        return null // not used directly when templated generator is used
    }
    override fun digest(title: String, events: List<EventEntity>): Utterance.Digest {
        val lines = mutableListOf<String>()
        val notifCount = events.count { it.eventType == "notif.post" }
        val musicSec = events
            .filter { it.eventType == "media.stop" }
            .mapNotNull { it.metrics["played_ms"]?.jsonPrimitive?.intOrNull ?: it.durationMs?.toInt() }
            .sum() / 1000
        val screenOnCount = events.count { it.eventType == "display.on" }
        if (notifCount > 0) lines += "Notifications: {}.".format(notifCount)
        if (musicSec > 0) lines += "Music time: {} min.".format(musicSec / 60)
        if (screenOnCount > 0) lines += "Screen ons: {}.".format(screenOnCount)
        if (lines.isEmpty()) lines += "Nothing notable."
        return Utterance.Digest(3, title, lines)
    }
}