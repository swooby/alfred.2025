package com.swooby.alfred.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swooby.alfred.core.profile.AudioProfileController
import com.swooby.alfred.core.profile.AudioProfileId
import com.swooby.alfred.core.profile.AudioProfileUiState
import com.swooby.alfred.core.profile.HeadsetEvent
import com.swooby.alfred.data.EventDao
import com.swooby.alfred.data.EventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * UI state container for the event list screen.
 */
data class EventListUiState(
    val query: String = "",
    val allEvents: List<EventEntity> = emptyList(),
    val visibleEvents: List<EventEntity> = emptyList(),
    val totalEventCount: Int = 0,
    val isLoading: Boolean = false,
    val isPerformingAction: Boolean = false,
    val errorMessage: String? = null,
    val selectionMode: Boolean = false,
    val selectedEventIds: Set<String> = emptySet(),
    val totalSelectionCount: Int = 0,
    val isAllSelected: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val audioProfileUiState: AudioProfileUiState = AudioProfileUiState(),
)

@Suppress("unused")
class EventListViewModel(
    private val eventDao: EventDao,
    private val userId: String,
    private val pageSize: Int = DEFAULT_EVENT_PAGE_SIZE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val audioProfileController: AudioProfileController,
) : ViewModel() {
    private val _state = MutableStateFlow(EventListUiState(isLoading = true))
    private val pageLimit = MutableStateFlow(pageSize)
    val state: StateFlow<EventListUiState> = _state.asStateFlow()

    init {
        observeEvents()
        observeEventCount()
        refresh()
        observeAudioProfiles()
    }

    fun onQueryChange(value: String) {
        val normalized = value.take(200)
        _state.update { current ->
            val filtered = applyFilter(current.allEvents, normalized)
            current.copy(query = normalized, visibleEvents = filtered)
        }
    }

    fun refresh() {
        _state.update {
            it.copy(
                isLoading = true,
                isPerformingAction = false,
                errorMessage = null,
                isLoadingMore = false,
            )
        }
        pageLimit.value = pageSize
    }

    fun setSelectionMode(enabled: Boolean) {
        _state.update { current ->
            if (enabled) {
                current.copy(selectionMode = true)
            } else {
                current.copy(
                    selectionMode = false,
                    selectedEventIds = emptySet(),
                    isAllSelected = false,
                    totalSelectionCount = 0,
                )
            }
        }
    }

    fun selectAll() {
        _state.update { current ->
            val visibleIds = current.visibleEvents.map(EventEntity::eventId).toSet()
            if (visibleIds.isEmpty()) {
                current
            } else {
                val total = determineSelectionCount(current.totalEventCount, current.allEvents.size)
                current.copy(
                    selectionMode = true,
                    selectedEventIds = visibleIds,
                    isAllSelected = true,
                    totalSelectionCount = total,
                )
            }
        }
    }

    fun unselectAll() {
        _state.update { current ->
            current.copy(
                selectionMode = false,
                selectedEventIds = emptySet(),
                isAllSelected = false,
                totalSelectionCount = 0,
            )
        }
    }

    fun setEventSelection(
        eventId: String,
        isSelected: Boolean,
    ) {
        _state.update { current ->
            if (current.allEvents.none { it.eventId == eventId }) {
                current
            } else {
                val updated = current.selectedEventIds.toMutableSet()
                if (isSelected) {
                    updated.add(eventId)
                } else {
                    updated.remove(eventId)
                }
                val hasSelection = updated.isNotEmpty()
                current.copy(
                    selectionMode = if (isSelected) true else hasSelection && current.selectionMode,
                    selectedEventIds = updated,
                    isAllSelected = false,
                    totalSelectionCount = updated.size,
                )
            }
        }
    }

    fun deleteSelected() {
        val currentState = state.value
        val deleteAll = currentState.isAllSelected && currentState.totalSelectionCount > 0
        val eventIds = currentState.selectedEventIds
        if (!deleteAll && eventIds.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _state.update {
                it.copy(
                    isPerformingAction = true,
                    errorMessage = null,
                )
            }
            try {
                if (deleteAll) {
                    eventDao.clearAllForUser(userId)
                } else {
                    eventDao.deleteByIds(userId, eventIds.toList())
                }
                _state.update { current ->
                    current.copy(
                        isPerformingAction = false,
                        selectedEventIds = emptySet(),
                        selectionMode = false,
                        isAllSelected = false,
                        totalSelectionCount = 0,
                    )
                }
            } catch (t: Throwable) {
                _state.update { current ->
                    current.copy(
                        isPerformingAction = false,
                        errorMessage = t.message ?: t::class.simpleName ?: "error",
                    )
                }
            }
        }
    }

    fun selectAudioProfile(profileId: AudioProfileId) {
        viewModelScope.launch {
            audioProfileController.selectProfile(profileId)
        }
    }

    fun refreshAudioProfilePermissions() {
        audioProfileController.refreshBluetoothPermission()
    }

    fun headsetEvents(): Flow<HeadsetEvent> = audioProfileController.headsetEvents

    private fun observeEvents() {
        viewModelScope.launch {
            pageLimit
                .flatMapLatest { limit ->
                    eventDao
                        .observeRecent(userId, limit)
                        .map { limit to it }
                }.flowOn(ioDispatcher)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        _state.update { current ->
                            current.copy(
                                isLoading = false,
                                isPerformingAction = false,
                                errorMessage = cause.message ?: cause::class.simpleName ?: "error",
                            )
                        }
                        val multiplier = (attempt + 1).coerceAtMost(MAX_RETRY_MULTIPLIER)
                        delay(RETRY_BASE_DELAY.inWholeMilliseconds * multiplier)
                        true
                    }
                }.collect { (limit, events) ->
                    _state.update { current ->
                        val existingIds = events.map(EventEntity::eventId).toSet()
                        val sanitizedSelection =
                            when {
                                current.isAllSelected -> existingIds
                                else -> current.selectedEventIds.filter { it in existingIds }.toSet()
                            }
                        val filtered = applyFilter(events, current.query)
                        val hasEvents = events.isNotEmpty()
                        val normalizedSelectionMode =
                            when {
                                !hasEvents -> false
                                current.isAllSelected -> true
                                else -> current.selectionMode && sanitizedSelection.isNotEmpty()
                            }
                        val totalSelectionCount =
                            when {
                                !hasEvents -> 0
                                current.isAllSelected -> determineSelectionCount(current.totalEventCount, events.size)
                                else -> sanitizedSelection.size
                            }
                        val moreAvailableByLimit = events.size >= limit
                        val canLoadMore =
                            moreAvailableByLimit &&
                                (current.totalEventCount == 0 || events.size < current.totalEventCount)
                        current.copy(
                            isLoading = false,
                            isPerformingAction = false,
                            allEvents = events,
                            visibleEvents = filtered,
                            errorMessage = null,
                            selectedEventIds = sanitizedSelection,
                            selectionMode = normalizedSelectionMode,
                            isAllSelected = current.isAllSelected && hasEvents,
                            totalSelectionCount = totalSelectionCount,
                            isLoadingMore = false,
                            canLoadMore = canLoadMore,
                        )
                    }
                }
        }
    }

    private fun observeAudioProfiles() {
        viewModelScope.launch {
            audioProfileController.uiState.collect { profileState ->
                _state.update { current ->
                    current.copy(audioProfileUiState = profileState)
                }
            }
        }
    }

    private fun observeEventCount() {
        viewModelScope.launch {
            eventDao
                .observeCount(userId)
                .flowOn(ioDispatcher)
                .collect { count ->
                    val total = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    _state.update { current ->
                        val canLoadMore =
                            current.allEvents.size >= pageLimit.value &&
                                (total == 0 || current.allEvents.size < total)
                        val totalSelectionCount =
                            if (current.isAllSelected) {
                                determineSelectionCount(total, current.allEvents.size)
                            } else {
                                current.totalSelectionCount
                            }
                        if (
                            current.totalEventCount == total &&
                            current.canLoadMore == canLoadMore &&
                            current.totalSelectionCount == totalSelectionCount
                        ) {
                            current
                        } else {
                            current.copy(
                                totalEventCount = total,
                                canLoadMore = canLoadMore,
                                totalSelectionCount = totalSelectionCount,
                            )
                        }
                    }
                }
        }
    }

    fun loadMore() {
        val currentState = state.value
        if (!currentState.canLoadMore || currentState.isLoadingMore || currentState.isPerformingAction) {
            return
        }
        _state.update {
            it.copy(isLoadingMore = true)
        }
        pageLimit.update { current ->
            val base = if (current <= 0) pageSize else current
            val next = base.toLong() + pageSize.toLong()
            next.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun applyFilter(
        events: List<EventEntity>,
        query: String,
    ): List<EventEntity> {
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
        private val audioProfileController: AudioProfileController,
        private val pageSize: Int = DEFAULT_EVENT_PAGE_SIZE,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EventListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EventListViewModel(
                    eventDao = eventDao,
                    userId = userId,
                    pageSize = pageSize,
                    ioDispatcher = ioDispatcher,
                    audioProfileController = audioProfileController,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }

    private companion object {
        private const val MAX_RETRY_MULTIPLIER = 6L
        private val RETRY_BASE_DELAY = 1.seconds
        private const val DEFAULT_EVENT_PAGE_SIZE = 100

        private fun determineSelectionCount(
            totalKnown: Int,
            loadedCount: Int,
        ): Int = if (totalKnown > 0) totalKnown else loadedCount
    }
}
