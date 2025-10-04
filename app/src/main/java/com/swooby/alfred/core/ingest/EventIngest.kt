package com.swooby.alfred.core.ingest

import com.swooby.alfred.BuildConfig
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.util.FooLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class RawEvent(
    val event: EventEntity,
    val fingerprint: String? = null,
    val coalesceKey: String? = null
)

interface EventIngest {
    val out: SharedFlow<EventEntity>
    fun submit(rawEvent: RawEvent)
}

class EventIngestImpl(
    private val scope: CoroutineScope,
    private val debounceWindow: Duration = 200.milliseconds,
    private val dedupeWindow: Duration = 2_000.milliseconds
) : EventIngest {
    companion object {
        private val TAG = FooLog.TAG(EventIngestImpl::class.java)
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private  val VERBOSE_LOG_SUBMIT = false && BuildConfig.DEBUG
    }

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

    override fun submit(rawEvent: RawEvent) {
        if (VERBOSE_LOG_SUBMIT) {
            FooLog.i(TAG, "submit: UNDEDUPED rawEvent=$rawEvent")
        }
        _in.tryEmit(rawEvent)
    }

    private fun tickerFlow(period: Duration) = flow {
        while (true) { kotlinx.coroutines.delay(period); emit(Unit) }
    }
}