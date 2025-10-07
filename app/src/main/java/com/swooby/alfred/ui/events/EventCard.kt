package com.swooby.alfred.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfred.data.EventEntity
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventCard(
    event: EventEntity,
    isSelected: Boolean,
    actionsEnabled: Boolean,
    onClick: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(24.dp)
    val cardColor = remember(colorScheme, isSelected) {
        if (isSelected) {
            colorScheme.primaryContainer
        } else {
            colorScheme.surfaceColorAtElevation(4.dp)
        }
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
        ?: contextJson?.intOrNull("timeoutAfter")?.let { "$it ms" }
    val groupKey = contextJson?.stringOrNull("groupKey")
    val ticker = contextJson?.stringOrNull("ticker")
    val shortcutId = traits?.stringOrNull("shortcutId")
    val locusId = traits?.stringOrNull("locusId")
    val intentsObj = traits?.objectOrNull("intents")
    val styleObj = traits?.objectOrNull("style")
    val bubbleObj = traits?.objectOrNull("bubble")

    val mediaSourceApp = attributes.stringOrNull("source_app")
    val mediaTitleAttr = attributes.stringOrNull("title")
    val mediaArtist = attributes.stringOrNull("artist")
    val mediaAlbum = attributes.stringOrNull("album")
    val mediaOutputRoute = attributes.stringOrNull("output_route")

    val eventInstant = event.ingestAt ?: event.tsEnd ?: event.tsStart
    val timeFormatter = rememberTimeFormatter()
    val postedLabel = formatInstant(eventInstant, timeFormatter)
    val tagLine = event.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val peopleList = traits?.arrayOrNull("people")?.mapNotNull { it.toPersonDisplay() } ?: emptyList()
    val actionsList = traits?.arrayOrNull("actions")?.mapNotNull { it.toActionDisplay() } ?: emptyList()

    val chips = buildList {
        category?.let { add("${LocalizedStrings.labelCategory}: $it") }
        template?.let { add("${LocalizedStrings.labelTemplate}: $it") }
        importance?.let { add("${LocalizedStrings.labelImportance}: $it") }
        rank?.let { add("${LocalizedStrings.labelRank}: $it") }
    }

    val componentLabel = LocalizedStrings.componentLabel(event.component)
    val headerTitle = when (event.component) {
        "notif_listener" -> appLabel?.takeIf { it.isNotBlank() }
            ?: LocalizedStrings.unknownApp
        "media_session" -> appLabel?.takeIf { it.isNotBlank() }
            ?: mediaSourceApp?.takeIf { it.isNotBlank() }
            ?: packageName?.takeIf { it.isNotBlank() }
            ?: LocalizedStrings.unknownApp
        else -> appLabel?.takeIf { it.isNotBlank() }
            ?: event.eventCategory.takeIf { it.isNotBlank() }
            ?: packageName?.takeIf { it.isNotBlank() }
            ?: LocalizedStrings.componentGeneric
    }
    val headerSubtitle = when (event.component) {
        "notif_listener" -> packageName?.takeIf { it.isNotBlank() && it != headerTitle }
        "media_session" -> (packageName?.takeIf { it.isNotBlank() } ?: mediaSourceApp?.takeIf { it.isNotBlank() })
            ?.takeIf { it != headerTitle }
        else -> event.eventCategory.takeIf { it.isNotBlank() && it != headerTitle }
    }

    var headlineText = titleText.takeIf { it.isNotBlank() }
        ?: event.eventAction.takeIf { it.isNotBlank() }
        ?: event.eventType
    val supportingTexts = mutableListOf<String>()
    fun addSupporting(value: String?) {
        val normalized = value?.trim()
        if (normalized.isNullOrEmpty()) return
        if (normalized == headlineText) return
        if (supportingTexts.contains(normalized)) return
        supportingTexts += normalized
    }

    when (event.component) {
        "media_session" -> {
            headlineText = mediaTitleAttr?.takeIf { it.isNotBlank() }
                ?: LocalizedStrings.mediaUnknownTitle
            val subtitle = listOfNotNull(
                mediaArtist?.takeIf { it.isNotBlank() },
                mediaAlbum?.takeIf { it.isNotBlank() }
            ).joinToString(" • ").takeIf { it.isNotBlank() }
            addSupporting(subtitle)

            val playbackLabel = LocalizedStrings.mediaPlaybackState(event.eventAction)
            val playedMs = event.metrics.intOrNull("played_ms")?.takeIf { it > 0 }
            val playbackLine = playedMs?.let {
                val formattedDuration = formatElapsedMillis(it.toLong())
                "$playbackLabel · ${LocalizedStrings.mediaPlayedDuration(formattedDuration)}"
            } ?: playbackLabel
            addSupporting(playbackLine)

            bodyText?.takeIf { !it.equals(playbackLine, ignoreCase = true) }?.let {
                addSupporting(it)
            }
        }

        "notif_listener" -> {
            subject?.stringOrNull("title")?.takeIf { it.isNotBlank() }?.let {
                headlineText = it
            }
            addSupporting(conversationTitle?.takeIf { it.isNotBlank() })
            addSupporting(bodyText)
            if (!expanded && subjectLines.isNotEmpty()) {
                addSupporting(subjectLines.first())
            }
        }

        else -> {
            addSupporting(bodyText)
            addSupporting(conversationTitle?.takeIf { it.isNotBlank() })
            if (!expanded && subjectLines.isNotEmpty()) {
                addSupporting(subjectLines.first())
            }
        }
    }

    if (supportingTexts.isEmpty()) {
        addSupporting(bodyText)
        if (!expanded && subjectLines.isNotEmpty()) {
            addSupporting(subjectLines.first())
        }
    }

    val supportingSummary = supportingTexts.toList()
    val actionChips = if (event.component == "notif_listener") {
        actionsList.take(3)
    } else {
        emptyList()
    }
    val metadataChips = buildList {
        addAll(chips)
        when (event.component) {
            "media_session" -> {
                mediaOutputRoute?.takeIf { it.isNotBlank() }?.let {
                    add(LocalizedStrings.mediaRouteLabel(it))
                }
            }

            "notif_listener" -> {
                channelName?.takeIf { it.isNotBlank() }?.let {
                    add("${LocalizedStrings.labelChannelName}: $it")
                }
            }
        }
    }.distinct()

    val identityItems = buildList {
        appLabel?.let { add(InfoItem(LocalizedStrings.labelApp, it)) }
        packageName?.let { add(InfoItem(LocalizedStrings.labelPackage, it)) }
        shortcutId?.let { add(InfoItem(LocalizedStrings.labelShortcut, it)) }
        locusId?.let { add(InfoItem(LocalizedStrings.labelLocus, it)) }
    }

    val contextItems = buildList {
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

    val contextFlags = buildList {
        if (contextJson?.booleanOrNull("colorized") == true) add(LocalizedStrings.flagColorized)
        if (contextJson?.booleanOrNull("onlyAlertOnce") == true) add(LocalizedStrings.flagOnlyAlertOnce)
        if (contextJson?.booleanOrNull("ongoing") == true) add(LocalizedStrings.flagOngoing)
        if (contextJson?.booleanOrNull("clearable") == true) add(LocalizedStrings.flagClearable)
        if (contextJson?.booleanOrNull("unclearable") == true) add(LocalizedStrings.flagUnclearable)
        if (contextJson?.booleanOrNull("isGroupSummary") == true) add(LocalizedStrings.flagGroupSummary)
        if (contextJson?.booleanOrNull("showWhen") == true) add(LocalizedStrings.flagShowWhen)
    }

    val rankingInfo = contextJson?.objectOrNull("rankingInfo")
    val rankingFlags = buildList {
        if (rankingInfo?.booleanOrNull("ambient") == true) add(LocalizedStrings.flagAmbient)
        if (rankingInfo?.booleanOrNull("suspended") == true) add(LocalizedStrings.flagSuspended)
        if (rankingInfo?.booleanOrNull("canShowBadge") == true) add(LocalizedStrings.flagBadge)
        if (rankingInfo?.booleanOrNull("isConversation") == true) add(LocalizedStrings.flagConversation)
    }

    val bubbleItems = buildList {
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

    val intentsItems = intentsObj?.entries?.mapNotNull { (key, value) ->
        (value as? JsonObject)?.stringOrNull("creatorPackage")?.let { InfoItem(key, it) }
    } ?: emptyList()

    val styleItems = styleObj?.entries?.mapNotNull { (key, value) ->
        value.toDisplayString()?.let { InfoItem(key, it) }
    } ?: emptyList()

    val attachmentsList = attachmentsJson?.mapNotNull { it.toAttachmentDisplay() } ?: emptyList()

    val metricsItems = event.metrics.entries.mapNotNull { (key, value) ->
        value.toDisplayString()?.let { InfoItem(key, it) }
    }

    val refsItems = refsJson?.entries?.mapNotNull { (key, value) ->
        value.toDisplayString()?.let { InfoItem(key, it) }
    } ?: emptyList()

    val integrityItems = buildList {
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

    val eventItems = buildList {
        add(InfoItem(LocalizedStrings.labelEventType, event.eventType))
        event.subjectEntityId?.let { add(InfoItem(LocalizedStrings.labelEntityId, it)) }
        event.subjectParentId?.let { add(InfoItem(LocalizedStrings.labelParentId, it)) }
        event.rawFingerprint?.let { add(InfoItem(LocalizedStrings.labelFingerprint, it)) }
    }

    val extrasText = rawExtrasJson?.let { PrettyJson.encodeToString(JsonObject.serializer(), it) }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .clip(cardShape)
            .background(cardColor)
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = postedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        EventBadge(text = componentLabel)
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    headerSubtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    tagLine?.let {
                        Text(
                            text = LocalizedStrings.tagsLabel(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.secondary,
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                supportingSummary.forEachIndexed { index, supporting ->
                    Text(
                        text = supporting,
                        style = if (index == 0) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = colorScheme.onSurfaceVariant,
                        maxLines = when {
                            expanded && index == 0 -> 6
                            expanded -> 3
                            index == 0 -> 3
                            else -> 1
                        },
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (actionChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actionChips.forEach { action ->
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(text = action) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = colorScheme.primaryContainer.copy(alpha = 0.85f),
                                labelColor = colorScheme.onPrimaryContainer,
                                disabledContainerColor = colorScheme.primaryContainer.copy(alpha = 0.85f),
                                disabledLabelColor = colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            if (metadataChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadataChips.forEach { chip ->
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(text = chip) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                labelColor = colorScheme.onSecondaryContainer,
                                disabledContainerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                disabledLabelColor = colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(color = colorScheme.primary.copy(alpha = 0.12f))

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
                        items = listOf(
                            InfoItem(
                                label = null,
                                value = peopleList.joinToString(separator = "\n")
                            )
                        )
                    )
                }

                if (actionsList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelActions,
                        items = listOf(
                            InfoItem(
                                label = null,
                                value = actionsList.joinToString(separator = "\n")
                            )
                        )
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

@Composable
private fun EventBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val formatterCache = AtomicReference<Pair<Locale, DateTimeFormatter>?>(null)

private fun timeFormatterFor(locale: Locale): DateTimeFormatter {
    formatterCache.get()?.takeIf { it.first == locale }?.let { return it.second }
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", locale)
    formatterCache.set(locale to formatter)
    return formatter
}

@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    return remember(locale) { timeFormatterFor(locale) }
}

private fun formatElapsedMillis(millis: Long): String {
    val totalSeconds = millis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

private fun formatInstant(instant: Instant, formatter: DateTimeFormatter): String {
    val zonedDateTime = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
    return formatter.format(zonedDateTime)
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

private fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.booleanOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

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
        contentOrNull != null -> contentOrNull?.takeIf { it.isNotBlank() }
        else -> content.takeIf { it.isNotBlank() }
    }
    is JsonObject -> if (isEmpty()) null else PrettyJson.encodeToString(JsonObject.serializer(), this)
    is JsonArray -> {
        val values = mapNotNull { it.toDisplayString()?.takeIf { text -> text.isNotBlank() } }
        if (values.isEmpty()) null else values.joinToString(", ")
    }
}

@Preview(showBackground = true)
@Composable
private fun EventCardPreview() {
    val now = Instant.fromEpochMilliseconds(java.time.Instant.now().toEpochMilli())
    val sampleEvent = EventEntity(
        eventId = "evt_preview",
        userId = "u_local",
        deviceId = "pixel-9",
        eventType = "notification",
        eventCategory = "Inbox",
        eventAction = "New message",
        subjectEntity = "Email",
        subjectEntityId = "com.mail:99",
        tsStart = now,
        tags = listOf("priority", "gmail"),
        attributes = buildJsonObject {
            put("actor", buildJsonObject {
                put("appLabel", JsonPrimitive("Mail"))
                put("packageName", JsonPrimitive("com.mail"))
            })
            put("subject", buildJsonObject {
                put("title", JsonPrimitive("Project Apollo"))
                put("text", JsonPrimitive("Latest update from Alex"))
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
                    add(buildJsonObject { put("name", JsonPrimitive("Alex King")) })
                    add(buildJsonObject { put("name", JsonPrimitive("Maya Singh")) })
                })
                put("actions", buildJsonArray {
                    add(buildJsonObject { put("title", JsonPrimitive("Reply")) })
                    add(buildJsonObject { put("title", JsonPrimitive("Mark read")) })
                    add(buildJsonObject { put("title", JsonPrimitive("Archive")) })
                })
            })
            put("refs", buildJsonObject {
                put("key", JsonPrimitive("notif-key"))
                put("user", JsonPrimitive("UserHandle{0}"))
            })
            put("subjectLines", buildJsonArray {
                add(JsonPrimitive("Sprint planning moved to Friday."))
                add(JsonPrimitive("See shared doc for tasks."))
            })
        }
    )

    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EventCard(
                event = sampleEvent,
                isSelected = false,
                actionsEnabled = true,
                onClick = null,
                onLongPress = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
