package com.swooby.alfred.ui.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlinx.coroutines.launch

@Composable
fun EventListScreen(
    state: EventListUiState,
    userInitials: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundBrush = remember(colorScheme.primary, colorScheme.tertiary, colorScheme.surface) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.primary.copy(alpha = 0.3f),
                colorScheme.tertiary.copy(alpha = 0.22f),
                colorScheme.surface
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
                }
            )
        }
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
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = state.isLoading,
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
    val headerShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    val headerBrush = remember(colorScheme.primary, colorScheme.secondary, colorScheme.tertiary) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.primary,
                colorScheme.secondary,
                colorScheme.tertiary
            )
        )
    }
    val headerContentColor = colorScheme.onPrimary
    val searchContainerColor = remember(headerContentColor) {
        if (headerContentColor.luminance() > 0.5f) {
            Color.Black.copy(alpha = 0.2f)
        } else {
            Color.White.copy(alpha = 0.2f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 10.dp, shape = headerShape, clip = false)
            .clip(headerShape)
            .background(headerBrush)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = LocalizedStrings.headerSubtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = headerContentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = LocalizedStrings.headerTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = headerContentColor
                    )
                }
                Avatar(
                    initials = userInitials,
                    onClick = onAvatarClick,
                    backgroundColor = headerContentColor.copy(alpha = 0.24f),
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
                    color = headerContentColor.copy(alpha = 0.8f)
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
private fun EventListContent(state: EventListUiState, modifier: Modifier = Modifier) {
    if (state.visibleEvents.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
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
            TimelineEventRow(
                event = event,
                isFirst = index == 0,
                isLast = index == state.visibleEvents.lastIndex
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
        EventCard(event = event, modifier = Modifier.weight(1f))
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
private fun EventCard(event: EventEntity, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(24.dp)
    val cardBrush = remember(colorScheme.surface, colorScheme.secondaryContainer) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.surfaceColorAtElevation(4.dp),
                colorScheme.secondaryContainer.copy(alpha = 0.55f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(cardBrush)
            .border(
                width = 1.dp,
                color = colorScheme.primary.copy(alpha = 0.12f),
                shape = cardShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

    val emptyTitle: String
        @Composable get() = stringResource(R.string.event_list_empty_title)

    val emptyBody: String
        @Composable get() = stringResource(R.string.event_list_empty_body)

    val emptyCta: String
        @Composable get() = stringResource(R.string.event_list_empty_hint)

    val lastUpdatedLabel: (String) -> String
        @Composable get() = { value -> stringResource(R.string.event_list_last_updated, value) }

    val eventTimestampLabel: (String) -> String
        @Composable get() = { value -> stringResource(R.string.event_list_event_time, value) }

    val eventTypeLabel: (String) -> String
        @Composable get() = { value -> stringResource(R.string.event_list_event_type, value) }

    val entityIdLabel: (String) -> String
        @Composable get() = { value -> stringResource(R.string.event_list_entity_id, value) }

    val tagsLabel: (String) -> String
        @Composable get() = { value -> stringResource(R.string.event_list_tags, value) }
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

    EventListScreen(
        state = EventListUiState(
            query = "",
            allEvents = sampleEvents,
            visibleEvents = sampleEvents
        ),
        userInitials = "A",
        onQueryChange = {},
        onRefresh = {},
        onNavigateToSettings = {}
    )
}
