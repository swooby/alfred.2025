package com.swooby.alfred.core.ingest

import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.core.ingest.CoalesceHistoryStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.take
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class EventIngestImplTest {
    private var originalLogSubmit = EventIngestImpl.LOG_SUBMIT
    private var originalLogFilter = EventIngestImpl.LOG_FILTER

    @BeforeTest
    fun setUp() {
        originalLogSubmit = EventIngestImpl.LOG_SUBMIT
        originalLogFilter = EventIngestImpl.LOG_FILTER
        EventIngestImpl.LOG_SUBMIT = false
        EventIngestImpl.LOG_FILTER = false
        EventIngestImpl.stats.reset()
    }

    @AfterTest
    fun tearDown() {
        EventIngestImpl.LOG_SUBMIT = originalLogSubmit
        EventIngestImpl.LOG_FILTER = originalLogFilter
        EventIngestImpl.stats.reset()
    }

    @Test
    fun coalesceSkipsRepeatUntilFingerprintChanges() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ingest =
                EventIngestImpl(
                    scope = scope,
                    debounceWindow = 10.milliseconds,
                    dedupeWindow = 20.milliseconds,
                )
            val collected = mutableListOf<EventEntity>()
            val collector =
                scope.launch {
                    ingest.out
                        .take(2)
                        .collect { collected += it }
                }

            ingest.submit(
                RawEvent(
                    event = event("first", 0.milliseconds),
                    fingerprint = "fp",
                    coalesceKey = "key",
                ),
            )
            delay(50)

            ingest.submit(
                RawEvent(
                    event = event("second", 50.milliseconds),
                    fingerprint = "fp",
                    coalesceKey = "key",
                ),
            )
            delay(100)

            ingest.submit(
                RawEvent(
                    event = event("third", 100.milliseconds),
                    fingerprint = "fp-new",
                    coalesceKey = "key",
                ),
            )
            withTimeout(500) {
                collector.join()
            }

            assertEquals(2, collected.size)
            assertEquals(listOf("first", "third"), collected.map { it.eventId })
        } finally {
            scope.cancel()
        }
    }

    private fun event(
        id: String,
        offset: Duration,
    ) = EventEntity(
        eventId = id,
        userId = "u",
        deviceId = "device",
        eventType = "type",
        eventCategory = "category",
        eventAction = "action",
        subjectEntity = "subject",
        tsStart = Instant.fromEpochMilliseconds(offset.inWholeMilliseconds),
    )

    @Test
    fun persistedHistorySuppressesImmediately() = runBlocking {
        val store = RecordingHistoryStore(initialEntries = listOf("key" to "fp"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ingest =
                EventIngestImpl(
                    scope = scope,
                    debounceWindow = 10.milliseconds,
                    dedupeWindow = 20.milliseconds,
                    coalesceHistoryStore = store,
                )
            val collected = mutableListOf<EventEntity>()
            val collector = scope.launch {
                ingest.out.collect { collected += it }
            }

            // Should be suppressed because the fingerprint was persisted.
            ingest.submit(
                RawEvent(
                    event = event("first", 0.milliseconds),
                    fingerprint = "fp",
                    coalesceKey = "key",
                ),
            )
            delay(100)
            assertEquals(0, collected.size)

            // New fingerprint should emit and persist.
            ingest.submit(
                RawEvent(
                    event = event("second", 50.milliseconds),
                    fingerprint = "fp-new",
                    coalesceKey = "key",
                ),
            )
            withTimeout(500) {
                while (collected.size < 1) {
                    delay(10)
                }
            }

            assertEquals(listOf("second"), collected.map { it.eventId })
            withTimeout(500) {
                while (store.savedSnapshots.isEmpty()) {
                    delay(10)
                }
            }
            assertEquals(listOf(listOf("key" to "fp-new")), store.savedSnapshots)

            collector.cancel()
            collector.join()
        } finally {
            scope.cancel()
        }
    }

    private class RecordingHistoryStore(
        private val initialEntries: List<Pair<String, String>> = emptyList(),
    ) : CoalesceHistoryStore {
        val savedSnapshots = mutableListOf<List<Pair<String, String>>>()

        override suspend fun load(): List<Pair<String, String>> = initialEntries

        override suspend fun save(entries: List<Pair<String, String>>) {
            savedSnapshots += entries
        }
    }
}
