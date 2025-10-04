package com.swooby.alfred.core.ingest

import com.swooby.alfred.BuildConfig
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.util.FooLog
import com.swooby.alfred.util.FooString
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
        private  val LOG_SUBMIT = false && BuildConfig.DEBUG
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private val LOG_FILTER = false && BuildConfig.DEBUG
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
                        val previous = recentCoalesce.put(raw.coalesceKey, raw)
                        if (LOG_FILTER) {
                            if (previous != null) {
                                FooLog.d(TAG, "#EVENT_FILTER debounce: drop coalesceKey=${FooString.quote(previous.coalesceKey)}")
                            }
                        }
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
        raw.fingerprint?.let { fingerprint ->
            while (recentFingerprints.isNotEmpty() &&
                   nowMs - recentFingerprints.first().first > dedupeWindow.inWholeMilliseconds) {
                recentFingerprints.removeFirst()
            }
            if (recentFingerprints.any { it.second == fingerprint }) {
                if (LOG_FILTER) {
                    FooLog.d(TAG, "#EVENT_FILTER dedupe: skip fingerprint=${FooString.quote(fingerprint)}")
                }
                return
            }
            recentFingerprints.addLast(nowMs to fingerprint)
        }

        val e = raw.event
        val normalized = e.copy(
            ingestAt = e.ingestAt ?: Clock.System.now(),
            durationMs = e.durationMs ?: e.tsEnd?.let { end ->
                end.toEpochMilliseconds() - e.tsStart.toEpochMilliseconds()
            }
        )
        if (LOG_FILTER) {
            FooLog.d(TAG, "#EVENT_FILTER emit: fingerprint=${FooString.quote(raw.fingerprint)} coalesceKey=${FooString.quote(raw.coalesceKey)} event=$normalized")
        }
        _out.emit(normalized)
    }

    override fun submit(rawEvent: RawEvent) {
        if (LOG_SUBMIT) {
            FooLog.i(TAG, "#EVENT_SUBMIT submit: rawEvent=$rawEvent")
        }
        _in.tryEmit(rawEvent)
    }

    private fun tickerFlow(period: Duration) = flow {
        while (true) { kotlinx.coroutines.delay(period); emit(Unit) }
    }
}
