package com.swooby.alfred.core.ingest

import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.data.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RawEvent(
    val event: EventEntity,
    val fingerprint: String? = null,
    val coalesceKey: String? = null,
) {
    override fun toString(): String =
        StringBuilder("{")
            .append(" fingerprint=")
            .append(FooString.quote(fingerprint))
            .append(",")
            .append(" coalesceKey=")
            .append(FooString.quote(coalesceKey))
            .append(",")
            .append(" event=")
            .append(event)
            .append("}")
            .toString()
}

interface EventIngest {
    val out: SharedFlow<EventEntity>

    fun submit(rawEvent: RawEvent)
}

class EventIngestImpl(
    private val scope: CoroutineScope,
    private val debounceWindow: Duration = 200.milliseconds,
    private val dedupeWindow: Duration = 2_000.milliseconds,
    private val coalesceHistoryStore: CoalesceHistoryStore = CoalesceHistoryStore.InMemory,
) : EventIngest {
    companion object {
        private val TAG = FooLog.TAG(EventIngestImpl::class.java)

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions", "RedundantSuppression", "UNREACHABLE_CODE")
        var LOG_SUBMIT = true && BuildConfig.DEBUG
            internal set

        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions", "RedundantSuppression", "UNREACHABLE_CODE")
        var LOG_FILTER = true && BuildConfig.DEBUG
            internal set

        private const val COALESCE_HISTORY_CAPACITY = 512
        private const val LOAD_FACTOR = 0.75f

        internal val stats = FilterStats()
    }

    @Suppress("ktlint:standard:backing-property-naming") // can't be "in" because that is a key word
    private val _in = MutableSharedFlow<RawEvent>(extraBufferCapacity = 1024)
    private val _out = MutableSharedFlow<EventEntity>(replay = 0, extraBufferCapacity = 256)
    override val out: SharedFlow<EventEntity> = _out.asSharedFlow()

    private val recentFingerprints = ArrayDeque<Pair<Long, String>>()
    private val recentCoalesce = mutableMapOf<String, RawEvent>()
    private val coalesceHistory = LinkedHashMap<String, String>(COALESCE_HISTORY_CAPACITY, LOAD_FACTOR, true)

    init {
        val persisted = runBlocking { coalesceHistoryStore.load() }
        persisted.takeLast(COALESCE_HISTORY_CAPACITY).forEach { (key, fingerprint) ->
            coalesceHistory[key] = fingerprint
        }

        scope.launch(Dispatchers.Default) {
            _in
                .onEach { raw ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (raw.coalesceKey != null) {
                        val previous = recentCoalesce.put(raw.coalesceKey, raw)
                        if (LOG_FILTER) {
                            if (previous != null) {
                                FooLog.d(TAG, "#EVENT_FILTER debounce: drop coalesceKey=${FooString.quote(previous.coalesceKey)} historySize=${coalesceHistory.size} stats=${stats.snapshot()}")
                            }
                        }
                        return@onEach
                    }
                    emitIfNotDuplicate(raw, now)
                }.launchIn(this)

            tickerFlow(debounceWindow)
                .onEach {
                    val snapshot = recentCoalesce.toMap()
                    recentCoalesce.clear()
                    val now = Clock.System.now().toEpochMilliseconds()
                    snapshot.values.forEach { emitIfNotDuplicate(it, now) }
                }.launchIn(this)
        }
    }

    private suspend fun emitIfNotDuplicate(
        raw: RawEvent,
        nowMs: Long,
    ) {
        raw.coalesceKey?.let { key ->
            val coalesceFingerprint = raw.fingerprint ?: raw.event.integritySig
            if (coalesceFingerprint != null && coalesceHistory[key] == coalesceFingerprint) {
                stats.coalesceDropped()
                if (LOG_FILTER) {
                    FooLog.d(TAG, "#EVENT_FILTER coalesce: skip coalesceKey=${FooString.quote(key)} fingerprint=${FooString.quote(coalesceFingerprint)} historySize=${coalesceHistory.size} stats=${stats.snapshot()}")
                }
                return
            }
        }

        raw.fingerprint?.let { fingerprint ->
            while (recentFingerprints.isNotEmpty() &&
                nowMs - recentFingerprints.first().first > dedupeWindow.inWholeMilliseconds
            ) {
                recentFingerprints.removeFirst()
            }
            if (recentFingerprints.any { it.second == fingerprint }) {
                stats.fingerprintDropped()
                if (LOG_FILTER) {
                    FooLog.d(TAG, "#EVENT_FILTER dedupe: skip fingerprint=${FooString.quote(fingerprint)} historySize=${coalesceHistory.size} stats=${stats.snapshot()}")
                }
                return
            }
            recentFingerprints.addLast(nowMs to fingerprint)
        }

        val e = raw.event
        val normalized =
            e.copy(
                ingestAt = e.ingestAt ?: Clock.System.now(),
                durationMs =
                    e.durationMs ?: e.tsEnd?.let { end ->
                        end.toEpochMilliseconds() - e.tsStart.toEpochMilliseconds()
                    },
            )
        stats.emitted()
        if (LOG_FILTER) {
            FooLog.i(TAG, "#EVENT_FILTER emit: fingerprint=${FooString.quote(raw.fingerprint)} coalesceKey=${FooString.quote(raw.coalesceKey)} historySize=${coalesceHistory.size} stats=${stats.snapshot()} event=$normalized")
        }
        _out.emit(normalized)
        raw.coalesceKey?.let { key ->
            val coalesceFingerprint = raw.fingerprint ?: raw.event.integritySig
            if (coalesceFingerprint != null) {
                recordCoalesceFingerprint(key, coalesceFingerprint)
            }
        }
    }

    override fun submit(rawEvent: RawEvent) {
        if (LOG_SUBMIT) {
            FooLog.d(TAG, "#EVENT_SUBMIT submit: historySize=${coalesceHistory.size} stats=${stats.snapshot()} rawEvent=$rawEvent")
        }
        _in.tryEmit(rawEvent)
    }

    private fun tickerFlow(period: Duration) =
        flow {
            while (true) {
                kotlinx.coroutines.delay(period)
                emit(Unit)
            }
        }

    private fun recordCoalesceFingerprint(
        key: String,
        fingerprint: String,
    ) {
        val existing = coalesceHistory[key]
        if (existing == fingerprint) {
            return
        }
        coalesceHistory[key] = fingerprint
        if (coalesceHistory.size > COALESCE_HISTORY_CAPACITY) {
            val iterator = coalesceHistory.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        schedulePersist()
    }

    private fun schedulePersist() {
        val snapshot = coalesceHistory.entries.map { it.key to it.value }
        scope.launch(Dispatchers.IO) {
            coalesceHistoryStore.save(snapshot)
        }
    }
}

internal class FilterStats {
    private val emittedCount = AtomicLong(0)
    private val coalesceDropCount = AtomicLong(0)
    private val fingerprintDropCount = AtomicLong(0)

    val emitted: Long
        get() = emittedCount.get()
    val coalesceDrops: Long
        get() = coalesceDropCount.get()
    val fingerprintDrops: Long
        get() = fingerprintDropCount.get()

    fun emitted() {
        emittedCount.incrementAndGet()
    }

    fun coalesceDropped() {
        coalesceDropCount.incrementAndGet()
    }

    fun fingerprintDropped() {
        fingerprintDropCount.incrementAndGet()
    }

    fun snapshot(): String = "emit=$emitted coalesceDrops=$coalesceDrops fingerprintDrops=$fingerprintDrops"

    fun reset() {
        emittedCount.set(0)
        coalesceDropCount.set(0)
        fingerprintDropCount.set(0)
    }
}
