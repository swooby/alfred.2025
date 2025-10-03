package com.swooby.alfred.ui.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred.R
import com.swooby.alfred.data.EventEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .padding(innerPadding)
        ) {
            EventListTopBar(
                query = state.query,
                isRefreshing = state.isLoading,
                userInitials = userInitials,
                lastUpdated = state.lastUpdated,
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
                onMenuClick = onMenuClick,
                onAvatarClick = onAvatarClick,
            )

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

            Spacer(modifier = Modifier.height(8.dp))

            EventListContent(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f, fill = true)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventListTopBar(
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = LocalizedStrings.menuContentDescription,
                        tint = colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Avatar(initials = userInitials, onClick = onAvatarClick)
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(text = LocalizedStrings.searchPlaceholder) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (isRefreshing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = LocalizedStrings.refreshContentDescription
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surfaceContainer,
                    unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = colorScheme.primary
                )
            )

            lastUpdated?.let { instant ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(text = LocalizedStrings.lastUpdatedLabel(formatInstant(instant))) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        labelColor = colorScheme.onSurfaceVariant,
                        disabledContainerColor = colorScheme.surfaceContainerHigh,
                        disabledLabelColor = colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun Avatar(initials: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = LocalizedStrings.avatarContentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = LocalizedStrings.recentSection,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        items(state.visibleEvents) { event ->
            EventRow(event = event)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = LocalizedStrings.emptyTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = LocalizedStrings.emptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EventRow(event: EventEntity, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!event.eventAction.isBlank()) {
                    Text(
                        text = event.eventAction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.eventCategory,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatInstant(event.tsStart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (event.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    event.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(text = tag) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = LocalizedStrings.eventTypeLabel(event.eventType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.subjectEntityId?.let {
                    Text(
                        text = LocalizedStrings.entityIdLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private object LocalizedStrings {
    val drawerTitle: String
        @Composable get() = stringResourceWrapper(R.string.event_list_drawer_title)

    val drawerSettings: String
        @Composable get() = stringResourceWrapper(R.string.event_list_drawer_settings)

    val menuContentDescription: String
        @Composable get() = stringResourceWrapper(R.string.event_list_menu_cd)

    val refreshContentDescription: String
        @Composable get() = stringResourceWrapper(R.string.event_list_refresh_cd)

    val searchPlaceholder: String
        @Composable get() = stringResourceWrapper(R.string.event_list_search_placeholder)

    val recentSection: String
        @Composable get() = stringResourceWrapper(R.string.event_list_recent_section)

    val emptyTitle: String
        @Composable get() = stringResourceWrapper(R.string.event_list_empty_title)

    val emptyBody: String
        @Composable get() = stringResourceWrapper(R.string.event_list_empty_body)

    val lastUpdatedLabel: (String) -> String
        @Composable get() = { value -> stringResourceWrapper(R.string.event_list_last_updated, value) }

    val avatarContentDescription: String
        @Composable get() = stringResourceWrapper(R.string.event_list_avatar_cd)

    @Composable
    fun eventTypeLabel(eventType: String): String =
        stringResourceWrapper(R.string.event_list_event_type, eventType)

    @Composable
    fun entityIdLabel(id: String): String =
        stringResourceWrapper(R.string.event_list_entity_id, id)
}

@Composable
private fun stringResourceWrapper(id: Int, vararg args: Any): String {
    return androidx.compose.ui.res.stringResource(id = id, *args)
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
