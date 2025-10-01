package com.swooby.alfred2017.core.ingest

import com.swooby.alfred2017.data.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class RawEvent(
    val event: EventEntity,
    val fingerprint: String? = null,
    val coalesceKey: String? = null
)

interface EventIngest {
    val out: SharedFlow<EventEntity>
    fun submit(raw: RawEvent)
}

class EventIngestImpl(
    private val scope: CoroutineScope,
    private val debounceWindow: Duration = 200.milliseconds,
    private val dedupeWindow: Duration = 2_000.milliseconds
) : EventIngest {

    private val _in = MutableSharedFlow<RawEvent>(extraBufferCapacity = 1024)
    private val _out = MutableSharedFlow<EventEntity>(replay = 0, extraBufferCapacity = 256)
    override val out: SharedFlow<EventEntity> = _out.asSharedFlow()

    private val recentFingerprints = ArrayDeque<Pair<Long,String>>()
    private val recentCoalesce = mutableMapOf<String, RawEvent>()

    init {
        scope.launch(Dispatchers.Default) {
            _in
                .onEach { raw ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (raw.coalesceKey != null) {
                        recentCoalesce[raw.coalesceKey] = raw
                        return@onEach
                    }
                    emitIfNotDuplicate(raw, now)
                }
                .launchIn(this)

            tickerFlow(debounceWindow).onEach {
                val snapshot = recentCoalesce.toMap()
                recentCoalesce.clear()
                val now = Clock.System.now().toEpochMilliseconds()
                snapshot.values.forEach { emitIfNotDuplicate(it, now) }
            }.launchIn(this)
        }
    }

    private suspend fun emitIfNotDuplicate(raw: RawEvent, nowMs: Long) {
        raw.fingerprint?.let { f ->
            while (recentFingerprints.isNotEmpty() &&
                   nowMs - recentFingerprints.first().first > dedupeWindow.inWholeMilliseconds) {
                recentFingerprints.removeFirst()
            }
            if (recentFingerprints.any { it.second == f }) return
            recentFingerprints.addLast(nowMs to f)
        }

        val e = raw.event
        val normalized = e.copy(
            ingestAt = e.ingestAt ?: Clock.System.now(),
            durationMs = e.durationMs ?: e.tsEnd?.let { end ->
                end.toEpochMilliseconds() - e.tsStart.toEpochMilliseconds()
            }
        )
        _out.emit(normalized)
    }

    override fun submit(raw: RawEvent) { _in.tryEmit(raw) }

    private fun tickerFlow(period: Duration) = flow {
        while (true) { kotlinx.coroutines.delay(period); emit(Unit) }
    }
}