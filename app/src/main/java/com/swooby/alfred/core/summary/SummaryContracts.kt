package com.swooby.alfred.core.summary

import com.swooby.alfred.data.EventEntity

sealed interface Utterance {
    val priority: Int
    data class Live(override val priority: Int, val text: String): Utterance
    data class Digest(override val priority: Int, val title: String, val lines: List<String>): Utterance
}

interface SummaryGenerator {
    fun livePhrase(e: EventEntity): Utterance.Live?
    fun digest(title: String, events: List<EventEntity>): Utterance.Digest
}