package com.swooby.alfred2017.core.summary

import com.swooby.alfred2017.data.EventEntity

interface PhraseTemplate {
    val priority: Int
    fun livePhraseOrNull(e: EventEntity): Utterance.Live?
}