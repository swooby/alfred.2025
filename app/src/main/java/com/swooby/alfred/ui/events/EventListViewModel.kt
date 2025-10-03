package com.swooby.alfred.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swooby.alfred.data.EventDao
import com.swooby.alfred.data.EventEntity
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * UI state container for the event list screen.
 */
data class EventListUiState(
    val query: String = "",
    val allEvents: List<EventEntity> = emptyList(),
    val visibleEvents: List<EventEntity> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdated: Instant? = null,
    val errorMessage: String? = null,
)

class EventListViewModel(
    private val eventDao: EventDao,
    private val userId: String,
    private val lookback: Duration = 7.days,
    private val limit: Int = 500,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(EventListUiState(isLoading = true))
    val state: StateFlow<EventListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        val normalized = value.take(200)
        _state.update { current ->
            val filtered = applyFilter(current.allEvents, normalized)
            current.copy(query = normalized, visibleEvents = filtered)
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val now = clock.now()
                val fromEpochMillis = now.toEpochMilliseconds() - lookback.inWholeMilliseconds
                val from = Instant.fromEpochMilliseconds(fromEpochMillis)
                val events = eventDao.listByTime(userId, from, now, limit)
                _state.update { current ->
                    val filtered = applyFilter(events, current.query)
                    current.copy(
                        isLoading = false,
                        allEvents = events,
                        visibleEvents = filtered,
                        lastUpdated = now,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = t.message ?: t::class.simpleName ?: "error",
                    )
                }
            }
        }
    }

    private fun applyFilter(events: List<EventEntity>, query: String): List<EventEntity> {
        if (query.isBlank()) return events
        val locale = Locale.getDefault()
        val normalized = query.trim().lowercase(locale)
        return events.filter { event ->
            buildList {
                add(event.eventType)
                add(event.eventCategory)
                add(event.eventAction)
                add(event.subjectEntity)
                event.subjectEntityId?.let(::add)
                event.subjectParentId?.let(::add)
                addAll(event.tags)
            }.any { candidate ->
                candidate.lowercase(locale).contains(normalized)
            }
        }
    }

    class Factory(
        private val eventDao: EventDao,
        private val userId: String,
        private val lookback: Duration = 7.days,
        private val limit: Int = 500,
        private val clock: Clock = Clock.System,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EventListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EventListViewModel(eventDao, userId, lookback, limit, clock) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${'$'}modelClass")
        }
    }
}
