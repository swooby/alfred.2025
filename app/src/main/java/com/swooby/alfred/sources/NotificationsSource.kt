package com.swooby.alfred.sources

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.WorkerThread
import java.security.MessageDigest
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.BuildConfig
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.AttachmentRef
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.data.Sensitivity
import com.swooby.alfred.util.FooLog
import com.swooby.alfred.util.FooNotificationListener
import com.swooby.alfred.util.FooPlatformUtils
import com.swooby.alfred.util.FooSha256
import com.swooby.alfred.util.FooString
import com.swooby.alfred.util.Ulids
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

class NotificationsSource : NotificationListenerService() {
    companion object {
        private val TAG = FooLog.TAG(NotificationsSource::class.java)
        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
        private val LOG_NOTIFICATION = true && BuildConfig.DEBUG

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
    }

    private val app get() = application as AlfredApp

    private val activeNotificationsSnapshot = ActiveNotificationsSnapshot()

    private fun getActiveNotificationsSnapshot(): ActiveNotificationsSnapshot {
        return activeNotificationsSnapshot.snapshot(this)
    }

    fun initializeActiveNotifications() {
        val activeNotificationsSnapshot = getActiveNotificationsSnapshot()
        initializeActiveNotifications(activeNotificationsSnapshot)
    }

    private fun initializeActiveNotifications(activeNotificationsSnapshot: ActiveNotificationsSnapshot) {
        if (LOG_NOTIFICATION) {
            FooLog.d(TAG, "#NOTIFICATION initializeActiveNotifications(activeNotificationsSnapshot(${activeNotificationsSnapshot.activeNotifications?.size}))")
        }
        for (activeNotification in activeNotificationsSnapshot.activeNotificationsRanked.orEmpty()) {
            FooLog.v(TAG, "#NOTIFICATION initializeActiveNotifications: activeNotification=$activeNotification")
            onNotificationPosted(activeNotification, activeNotificationsSnapshot.currentRanking)
        }
    }

    override fun onListenerConnected() {
        if (LOG_NOTIFICATION) {
            FooLog.d(TAG, "#NOTIFICATION onListenerConnected()")
        }

        //mOnListenerConnectedStartMillis = System.currentTimeMillis()

        // Now we have access; start the media source if PipelineService is running.
        val app = application as AlfredApp
        try {
            app.mediaSource.start("$TAG.onListenerConnected")
        } catch (se: SecurityException) {
            FooLog.w(TAG, "onListenerConnected: SecurityException", se)
            /* ignore */
        }

        initializeActiveNotifications()
    }

    override fun onListenerDisconnected() {
        if (LOG_NOTIFICATION) {
            FooLog.d(TAG, "#NOTIFICATION onListenerDisconnected()")
        }

        //val elapsedMillis = System.currentTimeMillis() - mOnListenerConnectedStartMillis
    }

    private fun buildStableNotificationFingerprint(
        pkg: String,
        subjectEntityId: String?,
        subjectTitle: String?,
        subjectText: String?,
        subjectLines: List<String>?,
        eventCategory: String?,
        eventAction: String?
    ): String? {
        val body = subjectText ?: subjectLines
            ?.joinToString(separator = "\n")
            ?.takeIf { it.isNotBlank() }
        val parts = listOf(
            pkg.takeIf { it.isNotBlank() },
            subjectEntityId?.takeIf { it.isNotBlank() },
            subjectTitle?.takeIf { it.isNotBlank() },
            body,
            eventCategory?.takeIf { it.isNotBlank() },
            eventAction?.takeIf { it.isNotBlank() }
        )
        if (parts.all { it.isNullOrBlank() }) return null
        val normalized = parts.joinToString(separator = "|") { it ?: "" }
        return FooSha256.sha256(normalized)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (LOG_NOTIFICATION) {
            FooLog.d(TAG, "#NOTIFICATION onNotificationPosted(sbn=$sbn, rankingMap=$rankingMap)")
        }

        if (sbn == null) return

        val envelope = NotificationExtractor.extract(this, SourceEventTypes.NOTIFICATION_POST, sbn, rankingMap)
        val pkg = (envelope.actor["packageName"] as? String)?.takeIf { it.isNotBlank() }
            ?: sbn.packageName
        val appLabel = (envelope.actor["appLabel"] as? String)?.takeIf { it.isNotBlank() }
        val template = (envelope.attributes["template"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.subject["template"] as? String)?.takeIf { it.isNotBlank() }
        val contextCategory = (envelope.context["category"] as? String)?.takeIf { it.isNotBlank() }
        val subjectTitle = extractSubjectTitle(envelope.subject)
        val subjectText = extractSubjectBody(envelope.subject)
        val subjectLines = (envelope.subject["lines"] as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        val subjectEntity = subjectTitle ?: appLabel ?: pkg
        val eventCategory = contextCategory ?: template ?: "notification"
        val eventAction = subjectText ?: subjectTitle ?: template ?: envelope.event
        val subjectEntityId = (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
            ?: "$pkg:${sbn.id}"
        val subjectParentId = (envelope.context["groupKey"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["tag"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["channelId"] as? String)?.takeIf { it.isNotBlank() }
        val postInstant = Instant.fromEpochMilliseconds(envelope.time)
        val metrics = envelope.metrics.toJsonObjectOrNull() ?: JsonObject(emptyMap())
        val attachments = envelope.attachments.mapNotNull { attachment ->
            val kind = (attachment["type"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uri = (attachment["uri"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mime = (attachment["mime"] as? String)?.takeIf { it.isNotBlank() }
            val size = (attachment["sizeBytes"] as? Number)?.toLong()
            AttachmentRef(kind = kind, uri = uri, mime = mime, sizeBytes = size)
        }
        val attributes = buildJsonObject {
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
        val fingerprint = buildStableNotificationFingerprint(
            pkg = pkg,
            subjectEntityId = subjectEntityId,
            subjectTitle = subjectTitle,
            subjectText = subjectText,
            subjectLines = subjectLines,
            eventCategory = eventCategory,
            eventAction = eventAction
        ) ?: (envelope.integrity["snapshotHash"] as? String)?.takeIf { it.isNotBlank() }
            ?: (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
            ?: sbn.key
        val coalesceKey = (envelope.refs["key"] as? String)?.takeIf { it.isNotBlank() }
        val tags = mutableListOf<String>()
        contextCategory?.let { tags += it }
        template?.let { tags += it }
        if ((envelope.context["rankingInfo"] as? Map<*, *>)?.isNotEmpty() == true) {
            tags += "ranked"
        }

        val eventId = Ulids.newUlid()
        val sensitivity = inferSensitivity(envelope.subject, subjectText, subjectLines, envelope.rawExtrasJson)

        val ev = EventEntity(
            eventId = eventId,
            schemaVer = 1,
            userId = "u_local",
            deviceId = "android:device",
            appPkg = pkg,
            component = SourceComponentIds.NOTIFICATION_SOURCE,
            parserVer = NotificationExtractor.PARSER_VERSION,
            eventType = envelope.event,
            eventCategory = eventCategory,
            eventAction = eventAction,
            subjectEntity = subjectEntity,
            subjectEntityId = subjectEntityId,
            subjectParentId = subjectParentId,
            tsStart = postInstant,
            ingestAt = postInstant,
            api = envelope.source["api"]?.toString(),
            sensitivity = sensitivity,
            attributes = attributes,
            metrics = metrics,
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (LOG_NOTIFICATION) {
            FooLog.d(TAG, "#NOTIFICATION onNotificationRemoved(sbn=$sbn, rankingMap=$rankingMap, reason=${FooNotificationListener.notificationCancelReasonToString(reason)})")
        }

        //...
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onNotificationRankingUpdate(...)")
        }
    }

    override fun onListenerHintsChanged(hints: Int) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onListenerHintsChanged(...)")
        }
    }

    override fun onSilentStatusBarIconsVisibilityChanged(hideSilentStatusIcons: Boolean) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onSilentStatusBarIconsVisibilityChanged(...)")
        }
    }

    override fun onNotificationChannelModified(pkg: String?, user: UserHandle?, channel: NotificationChannel?, modificationType: Int) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onNotificationChannelModified(...)")
        }
    }

    override fun onNotificationChannelGroupModified(pkg: String?, user: UserHandle?, group: NotificationChannelGroup?, modificationType: Int) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onNotificationChannelGroupModified(...)")
        }
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        if (LOG_NOTIFICATION) {
            FooLog.v(TAG, "#NOTIFICATION onInterruptionFilterChanged(...)")
        }
    }

    //
    //region ActiveNotifications
    //

    /**
     * Holds an immutable snapshot of active notifications and their ranking.
     *
     * Call [snapshot] to populate from a NotificationListenerService, or [reset] to clear.
     */
    class ActiveNotificationsSnapshot {
        /** Snapshot of the [NotificationListenerService] used at the last [snapshot] call; null after [reset]. */
        var notificationListenerService: NotificationsSource? = null
            private set

        /** Snapshot of active notifications at the last [snapshot] call; null after [reset]. */
        var activeNotifications: List<StatusBarNotification>? = null
            private set

        /** Snapshot of the system RankingMap at the last [snapshot] call; null after [reset]. */
        var currentRanking: RankingMap? = null
            private set


        private var _activeNotificationsRanked: List<StatusBarNotification>? = null

        /** Ranked view (top → bottom), cached after the first computation per snapshot. */
        val activeNotificationsRanked: List<StatusBarNotification>?
            get() {
                if (_activeNotificationsRanked == null) {
                    _activeNotificationsRanked = shadeSort(activeNotifications, currentRanking)
                    @Suppress("ConstantConditionIf")
                    if (false) {
                        for (sbn in _activeNotificationsRanked!!) {
                            FooLog.e(TAG, "activeNotificationsRanked: notification=${toString(sbn, showAllExtras = false)}")
                        }
                        FooLog.e(TAG, "activeNotificationsRanked:")
                    }
                }
                return _activeNotificationsRanked
            }

        /** Clears all state back to defaults (empty notifications, null ranking map). */
        fun reset() {
            notificationListenerService = null
            activeNotifications = null
            currentRanking = null
            _activeNotificationsRanked = null
        }

        /**
         * Replaces current state with a fresh snapshot from [service].
         * If [service] is null, behaves like [reset].
         */
        //@WorkerThread
        fun snapshot(service: NotificationsSource?): ActiveNotificationsSnapshot {
            reset()
            notificationListenerService = service
            if (service != null) {
                activeNotifications = service.activeNotifications?.toList()
                currentRanking = service.currentRanking
            }
            return this
        }

        /**
         * Top to bottom order of appearance in the Notification Shade.
         * Analogous to ...?
         */
        private enum class UiBucket(val order: Int) {
            MEDIA(0), CONVERSATION(1), ALERTING(2), SILENT(3)
        }

        private fun isMediaNotificationCompat(n: Notification): Boolean {
            val extras = n.extras
            val hasMediaSession = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
            val isTransport = n.category == Notification.CATEGORY_TRANSPORT
            val template = extras?.getString(Notification.EXTRA_TEMPLATE)
            // Accept framework or compat styles; the literal string contains '$'
            val isMediaStyle = template?.endsWith("\$MediaStyle") == true ||
                    template?.contains("MediaStyle") == true
            return hasMediaSession || isTransport || isMediaStyle
        }

        private fun bucketOfWithRank(sbn: StatusBarNotification, r: Ranking): UiBucket {
            if (isMediaNotificationCompat(sbn.notification)) return UiBucket.MEDIA
            if (r.isConversation) return UiBucket.CONVERSATION
            val isSilent = r.isAmbient || r.importance <= NotificationManager.IMPORTANCE_LOW
            return if (isSilent) UiBucket.SILENT else UiBucket.ALERTING
        }

        /** Fallback when RankingMap is null: heuristic using flags/category/priority. */
        private fun bucketOfNoRank(sbn: StatusBarNotification): UiBucket {
            val n = sbn.notification
            if (isMediaNotificationCompat(n)) return UiBucket.MEDIA
            // Heuristic: treat PRIORITY_LOW or below as silent when no ranking is available
            @Suppress("DEPRECATION")
            val silent = n.priority <= Notification.PRIORITY_LOW
            return if (silent) UiBucket.SILENT else UiBucket.ALERTING
        }

        private fun shadeSort(
            actives: List<StatusBarNotification>?,
            rankingMap: RankingMap?
        ): List<StatusBarNotification> {
            val list = actives ?: return emptyList()
            if (list.isEmpty()) return emptyList()

            // Collapse groups: prefer GROUP_SUMMARY when present
            val summariesByGroup = list
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
                .associateBy { it.notification.group }

            val collapsed = buildList {
                val seen = HashSet<String>()
                list.filter { it.notification.group.isNullOrEmpty() }
                    .forEach { if (seen.add(it.key)) add(it) }
                list.groupBy { it.notification.group }.forEach { (g, members) ->
                    if (g.isNullOrEmpty()) return@forEach
                    val summary = summariesByGroup[g]
                    if (summary != null) {
                        if (seen.add(summary.key)) add(summary)
                    } else {
                        members.forEach { if (seen.add(it.key)) add(it) }
                    }
                }
            }

            // System order index (may be empty if rankingMap == null)
            val sysOrder: Map<String, Int> =
                rankingMap?.orderedKeys?.withIndex()?.associate { it.value to it.index } ?: emptyMap()

            data class K(val bucket: UiBucket, val sys: Int, val tiebreak: Long)
            val keys = HashMap<String, K>(collapsed.size * 2)

            for (sbn in collapsed) {
                val n = sbn.notification
                val sortKey = n.sortKey
                val tiebreak = if (!sortKey.isNullOrEmpty()) sortKey.hashCode().toLong() else -sbn.postTime

                val (bucket, sysIdx) = if (rankingMap != null) {
                    val r = Ranking()
                    val has = rankingMap.getRanking(sbn.key, r)
                    val b = if (has) bucketOfWithRank(sbn, r) else bucketOfNoRank(sbn)
                    val idx = sysOrder[sbn.key] ?: Int.MAX_VALUE
                    b to idx
                } else {
                    bucketOfNoRank(sbn) to Int.MAX_VALUE
                }

                keys[sbn.key] = K(bucket, sysIdx, tiebreak)
            }

            return collapsed.sortedWith(
                compareBy<StatusBarNotification> { keys[it.key]!!.bucket }
                    .thenBy { keys[it.key]!!.sys }
                    .thenBy { keys[it.key]!!.tiebreak }
            )
        }

        companion object {
            fun toString(ranking: Ranking): String {
                return "{key=${ranking.key}, rank=${ranking.rank}}"
            }

            fun toString(sbn: StatusBarNotification, showAllExtras: Boolean = false): String {
                val notification = sbn.notification
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
                var text = extras?.getCharSequence(Notification.EXTRA_TEXT)
                if (text != null) {
                    text = if (text.length > 33) {
                        "(${text.length})${FooString.quote(text.substring(0, 32)).replaceAfterLast("\"", "…\"")}"
                    } else {
                        FooString.quote(text)
                    }
                }
                val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)

                val sb = StringBuilder("{ ")
                if (title != null || text != null || subText != null) {
                    sb.append("extras={ ")
                }
                if (title != null) {
                    sb.append("${Notification.EXTRA_TITLE}=${FooString.quote(title)}")
                }
                if (text != null) {
                    sb.append(", ${Notification.EXTRA_TEXT}=$text")
                }
                if (subText != null) {
                    sb.append(", ${Notification.EXTRA_SUB_TEXT}=${FooString.quote(subText)}")
                }
                if (title != null || text != null || subText != null) {
                    sb.append(" }, ")
                }
                sb.append(
                    "id=${sbn.id}, key=${FooString.quote(sbn.key)}, packageName=${
                        FooString.quote(sbn.packageName)
                    }, notification={ $notification"
                )
                if (showAllExtras) {
                    sb.append(", extras=")
                    if (extras != null) {
                        extras.remove(Notification.EXTRA_TITLE)
                        extras.remove(Notification.EXTRA_TEXT)
                        extras.remove(Notification.EXTRA_SUB_TEXT)
                    }
                    sb.append(FooPlatformUtils.toString(extras))
                }
                sb.append(" } }")
                return sb.toString()
            }
        }
    }
}
