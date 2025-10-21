package com.swooby.alfred.ui.events

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceComponentIds
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(24.dp)
    val cardColor =
        remember(colorScheme, isSelected) {
            if (isSelected) {
                colorScheme.primaryContainer
            } else {
                colorScheme.surfaceColorAtElevation(4.dp)
            }
        }
    val borderColor =
        remember(colorScheme.primary, isSelected) {
            if (isSelected) {
                colorScheme.primary.copy(alpha = 0.4f)
            } else {
                colorScheme.primary.copy(alpha = 0.12f)
            }
        }
    val clickableModifier =
        if (onClick != null || onLongPress != null) {
            val interactionSource = remember { MutableInteractionSource() }
            Modifier.combinedClickable(
                enabled = actionsEnabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = { onClick?.invoke() },
                onLongClick = onLongPress,
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
    val bodyText =
        subject?.stringOrNull("text")
            ?: subject?.stringOrNull("summaryText")
            ?: subject?.stringOrNull("subText")
            ?: subject?.stringOrNull("infoText")
            ?: event.eventAction.takeIf { it.isNotBlank() }

    val conversationTitle = subject?.stringOrNull("conversationTitle")
    val actorAppLabel = actor?.stringOrNull("appLabel")
    val packageName = event.appPkg ?: actor?.stringOrNull("packageName")
    val appLabel = rememberResolvedAppLabel(packageName, actorAppLabel)
    val category = contextJson?.stringOrNull("category")
    val template = traits?.stringOrNull("template") ?: subject?.stringOrNull("template")
    val channelId =
        refsJson?.stringOrNull("channelId")
            ?: contextJson?.objectOrNull("rankingChannel")?.stringOrNull("id")
    val channelName = contextJson?.objectOrNull("rankingChannel")?.stringOrNull("name")
    val importance =
        contextJson?.objectOrNull("rankingInfo")?.intOrNull("importance")
            ?: contextJson?.objectOrNull("rankingChannel")?.intOrNull("importance")
    val rank = contextJson?.objectOrNull("rankingInfo")?.intOrNull("rank")
    val userSentiment = contextJson?.objectOrNull("rankingInfo")?.intOrNull("userSentiment")
    val user = refsJson?.stringOrNull("user")
    val visibility = contextJson?.stringOrNull("visibility")
    val timeout =
        contextJson?.stringOrNull("timeoutAfter")
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

    val eventInstant = event.tsStart
    val timeFormatter = rememberTimeFormatter()
    val postedLabel = formatInstant(eventInstant, timeFormatter)
    val tagLine = event.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
    val fingerprint = event.rawFingerprint?.takeIf { it.isNotBlank() }
    val storedCoalesceKey = event.coalesceKey?.takeIf { it.isNotBlank() }
    val derivedCoalesceKey =
        if (storedCoalesceKey == null) {
            deriveCoalesceKey(event, refsJson)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    val coalesceKey =
        when {
            storedCoalesceKey != null -> storedCoalesceKey
            derivedCoalesceKey != null -> "${derivedCoalesceKey} (derived)"
            else -> null
        }

    val peopleList = traits?.arrayOrNull("people")?.mapNotNull { it.toPersonDisplay() } ?: emptyList()
    val actionsList = traits?.arrayOrNull("actions")?.mapNotNull { it.toActionDisplay() } ?: emptyList()

    val chips =
        buildList {
            category?.let { add("${LocalizedStrings.labelCategory}: $it") }
            template?.let { add("${LocalizedStrings.labelTemplate}: $it") }
            importance?.let { add("${LocalizedStrings.labelImportance}: $it") }
            rank?.let { add("${LocalizedStrings.labelRank}: $it") }
        }

    val componentLabel = LocalizedStrings.componentLabel(event.component)
    val headerTitle =
        when (event.component) {
            SourceComponentIds.NOTIFICATION_SOURCE ->
                appLabel?.takeIf { it.isNotBlank() }
                    ?: LocalizedStrings.unknownApp
            SourceComponentIds.MEDIA_SOURCE ->
                appLabel?.takeIf { it.isNotBlank() }
                    ?: mediaSourceApp?.takeIf { it.isNotBlank() }
                    ?: packageName?.takeIf { it.isNotBlank() }
                    ?: LocalizedStrings.unknownApp
            else ->
                appLabel?.takeIf { it.isNotBlank() }
                    ?: event.eventCategory.takeIf { it.isNotBlank() }
                    ?: packageName?.takeIf { it.isNotBlank() }
                    ?: LocalizedStrings.componentGeneric
        }
    val headerSubtitle =
        when (event.component) {
            SourceComponentIds.NOTIFICATION_SOURCE -> packageName?.takeIf { it.isNotBlank() && it != headerTitle }
            SourceComponentIds.MEDIA_SOURCE ->
                (packageName?.takeIf { it.isNotBlank() } ?: mediaSourceApp?.takeIf { it.isNotBlank() })
                    ?.takeIf { it != headerTitle }
            else -> event.eventCategory.takeIf { it.isNotBlank() && it != headerTitle }
        }

    var headlineText =
        titleText.takeIf { it.isNotBlank() }
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
        SourceComponentIds.MEDIA_SOURCE -> {
            headlineText = mediaTitleAttr?.takeIf { it.isNotBlank() }
                ?: LocalizedStrings.mediaUnknownTitle
            val subtitle =
                listOfNotNull(
                    mediaArtist?.takeIf { it.isNotBlank() },
                    mediaAlbum?.takeIf { it.isNotBlank() },
                ).joinToString(" • ").takeIf { it.isNotBlank() }
            addSupporting(subtitle)

            val playbackLabel = LocalizedStrings.mediaPlaybackState(event.eventAction)
            val playedMs = event.metrics.intOrNull("played_ms")?.takeIf { it > 0 }
            val playbackLine =
                playedMs?.let {
                    val formattedDuration = formatElapsedMillis(it.toLong())
                    "$playbackLabel · ${LocalizedStrings.mediaPlayedDuration(formattedDuration)}"
                } ?: playbackLabel
            addSupporting(playbackLine)

            bodyText?.takeIf { !it.equals(playbackLine, ignoreCase = true) }?.let {
                addSupporting(it)
            }
        }

        SourceComponentIds.NOTIFICATION_SOURCE -> {
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
    val actionChips =
        if (event.component == SourceComponentIds.NOTIFICATION_SOURCE) {
            actionsList.take(3)
        } else {
            emptyList()
        }
    val metadataChips =
        buildList {
            addAll(chips)
            when (event.component) {
                SourceComponentIds.MEDIA_SOURCE -> {
                    mediaOutputRoute?.takeIf { it.isNotBlank() }?.let {
                        add(LocalizedStrings.mediaRouteLabel(it))
                    }
                }

                SourceComponentIds.NOTIFICATION_SOURCE -> {
                    channelName?.takeIf { it.isNotBlank() }?.let {
                        add("${LocalizedStrings.labelChannelName}: $it")
                    }
                }
            }
        }.distinct()

    val identityItems =
        buildList {
            appLabel?.let { add(InfoItem(LocalizedStrings.labelApp, it)) }
            packageName?.let { add(InfoItem(LocalizedStrings.labelPackage, it)) }
            shortcutId?.let { add(InfoItem(LocalizedStrings.labelShortcut, it)) }
            locusId?.let { add(InfoItem(LocalizedStrings.labelLocus, it)) }
        }

    val contextItems =
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

    val contextFlags =
        buildList {
            if (contextJson?.booleanOrNull("colorized") == true) add(LocalizedStrings.flagColorized)
            if (contextJson?.booleanOrNull("onlyAlertOnce") == true) add(LocalizedStrings.flagOnlyAlertOnce)
            if (contextJson?.booleanOrNull("ongoing") == true) add(LocalizedStrings.flagOngoing)
            if (contextJson?.booleanOrNull("clearable") == true) add(LocalizedStrings.flagClearable)
            if (contextJson?.booleanOrNull("unclearable") == true) add(LocalizedStrings.flagUnclearable)
            if (contextJson?.booleanOrNull("isGroupSummary") == true) add(LocalizedStrings.flagGroupSummary)
            if (contextJson?.booleanOrNull("showWhen") == true) add(LocalizedStrings.flagShowWhen)
        }

    val rankingInfo = contextJson?.objectOrNull("rankingInfo")
    val rankingFlags =
        buildList {
            if (rankingInfo?.booleanOrNull("ambient") == true) add(LocalizedStrings.flagAmbient)
            if (rankingInfo?.booleanOrNull("suspended") == true) add(LocalizedStrings.flagSuspended)
            if (rankingInfo?.booleanOrNull("canShowBadge") == true) add(LocalizedStrings.flagBadge)
            if (rankingInfo?.booleanOrNull("isConversation") == true) add(LocalizedStrings.flagConversation)
        }

    val bubbleItems =
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

    val intentsItems =
        intentsObj?.entries?.mapNotNull { (key, value) ->
            (value as? JsonObject)?.stringOrNull("creatorPackage")?.let { InfoItem(key, it) }
        } ?: emptyList()

    val styleItems =
        styleObj?.entries?.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        } ?: emptyList()

    val attachmentsList = attachmentsJson?.mapNotNull { it.toAttachmentDisplay() } ?: emptyList()

    val metricsItems =
        event.metrics.entries.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        }

    val refsItems =
        refsJson?.entries?.mapNotNull { (key, value) ->
            value.toDisplayString()?.let { InfoItem(key, it) }
        } ?: emptyList()

    val integrityItems =
        buildList {
            integrityJson?.stringOrNull("snapshotHash")?.let {
                add(InfoItem(LocalizedStrings.labelIntegrityHash, it))
            }
            val fields = integrityJson?.arrayOrNull("fieldsPresent")
            if (fields != null && fields.isNotEmpty()) {
                val preview = fields.take(6).mapNotNull { it.toDisplayString() }.joinToString(", ")
                val description =
                    if (preview.isEmpty()) {
                        fields.size.toString()
                    } else {
                        "${fields.size} · $preview"
                    }
                add(InfoItem(LocalizedStrings.labelIntegrityFields, description))
            }
        }

    val eventItems =
        buildList {
            add(InfoItem(LocalizedStrings.labelEventType, event.eventType))
            event.subjectEntityId?.let { add(InfoItem(LocalizedStrings.labelEntityId, it)) }
            event.subjectParentId?.let { add(InfoItem(LocalizedStrings.labelParentId, it)) }
            coalesceKey?.let { add(InfoItem(LocalizedStrings.labelCoalesceKey, it)) }
            fingerprint?.let { add(InfoItem(LocalizedStrings.labelFingerprint, it)) }
        }

    val extrasText = rawExtrasJson?.let { PrettyJson.encodeToString(JsonObject.serializer(), it) }

    Box(
        modifier =
            modifier
                .then(clickableModifier)
                .clip(cardShape)
                .background(cardColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = cardShape,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = postedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        EventBadge(
                            text = componentLabel,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    headerSubtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    tagLine?.let {
                        Text(
                            text = LocalizedStrings.tagsLabel(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (fingerprint != null || coalesceKey != null) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                fingerprint?.let {
                                    DebugInfoLine(
                                        label = LocalizedStrings.labelFingerprint,
                                        value = it,
                                    )
                                }
                                coalesceKey?.let {
                                    DebugInfoLine(
                                        label = LocalizedStrings.labelCoalesceKey,
                                        value = it,
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = { expanded = !expanded },
                    enabled = actionsEnabled,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription =
                            if (expanded) {
                                LocalizedStrings.hideDetailsLabel
                            } else {
                                LocalizedStrings.showDetailsLabel
                            },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingSummary.forEachIndexed { index, supporting ->
                    Text(
                        text = supporting,
                        style =
                            if (index == 0) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                        color = colorScheme.onSurfaceVariant,
                        maxLines =
                            when {
                                expanded && index == 0 -> 6
                                expanded -> 3
                                index == 0 -> 3
                                else -> 1
                            },
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (actionChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    actionChips.forEach { action ->
                        SuggestionChip(
                            onClick = {
                                // TODO: Do the action of the Notification...
                            },
                            enabled = false,
                            label = { Text(text = action) },
                            colors =
                                SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    labelColor = colorScheme.onPrimaryContainer,
                                    disabledContainerColor = colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    disabledLabelColor = colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }
            }

            if (metadataChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    metadataChips.forEach { chip ->
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(text = chip) },
                            colors =
                                SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    labelColor = colorScheme.onSecondaryContainer,
                                    disabledContainerColor = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    disabledLabelColor = colorScheme.onSecondaryContainer,
                                ),
                        )
                    }
                }
            }

            if (expanded || (BuildConfig.DEBUG && LocalInspectionMode.current)) {
                HorizontalDivider(color = colorScheme.primary.copy(alpha = 0.12f))

                InfoSection(LocalizedStrings.sectionSummary, identityItems)
                InfoSection(LocalizedStrings.sectionContext, contextItems)

                val allFlags = (contextFlags + rankingFlags).distinct()
                if (allFlags.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelFlags,
                        items = allFlags.map { InfoItem(label = null, value = it) },
                    )
                }

                if (bubbleItems.isNotEmpty()) {
                    InfoSection(LocalizedStrings.sectionBubble, bubbleItems)
                }

                if (subjectLines.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelSubjectLines,
                        items = subjectLines.map { InfoItem(label = null, value = it) },
                    )
                }

                if (peopleList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelPeople,
                        items =
                            listOf(
                                InfoItem(
                                    label = null,
                                    value = peopleList.joinToString(separator = "\n"),
                                ),
                            ),
                    )
                }

                if (actionsList.isNotEmpty()) {
                    InfoSection(
                        title = LocalizedStrings.labelActions,
                        items =
                            listOf(
                                InfoItem(
                                    label = null,
                                    value = actionsList.joinToString(separator = "\n"),
                                ),
                            ),
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
                        items = attachmentsList.map { InfoItem(label = null, value = it) },
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
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = LocalizedStrings.labelRawExtras,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                        )
                        SelectionContainer {
                            Text(
                                text = extras,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class InfoItem(
    val label: String?,
    val value: String,
)

@Composable
private fun InfoSection(
    title: String,
    items: List<InfoItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        items.forEach { InfoLine(it) }
    }
}

@Composable
private fun InfoLine(item: InfoItem) {
    Text(
        text =
            buildAnnotatedString {
                item.label?.takeIf { it.isNotBlank() }?.let { label ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(label)
                        append(": ")
                    }
                }
                append(item.value)
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DebugInfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val valueColor = MaterialTheme.colorScheme.onSurface
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = primary)) {
                append(label)
                append(": ")
            }
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = valueColor)) {
                append(value)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EventBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberResolvedAppLabel(
    packageName: String?,
    providedLabel: String?,
): String? {
    val normalizedProvided = providedLabel?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    var resolved by remember(packageName, normalizedProvided) { mutableStateOf(normalizedProvided) }

    LaunchedEffect(packageName, normalizedProvided) {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        normalizedProvided?.let { PackageLabelResolver.rememberProvided(pkg, it) }

        val current = resolved
        if (!current.isNullOrBlank()) {
            PackageLabelResolver.rememberProvided(pkg, current)
            return@LaunchedEffect
        }

        val cached = PackageLabelResolver.cached(pkg)
        if (!cached.isNullOrBlank()) {
            resolved = cached
            return@LaunchedEffect
        }

        val lookup =
            withContext(Dispatchers.IO) {
                PackageLabelResolver.resolve(context, pkg)
            }
        if (!lookup.isNullOrBlank()) {
            PackageLabelResolver.rememberProvided(pkg, lookup)
            resolved = lookup
        }
    }

    return resolved
}

private object PackageLabelResolver {
    private const val LOG_TAG = "PkgLabelResolver"
    private val cache = ConcurrentHashMap<String, String>()
    private val heuristicHits = AtomicInteger(0)

    fun rememberProvided(
        packageName: String,
        label: String,
    ) {
        if (label.isBlank()) return
        cache.merge(packageName, label) { current, update ->
            when {
                current.isBlank() -> update
                current.equals(update, ignoreCase = true) -> update
                update.length > current.length -> update
                else -> current
            }
        }
    }

    fun cached(packageName: String): String? = cache[packageName]

    fun resolve(
        context: Context,
        packageName: String,
    ): String? {
        cache[packageName]?.let { return it }
        val packageLabel = loadApplicationLabel(context, packageName)
        if (!packageLabel.isNullOrBlank()) {
            cache[packageName] = packageLabel
            return packageLabel
        }
        if (!heuristicsEnabled()) {
            return null
        }
        val heuristic = heuristicsFromPackageName(packageName) ?: return null
        heuristicHits.incrementAndGet()
        FooLog.d(LOG_TAG, "Heuristic label for $packageName -> $heuristic")
        cache[packageName] = heuristic
        return heuristic
    }

    private fun loadApplicationLabel(
        context: Context,
        packageName: String,
    ): String? {
        val packageManager = context.packageManager
        val flagOptions =
            listOf(
                0L,
                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong(),
                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_DIRECT_BOOT_AWARE.toLong(),
                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_DIRECT_BOOT_UNAWARE.toLong(),
            )
        val label =
            flagOptions.firstNotNullOfOrNull { flags ->
                runCatching {
                    val info =
                        packageManager.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(flags),
                        )
                    packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        return label?.takeIf { it.isNotBlank() }
    }

    private fun heuristicsFromPackageName(packageName: String): String? {
        PackageNameOverrides[packageName]?.let { return it }
        val segments = packageName.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        val candidate =
            segments
                .asReversed()
                .firstOrNull { it.lowercase(Locale.getDefault()) !in DullSegments }
                ?: segments.last()
        val normalized = candidate.replace('_', ' ').replace('-', ' ')
        val spaced = normalized.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        val words =
            spaced
                .split(' ')
                .mapNotNull { word ->
                    val trimmed = word.trim()
                    trimmed.ifEmpty { null }
                }
        if (words.isEmpty()) return null
        val display =
            words.joinToString(" ") { raw ->
                val lower = raw.lowercase(Locale.getDefault())
                SegmentOverrides[lower] ?: raw.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
        return display.takeIf { it.isNotBlank() }
    }

    // Segments to ignore when guessing a display name; most are namespace noise or company suffixes.
    private val DullSegments =
        setOf(
            "android",
            "application",
            "applications",
            "app",
            "apps",
            "beta",
            "corp",
            "company",
            "google",
            "inc",
            "io",
            "ltd",
            "mobile",
            "official",
            "prod",
            "production",
            "service",
            "services",
            "studio",
            "team",
            "test",
        )

    // Common tokens mapped to their canonical casing (e.g., "youtube" -> "YouTube") before joining words.
    private val SegmentOverrides =
        mapOf(
            "youtube" to "YouTube",
            "youtubetv" to "YouTube TV",
            "youtubemusic" to "YouTube Music",
            "gmail" to "Gmail",
            "whatsapp" to "WhatsApp",
            "messenger" to "Messenger",
            "snapchat" to "Snapchat",
            "instagram" to "Instagram",
            "facebook" to "Facebook",
            "linkedin" to "LinkedIn",
            "reddit" to "Reddit",
            "telegram" to "Telegram",
            "spotify" to "Spotify",
            "slack" to "Slack",
            "teams" to "Teams",
            "zoom" to "Zoom",
            "discord" to "Discord",
            "drive" to "Drive",
            "docs" to "Docs",
            "sheets" to "Sheets",
            "slides" to "Slides",
            "photos" to "Photos",
            "calendar" to "Calendar",
            "maps" to "Maps",
            "meet" to "Meet",
            "fit" to "Fit",
            "todo" to "To Do",
            "todoist" to "Todoist",
        )

    private val PackageNameOverrides =
        mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.google.android.youtube.tv" to "YouTube TV",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.google.android.apps.messaging" to "Messages",
            "com.google.android.gm" to "Gmail",
            "com.google.android.apps.photos" to "Google Photos",
            "com.google.android.apps.maps" to "Google Maps",
            "com.google.android.apps.tachyon" to "Google Meet",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "com.whatsapp" to "WhatsApp",
            "com.snapchat.android" to "Snapchat",
            "com.spotify.music" to "Spotify",
            "com.twitter.android" to "Twitter",
            "com.x.android" to "X",
            "com.discord" to "Discord",
            "org.telegram.messenger" to "Telegram",
            "org.thoughtcrime.securesms" to "Signal",
            "com.google.android.apps.docs" to "Google Drive",
            "com.google.android.apps.tasks" to "Google Tasks",
            "com.microsoft.todos" to "Microsoft To Do",
            "com.microsoft.teams" to "Microsoft Teams",
            "com.linkedin.android" to "LinkedIn",
            "com.reddit.frontpage" to "Reddit",
            "com.slack" to "Slack",
            "us.zoom.videomeetings" to "Zoom",
            "com.amazon.mp3" to "Amazon Music",
        )

    @Suppress("KotlinConstantConditions")
    private fun heuristicsEnabled() = BuildConfig.ENABLE_LABEL_HEURISTICS

    fun stats(): PackageLabelResolverStats =
        PackageLabelResolverStats(
            cacheSize = cache.size,
            heuristicHits = heuristicHits.get(),
            heuristicsEnabled = heuristicsEnabled(),
        )

    fun resetStats() {
        heuristicHits.set(0)
    }
}

internal data class PackageLabelResolverStats(
    val cacheSize: Int,
    val heuristicHits: Int,
    val heuristicsEnabled: Boolean,
)

@Suppress("unused")
internal fun packageLabelResolverStats(): PackageLabelResolverStats = PackageLabelResolver.stats()

@Suppress("unused")
internal fun resetPackageLabelResolverStats() {
    PackageLabelResolver.resetStats()
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
    val locale =
        remember(configuration) {
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

private fun formatInstant(
    instant: Instant,
    formatter: DateTimeFormatter,
): String {
    val zonedDateTime =
        java.time.Instant
            .ofEpochMilli(instant.toEpochMilliseconds())
            .atZone(ZoneId.systemDefault())
    return formatter.format(zonedDateTime)
}

private val PrettyJson =
    Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

/**
 * Attempts to derive a stable coalesce key for an [EventEntity].
 *
 * Precedence rules:
 * 1. `refsJson.key` – trusted server-provided identifier when present.
 * 2. Component-specific heuristics:
 *    - Notifications reuse `subjectEntityId` (typically the notification key).
 *    - Media events map to `media:<pkg>:now_playing:<action>` using `eventAction`
 *      or the trailing segment of `eventType` as a fallback.
 *    - System events collapse well-known types into shared buckets such as
 *      `display_state`, `device_state`, or `wifi_state`.
 *
 * Example outputs: `media:com.spotify.music:now_playing:play`,
 * `display_state`, `power_connection`.
 */
private fun deriveCoalesceKey(
    event: EventEntity,
    refsJson: JsonObject?,
): String? {
    refsJson?.stringOrNull("key")?.takeIf { it.isNotBlank() }?.let { return it }
    return when (event.component) {
        SourceComponentIds.NOTIFICATION_SOURCE -> event.subjectEntityId?.takeIf { it.isNotBlank() }
        SourceComponentIds.MEDIA_SOURCE ->
            event.appPkg?.takeIf { it.isNotBlank() }?.let { pkg ->
                val action = (event.eventAction.takeIf { it.isNotBlank() }
                    ?: event.eventType.substringAfterLast('.'))
                    .lowercase(Locale.ROOT)
                "media:$pkg:now_playing:$action"
            }
        SourceComponentIds.SYSTEM_EVENT_SOURCE ->
            when (event.eventType) {
                SourceEventTypes.DISPLAY_ON,
                SourceEventTypes.DISPLAY_OFF -> "display_state"
                SourceEventTypes.DEVICE_UNLOCK -> "device_unlock"
                SourceEventTypes.DEVICE_BOOT,
                SourceEventTypes.DEVICE_SHUTDOWN -> "device_state"
                SourceEventTypes.POWER_CONNECTED,
                SourceEventTypes.POWER_DISCONNECTED -> "power_connection"
                SourceEventTypes.POWER_CHARGING_STATUS -> "power_status"
                SourceEventTypes.NETWORK_WIFI_CONNECT,
                SourceEventTypes.NETWORK_WIFI_DISCONNECT -> "wifi_state"
                else -> null
            }
        else -> null
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    val element = this[key] ?: return null
    return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: element.toDisplayString()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonArray.stringValues(): List<String> =
    mapNotNull { element ->
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
    val remoteInputs =
        obj
            .arrayOrNull("remoteInputs")
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
    val details =
        buildList {
            obj["uri"]?.toDisplayString()?.takeIf { it.isNotBlank() }?.let { add(it) }
            val resPkg = obj.stringOrNull("resPkg")
            obj["resId"]?.toDisplayString()?.takeIf { it.isNotBlank() }?.let { resId ->
                val pkg = resPkg?.let { "$it/" } ?: ""
                add(pkg + resId)
            }
        }
    return if (details.isEmpty()) type else "$type · ${details.joinToString()}"
}

private fun JsonElement.toDisplayString(): String? =
    when (this) {
        is JsonPrimitive ->
            when {
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

internal val PreviewTimestamp = Instant.fromEpochMilliseconds(1_700_000_000_000)

internal val PreviewNotificationEvent =
    EventEntity(
        eventId = "evt_preview_notification",
        userId = "u_local",
        deviceId = "pixel-9",
        appPkg = "com.mail",
        component = SourceComponentIds.NOTIFICATION_SOURCE,
        eventType = SourceEventTypes.NOTIFICATION_POST,
        eventCategory = "Inbox",
        eventAction = "New message",
        subjectEntity = "Email",
        subjectEntityId = "com.mail:99",
        tsStart = PreviewTimestamp,
        tags = listOf("priority", "gmail"),
        attributes =
            buildJsonObject {
                put(
                    "actor",
                    buildJsonObject {
                        put("appLabel", JsonPrimitive("Mail"))
                        put("packageName", JsonPrimitive("com.mail"))
                    },
                )
                put(
                    "subject",
                    buildJsonObject {
                        put("title", JsonPrimitive("Project Apollo"))
                        put("text", JsonPrimitive("Latest update from Alex"))
                        put("conversationTitle", JsonPrimitive("Team chat"))
                    },
                )
                put(
                    "context",
                    buildJsonObject {
                        put("category", JsonPrimitive("email"))
                        put("channelId", JsonPrimitive("inbox"))
                        put(
                            "rankingInfo",
                            buildJsonObject {
                                put("importance", JsonPrimitive(4))
                                put("isConversation", JsonPrimitive(true))
                            },
                        )
                    },
                )
                put(
                    "traits",
                    buildJsonObject {
                        put("template", JsonPrimitive("MessagingStyle"))
                        put(
                            "people",
                            buildJsonArray {
                                add(buildJsonObject { put("name", JsonPrimitive("Alex King")) })
                                add(buildJsonObject { put("name", JsonPrimitive("Maya Singh")) })
                            },
                        )
                        put(
                            "actions",
                            buildJsonArray {
                                add(buildJsonObject { put("title", JsonPrimitive("Reply")) })
                                add(buildJsonObject { put("title", JsonPrimitive("Mark read")) })
                                add(buildJsonObject { put("title", JsonPrimitive("Archive")) })
                            },
                        )
                    },
                )
                put(
                    "refs",
                    buildJsonObject {
                        put("key", JsonPrimitive("notification-key"))
                        put("user", JsonPrimitive("UserHandle{0}"))
                    },
                )
                put(
                    "subjectLines",
                    buildJsonArray {
                        add(JsonPrimitive("Sprint planning moved to Friday."))
                        add(JsonPrimitive("See shared doc for tasks."))
                    },
                )
            },
        rawFingerprint = "notif:mail:apollo",
    )

internal val PreviewMediaSessionEvent =
    EventEntity(
        eventId = "evt_preview_media",
        userId = "u_local",
        deviceId = "pixel-9",
        appPkg = "com.spotify.music",
        component = SourceComponentIds.MEDIA_SOURCE,
        eventType = SourceEventTypes.MEDIA_STOP,
        eventCategory = "media",
        eventAction = "stop",
        subjectEntity = "track",
        subjectEntityId = "song:1234",
        tsStart = PreviewTimestamp,
        tsEnd = PreviewTimestamp,
        tags = listOf("music", "now_playing"),
        attributes =
            buildJsonObject {
                put(
                    "actor",
                    buildJsonObject {
                        put("appLabel", JsonPrimitive("Spotify"))
                        put("packageName", JsonPrimitive("com.spotify.music"))
                    },
                )
                put("title", JsonPrimitive("Beyond the Sun"))
                put("artist", JsonPrimitive("Valentina Miras"))
                put("album", JsonPrimitive("Starlight Echoes"))
                put("source_app", JsonPrimitive("com.spotify.music"))
                put("output_route", JsonPrimitive("Pixel Buds"))
            },
        metrics =
            buildJsonObject {
                put("played_ms", JsonPrimitive(192_000))
                put("volume_stream_music", JsonPrimitive(7))
            },
        rawFingerprint = "media:spotify:track:stop",
    )

internal val PreviewGenericEvent =
    EventEntity(
        eventId = "evt_preview_generic",
        userId = "u_local",
        deviceId = "pixel-9",
        component = "system_monitor",
        eventType = "system.event",
        eventCategory = "System",
        eventAction = "Battery Optimized",
        subjectEntity = "Power Manager",
        tsStart = PreviewTimestamp,
        tags = listOf("system", "battery"),
        attributes =
            buildJsonObject {
                put(
                    "subject",
                    buildJsonObject {
                        put("title", JsonPrimitive("Battery optimization enabled"))
                        put("text", JsonPrimitive("Adaptive Battery is now active for background apps."))
                    },
                )
                put(
                    "context",
                    buildJsonObject {
                        put("category", JsonPrimitive("system"))
                    },
                )
                put(
                    "traits",
                    buildJsonObject {
                        put("template", JsonPrimitive("Status"))
                    },
                )
            },
        rawFingerprint = "system:event:preview",
    )

internal val PreviewEvents =
    listOf(
        PreviewNotificationEvent,
        PreviewMediaSessionEvent,
        PreviewGenericEvent,
    )

@Composable
private fun EventCardPreviewHost(event: EventEntity) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EventCard(
                event = event,
                isSelected = false,
                actionsEnabled = true,
                onClick = null,
                onLongPress = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
        }
    }
}

@Preview(name = "Notification Event", showBackground = true)
@Composable
private fun EventCardNotificationPreview() {
    EventCardPreviewHost(PreviewNotificationEvent)
}

@Preview(name = "Media Session Event", showBackground = true)
@Composable
private fun EventCardMediaSessionPreview() {
    EventCardPreviewHost(PreviewMediaSessionEvent)
}

@Preview(name = "Generic Event", showBackground = true)
@Composable
private fun EventCardGenericPreview() {
    EventCardPreviewHost(PreviewGenericEvent)
}
