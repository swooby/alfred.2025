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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred.R
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.ui.theme.AlfredTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.isString
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Composable
fun EventListScreen(
    state: EventListUiState,
    userInitials: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onEventSelectionChange: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onUnselectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
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

@OptIn(ExperimentalLayoutApi::class)
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

    var expanded by rememberSaveable(event.eventId) { mutableStateOf(false) }

    val attributes = event.attributes
    val actor = attributes.objectOrNull("actor")
    val subject = attributes.objectOrNull("subject")
    val contextJson = attributes.objectOrNull("context")
    val traits = attributes.objectOrNull("traits")
    val refsJson = attributes.objectOrNull("refs")
    val integrityJson = attributes.objectOrNull("integrity")
    val rawExtrasJson = attributes.objectOrNull("rawExtras")
    val attachmentsJson = attributes.arrayOrNull("attachments")
    val subjectLines = attributes.arrayOrNull("subjectLines")?.stringValues().orEmpty()

    val titleText = subject?.stringOrNull("title") ?: event.subjectEntity
    val bodyText = subject?.stringOrNull("text")
        ?: subject?.stringOrNull("summaryText")
        ?: subject?.stringOrNull("subText")
        ?: subject?.stringOrNull("infoText")
        ?: event.eventAction.takeIf { it.isNotBlank() }

    val conversationTitle = subject?.stringOrNull("conversationTitle")
    val appLabel = actor?.stringOrNull("appLabel")
    val packageName = event.appPkg ?: actor?.stringOrNull("packageName")
    val category = contextJson?.stringOrNull("category")
    val template = traits?.stringOrNull("template") ?: subject?.stringOrNull("template")
    val channelId = refsJson?.stringOrNull("channelId")
        ?: contextJson?.objectOrNull("rankingChannel")?.stringOrNull("id")
    val channelName = contextJson?.objectOrNull("rankingChannel")?.stringOrNull("name")
    val importance = contextJson?.objectOrNull("rankingInfo")?.intOrNull("importance")
        ?: contextJson?.objectOrNull("rankingChannel")?.intOrNull("importance")
    val rank = contextJson?.objectOrNull("rankingInfo")?.intOrNull("rank")
    val userSentiment = contextJson?.objectOrNull("rankingInfo")?.intOrNull("userSentiment")
    val user = refsJson?.stringOrNull("user")
    val visibility = contextJson?.stringOrNull("visibility")
    val timeout = contextJson?.stringOrNull("timeoutAfter")
        ?: contextJson?.intOrNull("timeoutAfter")?.let { "${it} ms" }
    val groupKey = contextJson?.stringOrNull("groupKey")
    val ticker = contextJson?.stringOrNull("ticker")
    val shortcutId = traits?.stringOrNull("shortcutId")
    val locusId = traits?.stringOrNull("locusId")
    val intentsObj = traits?.objectOrNull("intents")
    val styleObj = traits?.objectOrNull("style")
    val bubbleObj = traits?.objectOrNull("bubble")

    val postedLabel = LocalizedStrings.eventTimestampLabel(formatInstant(event.tsStart))
    val tagLine = event.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val chips = remember(category, template, importance, rank, conversationTitle) {
        buildList {
            category?.let { add("${LocalizedStrings.labelCategory}: $it") }
            template?.let { add("${LocalizedStrings.labelTemplate}: $it") }
            importance?.let { add("${LocalizedStrings.labelImportance}: $it") }
            rank?.let { add("${LocalizedStrings.labelRank}: $it") }
            conversationTitle?.let { add("${LocalizedStrings.labelConversation}: $it") }
        }
    }

    val identityItems = remember(appLabel, packageName, shortcutId, locusId) {
        buildList {
            appLabel?.let { add(InfoItem(LocalizedStrings.labelApp, it)) }
            packageName?.let { add(InfoItem(LocalizedStrings.labelPackage, it)) }
            shortcutId?.let { add(InfoItem(LocalizedStrings.labelShortcut, it)) }
            locusId?.let { add(InfoItem(LocalizedStrings.labelLocus, it)) }
        }
    }

    val contextItems = remember(channelName, channelId, importance, rank, userSentiment, visibility, user, groupKey, timeout, ticker) {
        buildList {
            channelName?.let { add(InfoItem(LocalizedStrings.labelChannelName, it)) }
            channelId?.let { add(InfoItem(LocalizedStrings.labelChannel, it)) }
            importance?.let { add(InfoItem(LocalizedStrings.labelImportance, it.toString())) }
            rank?.let { add(InfoItem(LocalizedStrings.labelRank, it.toString())) }
            userSentiment?.let { add(InfoItem(LocalizedStrings.labelUserSentiment, it.toString())) }
            visibility?.let { add(InfoItem(LocalizedStrings.labelVisibility, it)) }
            user?.let { add(InfoItem(LocalizedStrings.labelUser, it)) }
            groupKey?.let { add(InfoItem(LocalizedStrings.labelGroup, it)) }
            timeout?.let { add(InfoItem(LocalizedStrings.labelTimeout, it)) }
            ticker?.let { add(InfoItem(LocalizedStrings.labelTicker, it)) }
        }
    }

    val contextFlags = remember(contextJson) {
        buildList {
            if (contextJson?.containsKey("colorized") == true) add(LocalizedStrings.flagColorized)
            if (contextJson?.containsKey("onlyAlertOnce") == true) add(LocalizedStrings.flagOnlyAlertOnce)
            if (contextJson?.containsKey("ongoing") == true) add(LocalizedStrings.flagOngoing)
            if (contextJson?.containsKey("clearable") == true) add(LocalizedStrings.flagClearable)
            if (contextJson?.containsKey("unclearable") == true) add(LocalizedStrings.flagUnclearable)
            if (contextJson?.containsKey("isGroupSummary") == true) add(LocalizedStrings.flagGroupSummary)
            if (contextJson?.containsKey("showWhen") == true) add(LocalizedStrings.flagShowWhen)
        }
    }

    val rankingInfo = contextJson?.objectOrNull("rankingInfo")
    val rankingFlags = remember(rankingInfo) {
        buildList {
            if (rankingInfo?.containsKey("ambient") == true) add(LocalizedStrings.flagAmbient)
            if (rankingInfo?.containsKey("suspended") == true) add(LocalizedStrings.flagSuspended)
            if (rankingInfo?.containsKey("canShowBadge") == true) add(LocalizedStrings.flagBadge)
            if (rankingInfo?.containsKey("isConversation") == true) add(LocalizedStrings.flagConversation)
        }
    }

    val bubbleItems = remember(bubbleObj) {
        buildList {
            bubbleObj?.intOrNull("desiredHeight")?.let {
                add(InfoItem(LocalizedStrings.bubbleHeight, it.toString()))
            }
            if (bubbleObj?.booleanOrNull("autoExpand") == true) {
                add(InfoItem(null, LocalizedStrings.bubbleAutoExpand))
            }
            if (bubbleObj?.booleanOrNull("suppressNotif") == true) {
                add(InfoItem(null, LocalizedStrings.bubbleSuppress))
            }
        }
    }

    val peopleList = remember(traits) {
        traits?.arrayOrNull("people")?.mapNotNull { it.toPersonDisplay() } ?: emptyList()
    }

    val actionsList = remember(traits) {
        traits?.arrayOrNull("actions")?.mapNotNull { it.toActionDisplay() } ?: emptyList()
    }

    val intentsItems = remember(intentsObj) {
        intentsObj?.entries?.mapNotNull { (key, value) ->
            (value as? JsonObject)?.stringOrNull("creatorPackage")?.let { InfoItem(key, it) }
        } ?: emptyList()
    }

    val styleItems = remember(styleObj) {
        styleObj?.entries?.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        } ?: emptyList()
    }

    val attachmentsList = remember(attachmentsJson) {
        attachmentsJson?.mapNotNull { it.toAttachmentDisplay() } ?: emptyList()
    }

    val metricsItems = remember(event.metrics) {
        event.metrics.entries.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        }
    }

    val refsItems = remember(refsJson) {
        refsJson?.entries?.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        } ?: emptyList()
    }

    val integrityItems = remember(integrityJson) {
        buildList {
            integrityJson?.stringOrNull("snapshotHash")?.let {
                add(InfoItem(LocalizedStrings.labelIntegrityHash, it))
            }
            val fields = integrityJson?.arrayOrNull("fieldsPresent")
            if (fields != null && fields.isNotEmpty()) {
                val preview = fields.take(6).mapNotNull { it.toDisplayString() }.joinToString(", ")
                val description = if (preview.isEmpty()) {
                    fields.size.toString()
                } else {
                    "${fields.size} · $preview"
                }
                add(InfoItem(LocalizedStrings.labelIntegrityFields, description))
            }
        }
    }

    val eventItems = remember(event.eventType, event.subjectEntityId, event.subjectParentId, event.rawFingerprint) {
        buildList {
            add(InfoItem(LocalizedStrings.labelEventType, event.eventType))
            event.subjectEntityId?.let { add(InfoItem(LocalizedStrings.labelEntityId, it)) }
            event.subjectParentId?.let { add(InfoItem(LocalizedStrings.labelParentId, it)) }
            event.rawFingerprint?.let { add(InfoItem(LocalizedStrings.labelFingerprint, it)) }
        }
    }

    val extrasText = remember(rawExtrasJson) {
        rawExtrasJson?.let { PrettyJson.encodeToString(JsonObject.serializer(), it) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    bodyText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) 6 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    conversationTitle?.takeIf { it.isNotBlank() && it != titleText }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!expanded && subjectLines.isNotEmpty()) {
                        Text(
                            text = subjectLines.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    enabled = actionsEnabled
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) {
                            LocalizedStrings.hideDetailsLabel
                        } else {
                            LocalizedStrings.showDetailsLabel
                        }
                    )
                }
            }

            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { chip ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(text = chip) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                labelColor = colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = postedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                tagLine?.let {
                    Text(
                        text = LocalizedStrings.tagsLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.secondary
                    )
                }
            }

            if (expanded) {
                Divider(color = colorScheme.primary.copy(alpha = 0.12f))

                InfoSection(LocalizedStrings.sectionSummary, identityItems)
                InfoSection(LocalizedStrings.sectionContext, contextItems)

                val allFlags = (contextFlags + rankingFlags).distinct()
                if (allFlags.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelFlags,
                        items = allFlags.map { InfoItem(label = null, value = it) }
                    )
                }

                if (bubbleItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.sectionBubble, bubbleItems)
                }

                if (subjectLines.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelSubjectLines,
                        items = subjectLines.map { InfoItem(label = null, value = it) }
                    )
                }

                if (peopleList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelPeople,
                        items = listOf(InfoItem(label = null, value = peopleList.joinToString(separator = "\n")))
                    )
                }

                if (actionsList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelActions,
                        items = listOf(InfoItem(label = null, value = actionsList.joinToString(separator = "\n")))
                    )
                }

                if (intentsItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.labelIntents, intentsItems)
                }

                if (styleItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.labelStyle, styleItems)
                }

                if (attachmentsList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelAttachments,
                        items = attachmentsList.map { InfoItem(label = null, value = it) }
                    )
                }

                if (metricsItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.labelMetrics, metricsItems)
                }

                if (refsItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.labelRefs, refsItems)
                }

                if (integrityItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.labelIntegrity, integrityItems)
                }

                if (eventItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.sectionEvent, eventItems)
                }

                extrasText?.let { extras ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = LocalizedStrings.labelRawExtras,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                        SelectionContainer {
                            Text(
                                text = extras,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class InfoItem(val label: String?, val value: String)

@Composable
private fun InfoSection(
    title: String,
    items: List<InfoItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        items.forEach { InfoLine(it) }
    }
}

@Composable
private fun InfoLine(item: InfoItem) {
    Text(
        text = buildAnnotatedString {
            item.label?.takeIf { it.isNotBlank() }?.let { label ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(label)
                    append(": ")
                }
            }
            append(item.value)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val PrettyJson = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
}

private fun JsonObject.stringOrNull(key: String): String? {
    val element = this[key] ?: return null
    return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: element.toDisplayString()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonArray.stringValues(): List<String> = mapNotNull { element ->
    element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonElement.toPersonDisplay(): String? {
    val obj = this as? JsonObject ?: return null
    val name = obj.stringOrNull("name")
    val uri = obj.stringOrNull("uri")
    val key = obj.stringOrNull("key")
    return when {
        !name.isNullOrBlank() && !uri.isNullOrBlank() -> "$name ($uri)"
        !name.isNullOrBlank() -> name
        !uri.isNullOrBlank() -> uri
        !key.isNullOrBlank() -> key
        else -> null
    }
}

private fun JsonElement.toActionDisplay(): String? {
    val obj = this as? JsonObject ?: return null
    val title = obj.stringOrNull("title") ?: return null
    val semantic = obj["semanticAction"]?.toDisplayString()
    val remoteInputs = obj.arrayOrNull("remoteInputs")
        ?.mapNotNull { (it as? JsonObject)?.stringOrNull("resultKey") }
        ?.takeIf { it.isNotEmpty() }
    return buildString {
        append(title)
        semantic?.let { append(" · ").append(it) }
        remoteInputs?.let { append(" · ").append(it.joinToString()) }
    }
}

private fun JsonElement.toAttachmentDisplay(): String? {
    val obj = this as? JsonObject ?: return null
    val type = obj.stringOrNull("type") ?: return null
    val details = buildList {
        obj["uri"]?.toDisplayString()?.takeIf { it.isNotBlank() }?.let { add(it) }
        val resPkg = obj.stringOrNull("resPkg")
        obj["resId"]?.toDisplayString()?.takeIf { it.isNotBlank() }?.let { resId ->
            val pkg = resPkg?.let { "$it/" } ?: ""
            add(pkg + resId)
        }
    }
    return if (details.isEmpty()) type else "$type · ${details.joinToString()}"
}

private fun JsonElement.toDisplayString(): String? = when (this) {
    is JsonPrimitive -> when {
        booleanOrNull != null -> if (booleanOrNull == true) "true" else null
        isString -> contentOrNull?.takeIf { it.isNotBlank() }
        else -> content
    }
    is JsonObject -> if (isEmpty()) null else PrettyJson.encodeToString(JsonObject.serializer(), this)
    is JsonArray -> {
        val values = mapNotNull { it.toDisplayString()?.takeIf { text -> text.isNotBlank() } }
        if (values.isEmpty()) null else values.joinToString(", ")
    }
    else -> null
}

private object LocalizedStrings {
    val drawerTitle: String
        @Composable get() = stringResource(R.string.event_list_drawer_title)

    val drawerSettings: String
        @Composable get() = stringResource(R.string.event_list_drawer_settings)

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
                })
                put("refs", buildJsonObject {
                    put("key", JsonPrimitive("notif-key"))
                    put("user", JsonPrimitive("UserHandle{0}"))
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
            onQueryChange = {},
            onRefresh = {},
            onNavigateToSettings = {},
            onSelectionModeChange = {},
            onEventSelectionChange = { _, _ -> },
            onSelectAll = {},
            onUnselectAll = {},
            onDeleteSelected = {}
        )
    }
}
