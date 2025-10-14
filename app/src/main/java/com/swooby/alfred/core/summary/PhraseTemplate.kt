package com.swooby.alfred.core.summary

import com.swooby.alfred.data.EventEntity

interface PhraseTemplate {
    val priority: Int

    fun livePhraseOrNull(e: EventEntity): Utterance.Live?
}
