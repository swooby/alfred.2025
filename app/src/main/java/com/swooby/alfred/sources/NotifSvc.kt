package com.swooby.alfred.sources

import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.AttachmentRef
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.data.Sensitivity
import com.swooby.alfred.sources.NotificationExtractor
import com.swooby.alfred.util.Ulids
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

class NotifSvc : NotificationListenerService() {
    private val app get() = application as AlfredApp

    override fun onListenerConnected() {
        // Now we have access; start the media source if PipelineService is running.
        val app = application as AlfredApp
        try {
            app.mediaSource.start()
        } catch (_: SecurityException) {
            /* ignore */
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotificationPosted(sbn, tryGetRanking())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        handleNotificationPosted(sbn, rankingMap)
    }

    private fun handleNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ) {
        val envelope = NotificationExtractor.extract(this, sbn, rankingMap)
        val actorPkg = (envelope.actor["packageName"] as? String)?.takeIf { it.isNotBlank() }
            ?: sbn.packageName
        val appLabel = (envelope.actor["appLabel"] as? String)?.takeIf { it.isNotBlank() }
        val template = (envelope.attributes["template"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.subject["template"] as? String)?.takeIf { it.isNotBlank() }
        val contextCategory = (envelope.context["category"] as? String)?.takeIf { it.isNotBlank() }
        val subjectTitle = extractSubjectTitle(envelope.subject)
        val subjectText = extractSubjectBody(envelope.subject)
        val subjectLines = (envelope.subject["lines"] as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        val subjectEntity = subjectTitle ?: appLabel ?: actorPkg
        val eventCategory = contextCategory ?: template ?: "notification"
        val eventAction = subjectText ?: subjectTitle ?: template ?: envelope.event
        val subjectEntityId = (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
            ?: "$actorPkg:${sbn.id}"
        val subjectParentId = (envelope.context["groupKey"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["tag"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["channelId"] as? String)?.takeIf { it.isNotBlank() }
        val postInstant = Instant.fromEpochMilliseconds(envelope.time)
        val metricsJson = envelope.metrics.toJsonObjectOrNull() ?: JsonObject(emptyMap())
        val attachments = envelope.attachments.mapNotNull { attachment ->
            val kind = (attachment["type"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uri = (attachment["uri"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mime = (attachment["mime"] as? String)?.takeIf { it.isNotBlank() }
            val size = (attachment["sizeBytes"] as? Number)?.toLong()
            AttachmentRef(kind = kind, uri = uri, mime = mime, sizeBytes = size)
        }
        val attributesJson = buildJsonObject {
            envelope.actor.toJsonObjectOrNull()?.let { put("actor", it) }
            envelope.subject.toJsonObjectOrNull()?.let { put("subject", it) }
            envelope.source.toJsonObjectOrNull()?.let { put("source", it) }
            envelope.context.toJsonObjectOrNull()?.let { put("context", it) }
            envelope.attributes.toJsonObjectOrNull()?.let { put("traits", it) }
            envelope.refs.toJsonObjectOrNull()?.let { put("refs", it) }
            envelope.attachments.toJsonArrayOrNull()?.let { put("attachments", it) }
            envelope.integrity.toJsonObjectOrNull()?.let { put("integrity", it) }
            if (!envelope.rawExtrasJson.isEmpty()) {
                put("rawExtras", envelope.rawExtrasJson)
            }
            subjectLines?.takeIf { it.isNotEmpty() }?.let { lines ->
                put("subjectLines", buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } })
            }
        }
        val fingerprint = (envelope.integrity["snapshotHash"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
            ?: sbn.key
        val coalesceKey = (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
        val tags = mutableListOf<String>()
        contextCategory?.let { tags += it }
        template?.let { tags += it }
        if ((envelope.context["rankingInfo"] as? Map<*, *>)?.isNotEmpty() == true) {
            tags += "ranked"
        }

        val sensitivity = inferSensitivity(envelope.subject, subjectText, subjectLines, envelope.rawExtrasJson)
        val ev = EventEntity(
            eventId = Ulids.newUlid(),
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = actorPkg,
            component = "notif_listener",
            parserVer = "notif_extractor_v1",
            eventType = envelope.event,
            eventCategory = eventCategory,
            eventAction = eventAction,
            subjectEntity = subjectEntity,
            subjectEntityId = subjectEntityId,
            subjectParentId = subjectParentId,
            tsStart = postInstant,
            api = envelope.source["api"]?.toString(),
            sensitivity = sensitivity,
            attributes = attributesJson,
            metrics = metricsJson,
            tags = tags.distinct(),
            attachments = attachments,
            rawFingerprint = fingerprint,
            integritySig = (envelope.integrity["snapshotHash"] as? String)?.takeIf { it.isNotBlank() }
        )
        app.ingest.submit(
            RawEvent(
                event = ev,
                fingerprint = fingerprint,
                coalesceKey = coalesceKey
            )
        )
    }

    private fun tryGetRanking(): RankingMap? = try {
        currentRanking
    } catch (_: Throwable) {
        null
    }
}

private fun extractSubjectTitle(subject: Map<String, Any?>): String? {
    val titleKeys = listOf("title", "conversationTitle", "template")
    return titleKeys.asSequence()
        .mapNotNull { key -> (subject[key] as? String)?.takeIf { it.isNotBlank() } }
        .firstOrNull()
}

private fun extractSubjectBody(subject: Map<String, Any?>): String? {
    val bodyKeys = listOf("text", "summaryText", "subText", "infoText")
    val direct = bodyKeys.asSequence()
        .mapNotNull { key -> (subject[key] as? String)?.takeIf { it.isNotBlank() } }
        .firstOrNull()
    if (direct != null) return direct

    val messageText = (subject["messages"] as? List<*>)
        ?.firstNotNullOfOrNull { item ->
            (item as? Map<*, *>)
                ?.get("text")
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        }
    if (messageText != null) return messageText

    val lines = (subject["lines"] as? List<*>)
        ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
    if (!lines.isNullOrEmpty()) return lines.joinToString(separator = "\n")

    return null
}

private fun inferSensitivity(
    subject: Map<String, Any?>,
    body: String?,
    lines: List<String>?,
    rawExtras: JsonObject
): Sensitivity {
    if (!body.isNullOrBlank()) return Sensitivity.CONTENT
    if (!lines.isNullOrEmpty()) return Sensitivity.CONTENT
    val hasMessageContent = (subject["messages"] as? List<*>)?.any { item ->
        (item as? Map<*, *>)
            ?.get("text")
            ?.toString()
            ?.isNotBlank() == true
    } == true
    if (hasMessageContent) return Sensitivity.CONTENT

    val textKeys = listOf("android.text", "android.bigText", "android.summaryText")
    if (textKeys.any { key ->
            rawExtras[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } != null
        }
    ) {
        return Sensitivity.CONTENT
    }

    return Sensitivity.METADATA
}

private fun Map<String, Any?>.toJsonObjectOrNull(): JsonObject? {
    var hasEntry = false
    val obj = buildJsonObject {
        for ((key, value) in this@toJsonObjectOrNull) {
            val name = key.takeIf { it.isNotBlank() } ?: continue
            value.toJsonElementOrNull()?.let {
                put(name, it)
                hasEntry = true
            }
        }
    }
    return if (hasEntry) obj else null
}

private fun List<Map<String, Any?>>.toJsonArrayOrNull(): JsonArray? {
    if (isEmpty()) return null
    val array = buildJsonArray {
        this@toJsonArrayOrNull.forEach { item ->
            item.toJsonObjectOrNull()?.let { add(it) }
        }
    }
    return if (array.isEmpty()) null else array
}

private fun Any?.toJsonElementOrNull(): JsonElement? = when (this) {
    null -> null
    is JsonElement -> this
    is Boolean -> if (this) JsonPrimitive(true) else null
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this.toDouble())
    is String -> this.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) }
    is Map<*, *> -> (this as? Map<String, Any?>)?.toJsonObjectOrNull()
    is List<*> -> {
        val array = buildJsonArray {
            this@toJsonElementOrNull.forEach { value ->
                value.toJsonElementOrNull()?.let { add(it) }
            }
        }
        if (array.isEmpty()) null else array
    }
    is Array<*> -> this.toList().toJsonElementOrNull()
    else -> JsonPrimitive(toString())
}