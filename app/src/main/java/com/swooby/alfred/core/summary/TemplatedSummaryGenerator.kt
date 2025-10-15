package com.swooby.alfred.core.summary

import android.content.Context
import com.swooby.alfred.core.summary.templates.DeviceStateTemplate
import com.swooby.alfred.core.summary.templates.GenericMediaTemplate
import com.swooby.alfred.core.summary.templates.NotificationTemplate
import com.swooby.alfred.core.summary.templates.SpotifyTemplate
import com.swooby.alfred.data.EventEntity

class TemplatedSummaryGenerator(
    context: Context,
    templates: List<PhraseTemplate> =
        listOf(
            SpotifyTemplate(),
            GenericMediaTemplate(),
            NotificationTemplate(),
            DeviceStateTemplate(context),
        ),
) : SummaryGenerator {
    private val ordered = templates.sortedByDescending { it.priority }

    override fun livePhrase(e: EventEntity): Utterance.Live? {
        for (t in ordered) {
            val p = t.livePhraseOrNull(e)
            if (p != null) return p
        }
        return null
    }

    override fun digest(
        title: String,
        events: List<EventEntity>,
    ): Utterance.Digest {
        val base = SummaryGeneratorImpl()
        return base.digest(title, events)
    }
}
