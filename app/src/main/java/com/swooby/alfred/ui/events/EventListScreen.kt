package com.swooby.alfred.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.R
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SourceEventTypes
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.ui.theme.AlfredTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Instant

private const val LOAD_MORE_PREFETCH_THRESHOLD = 20

@Composable
fun EventListScreen(
    state: EventListUiState,
    userInitials: String,
    themeMode: ThemeMode,
    onQueryChange: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNotificationAccessRequested: () -> Unit,
    onApplicationInfoRequested: () -> Unit,
    onDeveloperOptionsRequested: () -> Unit,
    onAdbWirelessRequested: () -> Unit,
    onTextToSpeechSettingsRequested: () -> Unit,
    onQuitRequested: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onEventSelectionChange: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onLoadMore: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShuffleThemeRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = LocalizedStrings.drawerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    DrawerThemeModeSection(
                        selectedMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onShuffleThemeRequest = onShuffleThemeRequest
                    )
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerSettings) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onNavigateToSettings()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerNotificationAccess) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onNotificationAccessRequested()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerApplicationInfo) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onApplicationInfoRequested()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerDeveloperOptions) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onDeveloperOptionsRequested()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerAdbWireless) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onAdbWirelessRequested()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    if (BuildConfig.DEBUG) {
                        //
                        // Hide when non-DEBUG:
                        // This launches to Global TTS settings, which does not help this app's defined TTS settings.
                        //
                        NavigationDrawerItem(
                            label = { Text(text = LocalizedStrings.drawerTextToSpeech) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    onTextToSpeechSettingsRequested()
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(text = LocalizedStrings.drawerQuit) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onQuitRequested()
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        },
        modifier = modifier,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            EventListScaffold(
                state = state,
                userInitials = userInitials,
                snackbarHostState = snackbarHostState,
                onQueryChange = onQueryChange,
                onMenuClick = {
                    coroutineScope.launch { drawerState.open() }
                },
                onAvatarClick = {
                    coroutineScope.launch { drawerState.open() }
                },
                onSelectionModeChange = onSelectionModeChange,
                onSelectAll = onSelectAll,
                onUnselectAll = onUnselectAll,
                onDeleteSelected = {
                    if (!state.isPerformingAction && state.totalSelectionCount > 0) {
                        showDeleteSelectedDialog = true
                    }
                },
                onLoadMore = onLoadMore,
                onEventSelectionChange = { event, isSelected ->
                    onEventSelectionChange(event.eventId, isSelected)
                },
                onEventLongPress = { event ->
                    if (!state.isPerformingAction) {
                        if (!state.selectionMode) {
                            onSelectionModeChange(true)
                        }
                        if (!state.selectedEventIds.contains(event.eventId)) {
                            onEventSelectionChange(event.eventId, true)
                        }
                    }
                }
            )
        }
    }

    if (showDeleteSelectedDialog) {
        ActionConfirmDialog(
            title = LocalizedStrings.deleteSelectedDialogTitle,
            message = LocalizedStrings.deleteSelectedDialogMessage(state.totalSelectionCount),
            confirmLabel = LocalizedStrings.dialogDelete,
            dismissLabel = LocalizedStrings.dialogCancel,
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = {
                onDeleteSelected()
                showDeleteSelectedDialog = false
            },
            inProgress = state.isPerformingAction
        )
    }
}

@Composable
private fun DrawerThemeModeSection(
    selectedMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShuffleThemeRequest: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = LocalizedStrings.drawerThemeModeTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val options = remember {
            listOf(
                ThemeMode.LIGHT,
                ThemeMode.SYSTEM,
                ThemeMode.DARK,
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            options.forEachIndexed { index, mode ->
                val label = when (mode) {
                    ThemeMode.DARK -> LocalizedStrings.themeModeDark
                    ThemeMode.LIGHT -> LocalizedStrings.themeModeLight
                    ThemeMode.SYSTEM -> LocalizedStrings.themeModeSystem
                }
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = {
                        if (mode != selectedMode) {
                            onThemeModeChange(mode)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(text = label)
                }
            }
        }
        IconButton(onClick = onShuffleThemeRequest) {
            Icon(
                imageVector = Icons.Outlined.Shuffle,
                contentDescription = LocalizedStrings.shuffleThemeContentDescription
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EventListScaffold(
    state: EventListUiState,
    userInitials: String,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onLoadMore: () -> Unit,
    onEventSelectionChange: (EventEntity, Boolean) -> Unit,
    onEventLongPress: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val actionsEnabled = !state.isPerformingAction
    val listState = rememberLazyListState()
    val visibleRange by remember(listState, state.visibleEvents.size) {
        derivedStateOf {
            val visibleIndices = listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info ->
                    info.index.takeIf { index ->
                        index >= 0 && index < state.visibleEvents.size
                    }
                }
            val first = visibleIndices.minOrNull() ?: return@derivedStateOf null
            val last = visibleIndices.maxOrNull() ?: return@derivedStateOf null
            IntRange(first, last)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = state.selectionMode) {
                val visibleSelectedCount = if (state.isAllSelected) {
                    state.visibleEvents.size
                } else {
                    state.visibleEvents.count { event ->
                        state.selectedEventIds.contains(event.eventId)
                    }
                }
                SelectionBottomBar(
                    selectedCount = state.totalSelectionCount,
                    visibleCount = state.visibleEvents.size,
                    visibleSelectedCount = visibleSelectedCount,
                    allSelected = state.isAllSelected,
                    onSelectAll = onSelectAll,
                    onUnselectAll = onUnselectAll,
                    onDeleteSelected = onDeleteSelected,
                    onExitSelection = { onSelectionModeChange(false) },
                    actionsEnabled = actionsEnabled
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EventListHeader(
                query = state.query,
                totalEventCount = state.totalEventCount,
                inMemoryCount = state.visibleEvents.size,
                visibleRange = visibleRange,
                isRefreshing = state.isLoading,
                userInitials = userInitials,
                onQueryChange = onQueryChange,
                onMenuClick = onMenuClick,
                onAvatarClick = onAvatarClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = state.isLoading || state.isPerformingAction,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }

            EventListContent(
                state = state,
                selectionMode = state.selectionMode,
                actionsEnabled = actionsEnabled,
                onEventSelectionChange = onEventSelectionChange,
                onEventLongPress = onEventLongPress,
                listState = listState,
                onLoadMore = onLoadMore,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f, fill = true)
            )
        }
    }
}

@Composable
private fun EventListHeader(
    query: String,
    totalEventCount: Int,
    inMemoryCount: Int,
    visibleRange: IntRange?,
    isRefreshing: Boolean,
    userInitials: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerShape = RoundedCornerShape(28.dp)
    val headerContainerColor = remember(colorScheme.primaryContainer) {
        colorScheme.primaryContainer
    }
    val isHeaderBackgroundLight = headerContainerColor.luminance() > 0.5f
    val headerContentColor = colorScheme.onPrimaryContainer
    val outlineColor = if (isHeaderBackgroundLight) {
        colorScheme.primary.copy(alpha = 0.18f)
    } else {
        colorScheme.onPrimaryContainer.copy(alpha = 0.28f)
    }
    val searchContainerColor = if (isHeaderBackgroundLight) {
        colorScheme.surfaceColorAtElevation(2.dp)
    } else {
        colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
    }
    val avatarBackgroundColor = if (isHeaderBackgroundLight) {
        colorScheme.primary.copy(alpha = 0.16f)
    } else {
        colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
    }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = statusBarPadding + 8.dp, bottom = 8.dp)
                .clip(headerShape)
                .background(headerContainerColor)
                .border(width = 1.dp, color = outlineColor, shape = headerShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = LocalizedStrings.menuContentDescription,
                            tint = headerContentColor
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = LocalizedStrings.headerSubtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = headerContentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = LocalizedStrings.headerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = headerContentColor
                        )
                        Text(
                            text = visibleRange?.let { range ->
                                val start = (range.first + 1).coerceAtLeast(1)
                                val end = (range.last + 1).coerceAtLeast(start)
                                LocalizedStrings.totalCountDetailsWithRange(
                                    startIndex = start,
                                    endIndex = end,
                                    totalCount = totalEventCount,
                                    inMemoryCount = inMemoryCount,
                                )
                            } ?: LocalizedStrings.totalCountDetails(
                                totalCount = totalEventCount,
                                inMemoryCount = inMemoryCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = headerContentColor.copy(alpha = 0.7f)
                        )
                    }
                    Avatar(
                        initials = userInitials,
                        onClick = onAvatarClick,
                        backgroundColor = avatarBackgroundColor,
                        contentColor = headerContentColor
                    )
                }

                SearchField(
                    query = query,
                    isRefreshing = isRefreshing,
                    onQueryChange = onQueryChange,
                    containerColor = searchContainerColor,
                    contentColor = headerContentColor
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isRefreshing: Boolean,
    onQueryChange: (String) -> Unit,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fieldContainerColor = containerColor ?: colorScheme.surfaceColorAtElevation(3.dp)
    val fieldContentColor = contentColor ?: colorScheme.onSurface

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = LocalizedStrings.searchPlaceholder,
                color = fieldContentColor.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = fieldContentColor.copy(alpha = 0.75f)
            )
        },
        trailingIcon = {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = fieldContentColor
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fieldContainerColor,
            unfocusedContainerColor = fieldContainerColor,
            disabledContainerColor = fieldContainerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = fieldContentColor,
            focusedTextColor = fieldContentColor,
            unfocusedTextColor = fieldContentColor,
            focusedPlaceholderColor = fieldContentColor.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = fieldContentColor.copy(alpha = 0.7f),
            disabledPlaceholderColor = fieldContentColor.copy(alpha = 0.7f)
        )
    )
}

@Composable
private fun Avatar(
    initials: String,
    onClick: () -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val avatarContentDescription = LocalizedStrings.avatarContentDescription

    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = avatarContentDescription },
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun EventListContent(
    state: EventListUiState,
    selectionMode: Boolean,
    actionsEnabled: Boolean,
    onEventSelectionChange: (EventEntity, Boolean) -> Unit,
    onEventLongPress: (EventEntity) -> Unit,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.visibleEvents.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
        return
    }

    val shouldLoadMore = remember(listState, state.visibleEvents, state.canLoadMore, state.isLoadingMore) {
        derivedStateOf {
            if (!state.canLoadMore || state.isLoadingMore) return@derivedStateOf false
            if (state.visibleEvents.isEmpty()) return@derivedStateOf false
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            val triggerIndex = (state.visibleEvents.lastIndex - LOAD_MORE_PREFETCH_THRESHOLD).coerceAtLeast(0)
            lastVisibleIndex >= triggerIndex
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .collect { canLoad ->
                if (canLoad) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        itemsIndexed(state.visibleEvents) { index, event ->
            val isSelected = state.selectedEventIds.contains(event.eventId)
            TimelineEventRow(
                event = event,
                isFirst = index == 0,
                isLast = index == state.visibleEvents.lastIndex,
                selectionMode = selectionMode,
                isSelected = isSelected,
                onSelectionChange = { checked ->
                    onEventSelectionChange(event, checked)
                },
                onLongPress = { onEventLongPress(event) },
                actionsEnabled = actionsEnabled
            )
        }

        if (state.isLoadingMore) {
            item("loading_more_indicator") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 36.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = LocalizedStrings.emptyTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = LocalizedStrings.emptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = LocalizedStrings.emptyCta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimelineEventRow(
    event: EventEntity,
    isFirst: Boolean,
    isLast: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        TimelineIndicator(
            isFirst = isFirst,
            isLast = isLast,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChange,
                    enabled = actionsEnabled,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            EventCard(
                event = event,
                isSelected = isSelected,
                actionsEnabled = actionsEnabled,
                onClick = if (selectionMode) {
                    { onSelectionChange(!isSelected) }
                } else {
                    null
                },
                onLongPress = if (selectionMode) {
                    null
                } else {
                    { onLongPress() }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TimelineIndicator(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val strokeWidth = 4.dp.toPx()
        val lineColor = color.copy(alpha = 0.35f)

        if (!isFirst) {
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(centerX, 0f),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        if (!isLast) {
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX, size.height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    visibleCount: Int,
    visibleSelectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelection: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 6.dp
    ) {
        val hasVisibleEvents = visibleCount > 0
        val allVisibleSelected = hasVisibleEvents && visibleSelectedCount == visibleCount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onExitSelection,
                enabled = actionsEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = LocalizedStrings.exitSelectionLabel
                )
            }
            Text(
                text = LocalizedStrings.selectionCountLabel(selectedCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = if (allSelected || allVisibleSelected) onUnselectAll else onSelectAll,
                enabled = actionsEnabled && (hasVisibleEvents || allSelected),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = null
                    )
                    val label = if (allSelected || allVisibleSelected) {
                        LocalizedStrings.unselectAllLabel
                    } else {
                        LocalizedStrings.selectAllLabel
                    }
                    Text(
                        text = label,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            FilledTonalButton(
                onClick = onDeleteSelected,
                enabled = actionsEnabled && selectedCount > 0,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = LocalizedStrings.deleteSelectedLabel,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ActionConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    inProgress: Boolean
) {
    AlertDialog(
        onDismissRequest = {
            if (!inProgress) {
                onDismiss()
            }
        },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !inProgress
            ) {
                Text(text = dismissLabel)
            }
        }
    )
}

internal object LocalizedStrings {
    val drawerTitle: String
        @Composable get() = stringResource(R.string.event_list_drawer_title)

    val drawerSettings: String
        @Composable get() = stringResource(R.string.event_list_drawer_settings)

    val drawerNotificationAccess: String
        @Composable get() = stringResource(R.string.event_list_drawer_notification_access)

    val drawerApplicationInfo: String
        @Composable get() = stringResource(R.string.event_list_drawer_application_info)

    val drawerDeveloperOptions: String
        @Composable get() = stringResource(R.string.event_list_drawer_developer_options)

    val drawerAdbWireless: String
        @Composable get() = stringResource(R.string.event_list_drawer_adb_wireless)

    val drawerTextToSpeech: String
        @Composable get() = stringResource(R.string.event_list_drawer_text_to_speech)

    val drawerQuit: String
        @Composable get() = stringResource(R.string.event_list_drawer_quit)

    val drawerThemeModeTitle: String
        @Composable get() = stringResource(R.string.event_list_drawer_theme_mode_title)

    val themeModeDark: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_dark)

    val themeModeLight: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_light)

    val themeModeSystem: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_system)

    val shuffleThemeContentDescription: String
        @Composable get() = stringResource(R.string.event_list_theme_shuffle_cd)

    val menuContentDescription: String
        @Composable get() = stringResource(R.string.event_list_menu_cd)

    val avatarContentDescription: String
        @Composable get() = stringResource(R.string.event_list_avatar_cd)

    val searchPlaceholder: String
        @Composable get() = stringResource(R.string.event_list_search_placeholder)

    val headerSubtitle: String
        @Composable get() = stringResource(R.string.event_list_header_subtitle)

    val headerTitle: String
        @Composable get() = stringResource(R.string.event_list_header_title)

    val componentMediaSession: String
        @Composable get() = stringResource(R.string.event_list_component_media_session)
    val componentNotification: String
        @Composable get() = stringResource(R.string.event_list_component_notification)
    val componentGeneric: String
        @Composable get() = stringResource(R.string.event_list_component_generic)
    val unknownApp: String
        @Composable get() = stringResource(R.string.event_list_unknown_app)
    val mediaUnknownTitle: String
        @Composable get() = stringResource(R.string.event_list_media_unknown_title)

    @Composable
    fun componentLabel(component: String?): String = when (component) {
        SourceComponentIds.MEDIA_SOURCE -> componentMediaSession
        SourceComponentIds.NOTIFICATION_SOURCE -> componentNotification
        null -> componentGeneric
        else -> component.replace('_', ' ').replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    @Composable
    fun mediaPlaybackState(action: String): String {
        val normalized = action.lowercase(Locale.getDefault())
        return when (normalized) {
            "start", "play", "playing" -> stringResource(R.string.event_list_media_state_start)
            "stop", "stopped" -> stringResource(R.string.event_list_media_state_stop)
            "pause", "paused" -> stringResource(R.string.event_list_media_state_pause)
            "resume", "resumed" -> stringResource(R.string.event_list_media_state_resume)
            else -> stringResource(
                R.string.event_list_media_state_generic,
                action.replace('_', ' ').replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            )
        }
    }

    @Composable
    fun mediaPlayedDuration(duration: String): String =
        stringResource(R.string.event_list_media_played_duration, duration)

    @Composable
    fun mediaRouteLabel(route: String): String =
        stringResource(R.string.event_list_media_route, route)

    @Composable
    fun totalCountDetails(totalCount: Int, inMemoryCount: Int) =
        stringResource(R.string.event_list_total_count_details, totalCount, inMemoryCount)

    @Composable
    fun totalCountDetailsWithRange(
        startIndex: Int,
        endIndex: Int,
        totalCount: Int,
        inMemoryCount: Int,
    ): String {
        return if (startIndex == endIndex) {
            stringResource(
                R.string.event_list_total_count_details_single,
                startIndex,
                totalCount,
                inMemoryCount,
            )
        } else {
            stringResource(
                R.string.event_list_total_count_details_range,
                startIndex,
                endIndex,
                totalCount,
                inMemoryCount,
            )
        }
    }

    @Composable
    fun selectionCountLabel(count: Int) = stringResource(R.string.event_list_selection_count, count)

    val selectAllLabel: String
        @Composable get() = stringResource(R.string.event_list_select_all)

    val unselectAllLabel: String
        @Composable get() = stringResource(R.string.event_list_unselect_all)

    val deleteSelectedLabel: String
        @Composable get() = stringResource(R.string.event_list_delete_selected)

    val exitSelectionLabel: String
        @Composable get() = stringResource(R.string.event_list_exit_selection)

    val deleteSelectedDialogTitle: String
        @Composable get() = stringResource(R.string.event_list_delete_selected_title)

    @Composable
    fun deleteSelectedDialogMessage(count: Int) = stringResource(R.string.event_list_delete_selected_message, count)

    val dialogCancel: String
        @Composable get() = stringResource(R.string.event_list_dialog_cancel)

    val dialogDelete: String
        @Composable get() = stringResource(R.string.event_list_dialog_delete)

    val emptyTitle: String
        @Composable get() = stringResource(R.string.event_list_empty_title)

    val emptyBody: String
        @Composable get() = stringResource(R.string.event_list_empty_body)

    val emptyCta: String
        @Composable get() = stringResource(R.string.event_list_empty_hint)

    @Composable
    fun eventTypeLabel(value: String) = stringResource(R.string.event_list_event_type, value)

    @Composable
    fun entityIdLabel(value: String) = stringResource(R.string.event_list_entity_id, value)

    @Composable
    fun tagsLabel(value: String) = stringResource(R.string.event_list_tags, value)

    val showDetailsLabel: String
        @Composable get() = stringResource(R.string.event_list_expand)
    val hideDetailsLabel: String
        @Composable get() = stringResource(R.string.event_list_collapse)
    val labelApp: String
        @Composable get() = stringResource(R.string.event_list_label_app)
    val labelPackage: String
        @Composable get() = stringResource(R.string.event_list_label_package)
    val labelCategory: String
        @Composable get() = stringResource(R.string.event_list_label_category)
    val labelTemplate: String
        @Composable get() = stringResource(R.string.event_list_label_template)
    val labelShortcut: String
        @Composable get() = stringResource(R.string.event_list_label_shortcut)
    val labelLocus: String
        @Composable get() = stringResource(R.string.event_list_label_locus)
    val labelChannelName: String
        @Composable get() = stringResource(R.string.event_list_label_channel_name)
    val labelChannel: String
        @Composable get() = stringResource(R.string.event_list_label_channel)
    val labelImportance: String
        @Composable get() = stringResource(R.string.event_list_label_importance)
    val labelRank: String
        @Composable get() = stringResource(R.string.event_list_label_rank)
    val labelUserSentiment: String
        @Composable get() = stringResource(R.string.event_list_label_user_sentiment)
    val labelVisibility: String
        @Composable get() = stringResource(R.string.event_list_label_visibility)
    val labelUser: String
        @Composable get() = stringResource(R.string.event_list_label_user)
    val labelGroup: String
        @Composable get() = stringResource(R.string.event_list_label_group)
    val labelTimeout: String
        @Composable get() = stringResource(R.string.event_list_label_timeout)
    val labelTicker: String
        @Composable get() = stringResource(R.string.event_list_label_ticker)
    val labelConversation: String
        @Composable get() = stringResource(R.string.event_list_label_conversation)
    val labelPeople: String
        @Composable get() = stringResource(R.string.event_list_label_people)
    val labelActions: String
        @Composable get() = stringResource(R.string.event_list_label_actions)
    val labelIntents: String
        @Composable get() = stringResource(R.string.event_list_label_intents)
    val labelStyle: String
        @Composable get() = stringResource(R.string.event_list_label_style)
    val labelAttachments: String
        @Composable get() = stringResource(R.string.event_list_label_attachments)
    val labelMetrics: String
        @Composable get() = stringResource(R.string.event_list_label_metrics)
    val labelRefs: String
        @Composable get() = stringResource(R.string.event_list_label_refs)
    val labelIntegrity: String
        @Composable get() = stringResource(R.string.event_list_label_integrity)
    val labelIntegrityHash: String
        @Composable get() = stringResource(R.string.event_list_label_integrity_hash)
    val labelIntegrityFields: String
        @Composable get() = stringResource(R.string.event_list_label_integrity_fields)
    val labelFlags: String
        @Composable get() = stringResource(R.string.event_list_label_flags)
    val labelRawExtras: String
        @Composable get() = stringResource(R.string.event_list_label_raw_extras)
    val labelSubjectLines: String
        @Composable get() = stringResource(R.string.event_list_label_subject_lines)
    val labelEventType: String
        @Composable get() = stringResource(R.string.event_list_label_event_type)
    val labelEntityId: String
        @Composable get() = stringResource(R.string.event_list_label_entity_id)
    val labelParentId: String
        @Composable get() = stringResource(R.string.event_list_label_parent_id)
    val labelFingerprint: String
        @Composable get() = stringResource(R.string.event_list_label_fingerprint)
    val sectionSummary: String
        @Composable get() = stringResource(R.string.event_list_section_identity)
    val sectionContext: String
        @Composable get() = stringResource(R.string.event_list_section_context)
    val sectionBubble: String
        @Composable get() = stringResource(R.string.event_list_section_bubble)
    val sectionEvent: String
        @Composable get() = stringResource(R.string.event_list_section_event)
    val bubbleHeight: String
        @Composable get() = stringResource(R.string.event_list_bubble_height)
    val bubbleAutoExpand: String
        @Composable get() = stringResource(R.string.event_list_bubble_auto_expand)
    val bubbleSuppress: String
        @Composable get() = stringResource(R.string.event_list_bubble_suppress)
    val flagColorized: String
        @Composable get() = stringResource(R.string.event_list_flag_colorized)
    val flagOnlyAlertOnce: String
        @Composable get() = stringResource(R.string.event_list_flag_only_alert_once)
    val flagOngoing: String
        @Composable get() = stringResource(R.string.event_list_flag_ongoing)
    val flagClearable: String
        @Composable get() = stringResource(R.string.event_list_flag_clearable)
    val flagUnclearable: String
        @Composable get() = stringResource(R.string.event_list_flag_unclearable)
    val flagGroupSummary: String
        @Composable get() = stringResource(R.string.event_list_flag_group_summary)
    val flagShowWhen: String
        @Composable get() = stringResource(R.string.event_list_flag_show_when)
    val flagAmbient: String
        @Composable get() = stringResource(R.string.event_list_flag_ambient)
    val flagSuspended: String
        @Composable get() = stringResource(R.string.event_list_flag_suspended)
    val flagBadge: String
        @Composable get() = stringResource(R.string.event_list_flag_badge)
    val flagConversation: String
        @Composable get() = stringResource(R.string.event_list_flag_conversation)
}

@Preview(showBackground = true)
@Composable
private fun EventListPreview() {
    val now = Instant.fromEpochMilliseconds(java.time.Instant.now().toEpochMilli())
    val sampleEvents = listOf(
        EventEntity(
            eventId = "evt-1",
            userId = "u_local",
            deviceId = "pixel-9",
            eventType = "notification",
            eventCategory = "Inbox",
            eventAction = "Received new message",
            subjectEntity = "Email",
            subjectEntityId = "com.mail:42",
            tsStart = now,
            tags = listOf("priority", "gmail"),
            attributes = buildJsonObject {
                put("actor", buildJsonObject {
                    put("appLabel", JsonPrimitive("Mail"))
                    put("packageName", JsonPrimitive("com.mail"))
                })
                put("subject", buildJsonObject {
                    put("title", JsonPrimitive("Project Apollo"))
                    put("text", JsonPrimitive("New update from Alex"))
                    put("conversationTitle", JsonPrimitive("Team chat"))
                })
                put("context", buildJsonObject {
                    put("category", JsonPrimitive("email"))
                    put("channelId", JsonPrimitive("inbox"))
                    put("rankingInfo", buildJsonObject {
                        put("importance", JsonPrimitive(4))
                        put("isConversation", JsonPrimitive(true))
                    })
                })
                put("traits", buildJsonObject {
                    put("template", JsonPrimitive("MessagingStyle"))
                    put("people", buildJsonArray {
                        add(buildJsonObject { put("name", JsonPrimitive("Alex")) })
                    })
                    put("actions", buildJsonArray {
                        add(buildJsonObject { put("title", JsonPrimitive("Reply")) })
                        add(buildJsonObject { put("title", JsonPrimitive("Mark read")) })
                    })
                })
                put("refs", buildJsonObject {
                    put("key", JsonPrimitive("notification-key"))
                    put("user", JsonPrimitive("UserHandle{0}"))
                })
                put("subjectLines", buildJsonArray {
                    add(JsonPrimitive("Latest update includes timeline adjustments."))
                })
            },
            metrics = buildJsonObject {
                put("actionsCount", JsonPrimitive(2))
                put("peopleCount", JsonPrimitive(1))
            }
        ),
        EventEntity(
            eventId = "evt-2",
            userId = "u_local",
            deviceId = "pixel-9",
            appPkg = "com.spotify.music",
            component = SourceComponentIds.MEDIA_SOURCE,
            eventType = SourceEventTypes.MEDIA_STOP,
            eventCategory = "media",
            eventAction = "stop",
            subjectEntity = "track",
            tsStart = now,
            tsEnd = now,
            tags = listOf("music"),
            attributes = buildJsonObject {
                put("actor", buildJsonObject {
                    put("appLabel", JsonPrimitive("Spotify"))
                    put("packageName", JsonPrimitive("com.spotify.music"))
                })
                put("title", JsonPrimitive("Beyond the Sun"))
                put("artist", JsonPrimitive("Valentina Miras"))
                put("album", JsonPrimitive("Starlight Echoes"))
                put("source_app", JsonPrimitive("com.spotify.music"))
                put("output_route", JsonPrimitive("Pixel Buds"))
            },
            metrics = buildJsonObject {
                put("played_ms", JsonPrimitive(192000))
            }
        ),
        EventEntity(
            eventId = "evt-3",
            userId = "u_local",
            deviceId = "pixel-9",
            appPkg = "com.phone",
            component = "call_log",
            eventType = "call",
            eventCategory = "Communications",
            eventAction = "Missed call",
            subjectEntity = "Carol Micek",
            tsStart = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3_600_000),
            tags = listOf("work"),
            attributes = buildJsonObject {
                put("actor", buildJsonObject {
                    put("appLabel", JsonPrimitive("Dialer"))
                    put("packageName", JsonPrimitive("com.phone"))
                })
                put("context", buildJsonObject {
                    put("category", JsonPrimitive("call"))
                    put("rankingInfo", buildJsonObject {
                        put("importance", JsonPrimitive(3))
                    })
                })
            }
        )
    )

    AlfredTheme {
        EventListScreen(
            state = EventListUiState(
                query = "",
                allEvents = sampleEvents,
                visibleEvents = sampleEvents
            ),
            userInitials = "A",
            themeMode = ThemeMode.SYSTEM,
            onQueryChange = {},
            onNavigateToSettings = {},
            onNotificationAccessRequested = {},
            onApplicationInfoRequested = {},
            onDeveloperOptionsRequested = {},
            onAdbWirelessRequested = {},
            onTextToSpeechSettingsRequested = {},
            onQuitRequested = {},
            onSelectionModeChange = {},
            onEventSelectionChange = { _, _ -> },
            onSelectAll = {},
            onUnselectAll = {},
            onDeleteSelected = {},
            onLoadMore = {},
            onThemeModeChange = {},
            onShuffleThemeRequest = {}
        )
    }
}
