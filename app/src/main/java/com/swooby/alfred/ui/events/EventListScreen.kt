package com.swooby.alfred.ui.events

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred.R
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.settings.ThemeMode
import com.swooby.alfred.ui.theme.AlfredTheme
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Composable
fun EventListScreen(
    state: EventListUiState,
    userInitials: String,
    themeMode: ThemeMode,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onEventSelectionChange: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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
    val backgroundBrush = remember(
        colorScheme.surface,
        colorScheme.primaryContainer,
        colorScheme.background
    ) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.surface,
                colorScheme.primaryContainer.copy(alpha = 0.35f),
                colorScheme.background
            )
        )
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
                        onThemeModeChange = onThemeModeChange
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
                }
            }
        },
        modifier = modifier,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            EventListScaffold(
                state = state,
                userInitials = userInitials,
                snackbarHostState = snackbarHostState,
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
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
                    if (!state.isPerformingAction && state.selectedEventIds.isNotEmpty()) {
                        showDeleteSelectedDialog = true
                    }
                },
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
            message = LocalizedStrings.deleteSelectedDialogMessage(state.selectedEventIds.size),
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
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = LocalizedStrings.drawerThemeModeTitle,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
    ThemeMode.values().forEach { mode ->
        val label = when (mode) {
            ThemeMode.SYSTEM -> LocalizedStrings.themeModeSystem
            ThemeMode.LIGHT -> LocalizedStrings.themeModeLight
            ThemeMode.DARK -> LocalizedStrings.themeModeDark
        }
        NavigationDrawerItem(
            label = { Text(text = label) },
            selected = selectedMode == mode,
            onClick = { onThemeModeChange(mode) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EventListScaffold(
    state: EventListUiState,
    userInitials: String,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onEventSelectionChange: (EventEntity, Boolean) -> Unit,
    onEventLongPress: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val actionsEnabled = !state.isPerformingAction

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = state.selectionMode) {
                val visibleSelectedCount = state.visibleEvents.count { event ->
                    state.selectedEventIds.contains(event.eventId)
                }
                SelectionBottomBar(
                    selectedCount = state.selectedEventIds.size,
                    visibleCount = state.visibleEvents.size,
                    visibleSelectedCount = visibleSelectedCount,
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
                isRefreshing = state.isLoading,
                userInitials = userInitials,
                lastUpdated = state.lastUpdated,
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
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
    isRefreshing: Boolean,
    userInitials: String,
    lastUpdated: Instant?,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val headerShape = RoundedCornerShape(28.dp)
    val headerBrush = remember(
        colorScheme.primaryContainer,
        colorScheme.secondaryContainer,
        colorScheme.tertiaryContainer
    ) {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.primaryContainer.copy(alpha = 0.9f),
                colorScheme.secondaryContainer.copy(alpha = 0.85f),
                colorScheme.tertiaryContainer.copy(alpha = 0.9f)
            )
        )
    }
    val headerBackgroundLuminance = remember(
        colorScheme.primaryContainer,
        colorScheme.secondaryContainer,
        colorScheme.tertiaryContainer
    ) {
        val colors = listOf(
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer
        )
        colors.fold(0f) { total, color -> total + color.luminance() } / colors.size
    }
    val isHeaderBackgroundLight = headerBackgroundLuminance > 0.5f
    val headerContentColor = Color.White
    val outlineColor = if (isHeaderBackgroundLight) {
        colorScheme.primary.copy(alpha = 0.12f)
    } else {
        headerContentColor.copy(alpha = 0.2f)
    }
    val searchContainerColor = if (isHeaderBackgroundLight) {
        Color.White.copy(alpha = 0.92f)
    } else {
        Color.White.copy(alpha = 0.18f)
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
                .background(headerBrush)
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
                    }
                    Avatar(
                        initials = userInitials,
                        onClick = onAvatarClick,
                        backgroundColor = Color.White.copy(alpha = if (isHeaderBackgroundLight) 0.65f else 0.24f),
                        contentColor = headerContentColor
                    )
                }

                SearchField(
                    query = query,
                    isRefreshing = isRefreshing,
                    onQueryChange = onQueryChange,
                    onRefresh = onRefresh,
                    containerColor = searchContainerColor,
                    contentColor = headerContentColor
                )

                lastUpdated?.let { instant ->
                    Text(
                        text = LocalizedStrings.lastUpdatedLabel(formatInstant(instant)),
                        style = MaterialTheme.typography.bodySmall,
                        color = headerContentColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isRefreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
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
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = LocalizedStrings.refreshContentDescription,
                        tint = fieldContentColor
                    )
                }
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
    modifier: Modifier = Modifier
) {
    if (state.visibleEvents.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        item {
            Text(
                text = LocalizedStrings.timelineTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

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
                onClick = if (allVisibleSelected) onUnselectAll else onSelectAll,
                enabled = actionsEnabled && hasVisibleEvents,
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
                    val label = if (allVisibleSelected) {
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

@Composable
private fun EventCard(
    event: EventEntity,
    isSelected: Boolean,
    actionsEnabled: Boolean,
    onClick: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(24.dp)
    val cardBrush = remember(
        colorScheme.surface,
        colorScheme.secondaryContainer,
        colorScheme.primaryContainer,
        isSelected
    ) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.surfaceColorAtElevation(4.dp),
                if (isSelected) {
                    colorScheme.primaryContainer.copy(alpha = 0.75f)
                } else {
                    colorScheme.secondaryContainer.copy(alpha = 0.55f)
                }
            )
        )
    }
    val borderColor = remember(colorScheme.primary, isSelected) {
        if (isSelected) {
            colorScheme.primary.copy(alpha = 0.4f)
        } else {
            colorScheme.primary.copy(alpha = 0.12f)
        }
    }
    val clickableModifier = if (onClick != null || onLongPress != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier.combinedClickable(
            enabled = actionsEnabled,
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            onClick = { onClick?.invoke() },
            onLongClick = onLongPress
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .clip(cardShape)
            .background(cardBrush)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = cardShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = event.subjectEntity,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.eventAction.isNotBlank()) {
                        Text(
                            text = event.eventAction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(text = event.eventCategory) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = colorScheme.tertiaryContainer,
                        labelColor = colorScheme.onTertiaryContainer
                    )
                )
                Text(
                    text = LocalizedStrings.eventTimestampLabel(formatInstant(event.tsStart)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            if (event.tags.isNotEmpty()) {
                val previewTags = event.tags.take(4)
                val tagLine = buildString {
                    append(previewTags.joinToString(separator = ", "))
                    if (event.tags.size > previewTags.size) {
                        append(", …")
                    }
                }
                Text(
                    text = LocalizedStrings.tagsLabel(tagLine),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.secondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = LocalizedStrings.eventTypeLabel(event.eventType),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary
                )
                event.subjectEntityId?.let {
                    Text(
                        text = LocalizedStrings.entityIdLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private object LocalizedStrings {
    val drawerTitle: String
        @Composable get() = stringResource(R.string.event_list_drawer_title)

    val drawerSettings: String
        @Composable get() = stringResource(R.string.event_list_drawer_settings)

    val drawerThemeModeTitle: String
        @Composable get() = stringResource(R.string.event_list_drawer_theme_mode_title)

    val themeModeSystem: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_system)

    val themeModeLight: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_light)

    val themeModeDark: String
        @Composable get() = stringResource(R.string.event_list_theme_mode_dark)

    val menuContentDescription: String
        @Composable get() = stringResource(R.string.event_list_menu_cd)

    val refreshContentDescription: String
        @Composable get() = stringResource(R.string.event_list_refresh_cd)

    val avatarContentDescription: String
        @Composable get() = stringResource(R.string.event_list_avatar_cd)

    val searchPlaceholder: String
        @Composable get() = stringResource(R.string.event_list_search_placeholder)

    val headerSubtitle: String
        @Composable get() = stringResource(R.string.event_list_header_subtitle)

    val headerTitle: String
        @Composable get() = stringResource(R.string.event_list_header_title)

    val timelineTitle: String
        @Composable get() = stringResource(R.string.event_list_timeline_title)

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
    fun lastUpdatedLabel(value: String) = stringResource(R.string.event_list_last_updated, value)

    @Composable
    fun eventTimestampLabel(value: String) = stringResource(R.string.event_list_event_time, value)

    @Composable
    fun eventTypeLabel(value: String) = stringResource(R.string.event_list_event_type, value)

    @Composable
    fun entityIdLabel(value: String) = stringResource(R.string.event_list_entity_id, value)

    @Composable
    fun tagsLabel(value: String) = stringResource(R.string.event_list_tags, value)
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())

private fun formatInstant(instant: Instant): String {
    val zonedDateTime = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
    return TIME_FORMATTER.format(zonedDateTime)
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
            tsStart = now,
            tags = listOf("priority", "gmail")
        ),
        EventEntity(
            eventId = "evt-2",
            userId = "u_local",
            deviceId = "pixel-9",
            eventType = "call",
            eventCategory = "Communications",
            eventAction = "Missed call",
            subjectEntity = "Carol Micek",
            tsStart = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3_600_000),
            tags = listOf("work")
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
            onRefresh = {},
            onNavigateToSettings = {},
            onSelectionModeChange = {},
            onEventSelectionChange = { _, _ -> },
            onSelectAll = {},
            onUnselectAll = {},
            onDeleteSelected = {},
            onThemeModeChange = {}
        )
    }
}
