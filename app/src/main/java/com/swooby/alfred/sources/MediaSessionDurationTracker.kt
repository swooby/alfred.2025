package com.swooby.alfred.sources

import android.media.session.MediaController
import android.media.session.PlaybackState
import com.swooby.alfred.util.Ulids
import kotlin.time.Clock
import kotlin.time.Instant

data class MediaPlaySpan(
    val sessionId: String,
    val startWall: Instant,
    val startPosMs: Long,
    var lastState: Int,
)

class MediaSessionDurationTracker {
    private val spans = mutableMapOf<MediaController, MediaPlaySpan>()

    fun onStart(
        controller: MediaController,
        state: PlaybackState?,
    ): MediaPlaySpan =
        spans
            .getOrPut(controller) {
                MediaPlaySpan(
                    sessionId = "MS-${Ulids.newUlid()}",
                    startWall = Clock.System.now(),
                    startPosMs = state?.position ?: 0L,
                    lastState = state?.state ?: PlaybackState.STATE_PLAYING,
                )
            }.also { it.lastState = PlaybackState.STATE_PLAYING }

    fun onStop(
        controller: MediaController,
        state: PlaybackState?,
    ): QuadTimes? {
        val span = spans.remove(controller) ?: return null
        val endWall = Clock.System.now()
        val durationMs = endWall.toEpochMilliseconds() - span.startWall.toEpochMilliseconds()
        val endPos = state?.position ?: (span.startPosMs + durationMs)
        val playedMs = (endPos - span.startPosMs).coerceAtLeast(0)
        return QuadTimes(span.sessionId, span.startWall, endWall, durationMs, playedMs)
    }

    data class QuadTimes(
        val sessionId: String,
        val tsStart: Instant,
        val tsEnd: Instant,
        val durationMs: Long,
        val playedMs: Long,
    )
}
