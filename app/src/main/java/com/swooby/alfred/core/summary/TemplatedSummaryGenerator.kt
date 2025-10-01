package com.swooby.alfred.core.summary

import com.swooby.alfred.core.summary.templates.*
import com.swooby.alfred.data.EventEntity

class TemplatedSummaryGenerator(
    templates: List<PhraseTemplate> = listOf(
        SpotifyTemplate(),
        GenericMediaTemplate(),
        GenericNotifTemplate(),
        ScreenTemplate()
    )
) : SummaryGenerator {

    private val ordered = templates.sortedByDescending { it.priority }

    override fun livePhrase(e: EventEntity): Utterance.Live? {
        for (t in ordered) {
            val p = t.livePhraseOrNull(e)
            if (p != null) return p
        }
        return null
    }

    override fun digest(title: String, events: List<EventEntity>): Utterance.Digest {
        val base = SummaryGeneratorImpl()
        return base.digest(title, events)
    }
}