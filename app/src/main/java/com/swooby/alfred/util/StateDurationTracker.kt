package com.swooby.alfred.util

import kotlin.time.Instant

/**
 * Tracks how long a keyed state was active before transitioning to a new state.
 *
 * The tracker stores the last observed [state] along with its [timestamp]. When the same key
 * transitions to a different state, [record] returns the previous state and the elapsed duration.
 * This makes it easy to speak or log durations when the state changes (e.g. screen on/off, wifi
 * connect/disconnect).
 */
class StateDurationTracker<K, S> {
    data class Transition<S>(
        val previousState: S,
        val durationMs: Long,
    )

    private data class Entry<S>(
        val state: S,
        val timestamp: Instant,
    )

    private val entries = mutableMapOf<K, Entry<S>>()

    /**
     * Records that [key] has transitioned to [newState] at [timestamp].
     *
     * @return the previous state and how long it was active, or `null` if this is the first
     * observation or the state hasn't changed.
     */
    fun record(
        key: K,
        newState: S,
        timestamp: Instant,
    ): Transition<S>? {
        val previous = entries.put(key, Entry(newState, timestamp))
        if (previous == null || previous.state == newState) {
            return null
        }
        val duration = timestamp.toEpochMilliseconds() - previous.timestamp.toEpochMilliseconds()
        val clampedDuration = if (duration >= 0) duration else 0L
        return Transition(previous.state, clampedDuration)
    }

    /**
     * Removes tracking information for [key], if present.
     */
    fun reset(key: K) {
        entries.remove(key)
    }

    /**
     * Clears all tracked states.
     */
    fun clear() {
        entries.clear()
    }
}
