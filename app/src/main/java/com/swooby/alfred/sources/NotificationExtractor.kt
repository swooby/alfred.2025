//@file:Suppress("DEPRECATION")

package com.swooby.alfred.sources

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import com.smartfoo.android.core.crypto.FooCrypto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * High-level envelope that coalesces cleanly with a "universal event" table.
 * Keep types broad to survive OEM quirks and schema evolution.
 */
data class EventEnvelope(
    val event: String,
    val time: Long,
    val actor: Map<String, Any?>,
    val subject: Map<String, Any?>,
    val source: Map<String, Any?>,
    val context: Map<String, Any?>,
    val attributes: Map<String, Any?>,
    val metrics: Map<String, Any?>,
    val refs: Map<String, Any?>,
    val attachments: List<Map<String, Any?>>, // icon/picture references (no blobs)
    val integrity: Map<String, Any?>,
    val rawExtrasJson: JsonObject
)

/** Main API */
object NotificationExtractor {
    const val PARSER_VERSION = "notification_extractor_v1"

    @JvmStatic
    fun extract(
        context: Context,
        eventType: String,
        sbn: StatusBarNotification,
        rankingMap: RankingMap? = null,
    ): EventEnvelope {
        val notification = sbn.notification
        val extras = NotificationCompat.getExtras(notification) ?: Bundle.EMPTY
        val template = extras.getString("android.template")
        val rawExtras = bundleToJson(extras)

        val actor = appIdentity(context, sbn)
        val refs = mutableMapOf<String, Any?>()
        refs.putMeaningful("key", sbn.key)
        refs.putMeaningful("id", sbn.id)
        refs.putMeaningful("tag", sbn.tag)
        refs.putMeaningful("pkg", sbn.packageName)
        refs.putMeaningful("channelId", notification.channelId)
        refs.putMeaningful("user", sbn.user?.toString())

        val (rankingInfo, rankingChannel) = rankingMapInfo(rankingMap, sbn.key)

        val contextMap = mutableMapOf<String, Any?>()
        contextMap.putMeaningful("category", notification.category)
        contextMap.putMeaningful("priority", notification.priority)
        contextMap.putMeaningful("visibility", notification.visibility)
        contextMap.putMeaningful("when", notification.`when`.takeIf { it > 0 })
        notification.color.takeIf { it != Notification.COLOR_DEFAULT }?.let {
            contextMap["color"] = it
        }
        extras.getBoolean("android.colorized", false).takeIfTrue()?.let {
            contextMap["colorized"] = true
        }
        ((notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0).takeIfTrue()?.let {
            contextMap["onlyAlertOnce"] = true
        }
        sbn.isOngoing.takeIfTrue()?.let { contextMap["ongoing"] = true }
        (!sbn.isClearable).takeIfTrue()?.let { contextMap["unclearable"] = true }
        sbn.isClearable.takeIfTrue()?.let { contextMap["clearable"] = true }
        notification.group.takeIf { !it.isNullOrBlank() }?.let { contextMap["groupKey"] = it }
        ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0).takeIfTrue()?.let {
            contextMap["isGroupSummary"] = true
        }
        notification.timeoutAfter.takeIf { it > 0 }?.let { contextMap["timeoutAfter"] = it }
        extras.getBoolean("android.showWhen", true).takeIf { it && notification.`when` > 0 }?.let {
            contextMap["showWhen"] = true
        }
        notification.tickerText?.toString()?.takeUnless { it.isBlank() }?.let {
            contextMap["ticker"] = it
        }
        notification.publicVersion?.category.takeUnless { it.isNullOrBlank() }?.let {
            contextMap["visibilityPublicVersion"] = it
        }
        if (rankingInfo.isNotEmpty()) {
            contextMap["rankingInfo"] = rankingInfo
        }
        rankingChannel?.let { contextMap["rankingChannel"] = it }

        val subject = buildSubjectFromTemplate(extras, template)

        val actions = buildActions(notification)
        val people = buildPeople(notification, extras)
        val attachments = buildAttachments(notification, extras)
        val styleBlock = buildStyleBlock(extras, template)
        val intents = pendingIntentStubs(notification)
        val bubble = bubbleStub(notification)

        val attributeMap = mutableMapOf<String, Any?>()
        attributeMap.putMeaningful("template", template ?: "Base")
        notification.shortcutId.takeUnless { it.isNullOrBlank() }?.let { attributeMap["shortcutId"] = it }
        notification.locusId?.id.takeUnless { it.isNullOrBlank() }?.let { attributeMap["locusId"] = it }
        if (people.isNotEmpty()) attributeMap["people"] = people
        if (actions.isNotEmpty()) attributeMap["actions"] = actions
        if (styleBlock.isNotEmpty()) attributeMap["style"] = styleBlock
        if (intents.isNotEmpty()) attributeMap["intents"] = intents
        bubble?.let { attributeMap["bubble"] = it }

        val metrics = mutableMapOf<String, Any?>()
        metrics.putMeaningful("actionsCount", actions.size.takeIf { it > 0 })
        metrics.putMeaningful("peopleCount", people.size.takeIf { it > 0 })
        (subject["title"] as? String)?.length?.takeIf { it > 0 }?.let {
            metrics["titleLength"] = it
        }
        (subject["text"] as? String)?.length?.takeIf { it > 0 }?.let {
            metrics["textLength"] = it
        }
        metrics.putMeaningful("extrasFieldCount", rawExtras.size)

        val source = mapOf(
            "source" to "android.notification",
            "api" to Build.VERSION.SDK_INT
        )

        val integrity = mutableMapOf<String, Any?>()
        integrity.putMeaningful("snapshotHash", FooCrypto.SHA256(rawExtras.toString()))
        integrity.putMeaningful("fieldsPresent", rawExtras.keys.toList())

        return EventEnvelope(
            event = eventType,
            time = sbn.postTime,
            actor = actor,
            subject = subject,
            source = source,
            context = contextMap,
            attributes = attributeMap,
            metrics = metrics,
            refs = refs,
            attachments = attachments,
            integrity = integrity,
            rawExtrasJson = rawExtras
        )
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun appIdentity(context: Context, sbn: StatusBarNotification): Map<String, Any?> {
        val pm = context.packageManager
        val pkg = sbn.packageName
        val appLabel = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai)?.toString()
        } catch (_: Exception) {
            null
        }
        val uid = try {
            pm.getApplicationInfo(pkg, 0).uid
        } catch (_: Exception) {
            null
        }
        return buildMap {
            putMeaningful("packageName", pkg)
            putMeaningful("appLabel", appLabel)
            putMeaningful("uid", uid)
        }
    }

    private fun rankingMapInfo(
        rankingMap: RankingMap?,
        key: String
    ): Pair<Map<String, Any?>, Map<String, Any?>?> {
        if (rankingMap == null) return Pair(emptyMap(), null)
        val ranking = Ranking()
        if (!rankingMap.getRanking(key, ranking)) return Pair(emptyMap(), null)

        val info = mutableMapOf<String, Any?>()
        info.putMeaningful("importance", ranking.importance)
        info.putMeaningful("rank", ranking.rank.takeIf { it >= 0 })
        ranking.isAmbient.takeIfTrue()?.let { info["ambient"] = true }
        ranking.isSuspended.takeIfTrue()?.let { info["suspended"] = true }
        ranking.canShowBadge().takeIfTrue()?.let { info["canShowBadge"] = true }
        (ranking.userSentiment.takeIf { it != Ranking.USER_SENTIMENT_NEUTRAL })?.let {
            info["userSentiment"] = it
        }
        ranking.isConversation.takeIfTrue()?.let { info["isConversation"] = true }

        val channel = ranking.channel?.let { ch ->
            mutableMapOf<String, Any?>().apply {
                putMeaningful("id", ch.id)
                putMeaningful("name", ch.name?.toString())
                putMeaningful("importance", ch.importance)
                putMeaningful("group", ch.group)
            }
        }

        return Pair(info, channel)
    }

    private fun buildSubjectFromTemplate(extras: Bundle, template: String?): Map<String, Any?> {
        return when (template) {
            "android.app.Notification\$MessagingStyle" -> {
                val messages = (extras.getParcelableArray("android.messages") ?: emptyArray())
                    .mapNotNull { parcel ->
                        (parcel as? Bundle)?.let { bundle ->
                            val text = bundle.getCharSequence("text")?.toString()
                            val sender = bundle.getCharSequence("sender")?.toString()
                                ?: (bundle.getParcelable<Parcelable>("sender_person") as? Person)?.name?.toString()
                            val timestamp = bundle.getLong("time")
                            mutableMapOf<String, Any?>().apply {
                                putMeaningful("text", text)
                                putMeaningful("sender", sender)
                                putMeaningful("timestamp", timestamp.takeIf { it > 0 })
                            }.takeIf { it.isNotEmpty() }
                        }
                    }
                mutableMapOf<String, Any?>().apply {
                    putMeaningful("template", "MessagingStyle")
                    putMeaningful(
                        "conversationTitle",
                        extras.getCharSequence("android.conversationTitle")?.toString()
                    )
                    extras.getBoolean("android.isGroupConversation").takeIfTrue()?.let {
                        this["isGroupConversation"] = true
                    }
                    if (messages.isNotEmpty()) this["messages"] = messages
                    putMeaningful("title", extras.getCharSequence("android.title")?.toString())
                }
            }
            "android.app.Notification\$InboxStyle" -> mutableMapOf<String, Any?>().apply {
                putMeaningful("template", "InboxStyle")
                val lines = extras.getCharSequenceArray("android.textLines")
                    ?.mapNotNull { it?.toString()?.takeUnless(String::isBlank) }
                if (!lines.isNullOrEmpty()) this["lines"] = lines
                putMeaningful("title", extras.getCharSequence("android.title")?.toString())
                putMeaningful(
                    "summaryText",
                    extras.getCharSequence("android.summaryText")?.toString()
                )
            }
            "android.app.Notification\$BigTextStyle" -> mutableMapOf<String, Any?>().apply {
                putMeaningful("template", "BigTextStyle")
                putMeaningful("title", extras.getCharSequence("android.title")?.toString())
                putMeaningful("text", extras.getCharSequence("android.bigText")?.toString())
                putMeaningful("subText", extras.getCharSequence("android.subText")?.toString())
            }
            "android.app.Notification\$BigPictureStyle" -> mutableMapOf<String, Any?>().apply {
                putMeaningful("template", "BigPictureStyle")
                putMeaningful("title", extras.getCharSequence("android.title")?.toString())
                putMeaningful("text", extras.getCharSequence("android.text")?.toString())
                extras.getParcelable<Parcelable>("android.picture")?.let {
                    this["hasPicture"] = true
                }
            }
            else -> mutableMapOf<String, Any?>().apply {
                putMeaningful("template", template ?: "Base")
                putMeaningful("title", extras.getCharSequence("android.title")?.toString())
                putMeaningful("text", extras.getCharSequence("android.text")?.toString())
                putMeaningful("subText", extras.getCharSequence("android.subText")?.toString())
                putMeaningful("infoText", extras.getCharSequence("android.infoText")?.toString())
            }
        }
    }

    private fun buildStyleBlock(extras: Bundle, template: String?): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>()
        base.putMeaningful("template", template ?: "Base")
        if (template == "android.app.Notification\$MediaStyle") {
            extras.getParcelable<Parcelable>("android.mediaSession")?.let {
                base["hasMediaSession"] = true
            }
            extras.getIntArray("android.compactActions")?.takeIf { it.isNotEmpty() }?.let {
                base["compactActionIndices"] = it.toList()
            }
        }
        if (template == "android.app.Notification\$CallStyle") {
            base["isCall"] = true
            base.putMeaningful("callType", extras.getInt("android.callType", -1).takeIf { it >= 0 })
            extras.getBoolean("android.callIsOngoing").takeIfTrue()?.let { base["isOngoing"] = true }
            extras.getParcelable<Person>("android.callPerson")?.name?.toString()?.takeUnless(String::isBlank)?.let {
                base["caller"] = it
            }
        }
        return base.filterValues { it != null }
    }

    private fun buildActions(notification: Notification): List<Map<String, Any?>> {
        val actions = notification.actions ?: emptyArray()
        if (actions.isEmpty()) return emptyList()
        return actions.mapNotNull { action ->
            val remoteInputs = action.remoteInputs?.mapNotNull { ri ->
                mutableMapOf<String, Any?>().apply {
                    putMeaningful("resultKey", ri.resultKey)
                    ri.allowFreeFormInput.takeIfTrue()?.let { this["allowFreeForm"] = true }
                    ri.choices?.mapNotNull { it?.toString()?.takeUnless(String::isBlank) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { this["choices"] = it }
                }.takeIf { it.isNotEmpty() }
            } ?: emptyList()
            mutableMapOf<String, Any?>().apply {
                putMeaningful("title", action.title?.toString())
                val semantic = action.semanticAction
                if (semantic != Notification.Action.SEMANTIC_ACTION_NONE) {
                    this["semanticAction"] = semantic
                }
                action.isContextual.takeIfTrue()?.let { this["isContextual"] = true }
                if (remoteInputs.isNotEmpty()) {
                    this["remoteInputs"] = remoteInputs
                }
            }.takeIf { it.isNotEmpty() }
        }
    }

    private fun buildPeople(notification: Notification, extras: Bundle): List<Map<String, Any?>> {
        val people = mutableListOf<Map<String, Any?>>()
        val persons = extras.getParcelableArrayList("android.people.list", Person::class.java)
                    ?.filterNotNull()
                    ?: emptyList()
        persons.forEach { person ->
            val stub = personStub(person)
            if (stub.isNotEmpty()) people += stub
        }
        extras.getStringArray("android.people")?.forEach { uri ->
            uri?.takeUnless { it.isBlank() }?.let {
                people += mapOf("uri" to it)
            }
        }
        notification.extras.getCharSequenceArray("android.people")?.forEach { seq ->
            seq?.toString()?.takeUnless { it.isBlank() }?.let {
                people += mapOf("name" to it)
            }
        }
        return people
    }

    private fun buildAttachments(notification: Notification, extras: Bundle): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        notification.smallIcon?.let { icon ->
            iconStub("smallIcon", icon)?.let { result += it }
        }
        notification.getLargeIcon()?.let { icon ->
            iconStub("largeIcon", icon)?.let { result += it }
        }
        (extras.getParcelable<Parcelable>("android.picture") as? Icon)?.let { icon ->
            iconStub("bigPicture", icon)?.let { result += it }
        }
        notification.sound?.let { uri ->
            result += mapOf("type" to "sound", "uri" to uri.toString())
        }
        notification.vibrate?.takeIf { it.isNotEmpty() }?.let { pattern ->
            result += mapOf("type" to "vibration", "patternSize" to pattern.size)
        }
        return result
    }

    private fun pendingIntentStubs(notification: Notification): Map<String, Any?> {
        fun stub(pi: android.app.PendingIntent?): Map<String, Any?>? {
            if (pi == null) return null
            return mutableMapOf<String, Any?>().apply {
                putMeaningful("creatorPackage", pi.creatorPackage)
                pi.isImmutable.takeIfTrue()?.let { this["isImmutable"] = true }
            }.takeIf { it.isNotEmpty() }
        }
        val map = mutableMapOf<String, Any?>()
        stub(notification.contentIntent)?.let { map["contentIntent"] = it }
        stub(notification.deleteIntent)?.let { map["deleteIntent"] = it }
        stub(notification.fullScreenIntent)?.let { map["fullScreenIntent"] = it }
        return map
    }

    private fun bubbleStub(notification: Notification): Map<String, Any?>? {
        val bubble = notification.bubbleMetadata ?: return null
        return mutableMapOf<String, Any?>().apply {
            putMeaningful("desiredHeight", bubble.desiredHeight.takeIf { it > 0 })
            bubble.autoExpandBubble.takeIfTrue()?.let { this["autoExpand"] = true }
            bubble.isNotificationSuppressed.takeIfTrue()?.let { this["suppressNotif"] = true }
        }.takeIf { it.isNotEmpty() }
    }

    private fun personStub(person: Person): Map<String, Any?> = mutableMapOf<String, Any?>().apply {
        putMeaningful("name", person.name?.toString())
        putMeaningful("uri", person.uri)
        putMeaningful("key", person.key)
    }

    private fun iconStub(kind: String, icon: Icon): Map<String, Any?>? {
        val map = mutableMapOf<String, Any?>()
        map["type"] = kind
        map["iconType"] = icon.type
        tryOrNull { icon.resId }?.let { map["resId"] = it }
        tryOrNull { icon.resPackage }?.let { map["resPkg"] = it }
        tryOrNull { icon.uri?.toString() }?.takeUnless { it.isNullOrBlank() }?.let {
            map["uri"] = it
        }
        return map.takeIf { it.isNotEmpty() }
    }

    // --- Bundle → JSON ------------------------------------------------------------------------

    private fun bundleToJson(bundle: Bundle?): JsonObject {
        if (bundle == null) return JsonObject(emptyMap())
        return buildJsonObject {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                valueToJsonElement(value, includeFalse = true, includeBlank = true)?.let { element ->
                    put(key, element)
                }
            }
        }
    }

    private fun valueToJsonElement(
        value: Any?,
        includeFalse: Boolean = false,
        includeBlank: Boolean = false
    ): JsonElement? = when (value) {
        null -> null
        is Boolean -> if (value || includeFalse) JsonPrimitive(value) else null
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is CharSequence -> {
            val text = value.toString()
            if (text.isBlank() && !includeBlank) null else JsonPrimitive(text)
        }
        is Bundle -> bundleToJson(value)
        is Parcelable -> parcelableToStubJson(value)
        is Array<*> -> buildJsonArray {
            value.forEach { item ->
                valueToJsonElement(item, includeFalse, includeBlank)?.let { add(it) }
            }
        }.takeUnless(JsonArray::isEmpty)
        is List<*> -> buildJsonArray {
            value.forEach { item ->
                valueToJsonElement(item, includeFalse, includeBlank)?.let { add(it) }
            }
        }.takeUnless(JsonArray::isEmpty)
        is SparseArray<*> -> buildJsonArray {
            for (i in 0 until value.size()) {
                valueToJsonElement(value.valueAt(i), includeFalse, includeBlank)?.let { add(it) }
            }
        }.takeUnless(JsonArray::isEmpty)
        is Map<*, *> -> buildJsonObject {
            value.entries.forEach { (k, v) ->
                val key = k?.toString() ?: return@forEach
                valueToJsonElement(v, includeFalse, includeBlank)?.let { put(key, it) }
            }
        }.takeUnless(JsonObject::isEmpty)
        is Uri -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value.toDouble())
        is Enum<*> -> JsonPrimitive(value.name)
        else -> JsonPrimitive(value.toString())
    }

    private fun parcelableToStubJson(parcelable: Parcelable): JsonObject = when (parcelable) {
        is Icon -> buildJsonObject {
            put("type", JsonPrimitive("Icon"))
            put("iconType", JsonPrimitive(parcelable.type))
            tryOrNull { parcelable.resId }?.let { put("resId", JsonPrimitive(it)) }
            tryOrNull { parcelable.resPackage }?.let { put("resPkg", JsonPrimitive(it)) }
            tryOrNull { parcelable.uri?.toString() }?.takeUnless { it.isNullOrBlank() }?.let {
                put("uri", JsonPrimitive(it))
            }
        }
        is Person -> buildJsonObject {
            personStub(parcelable).forEach { (k, v) ->
                valueToJsonElement(v, includeFalse = true, includeBlank = true)?.let { put(k, it) }
            }
        }
        is RemoteInput -> buildJsonObject {
            put("type", JsonPrimitive("RemoteInput"))
            put("resultKey", JsonPrimitive(parcelable.resultKey))
            parcelable.allowFreeFormInput.takeIfTrue()?.let { put("allowFreeForm", JsonPrimitive(true)) }
            parcelable.choices?.mapNotNull { it?.toString()?.takeUnless(String::isBlank) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { list ->
                    put("choices", buildJsonArray { list.forEach { add(JsonPrimitive(it)) } })
                }
        }
        else -> buildJsonObject {
            put("type", JsonPrimitive(parcelable.javaClass.name))
        }
    }

    // --- Utils --------------------------------------------------------------------------------

    private inline fun Boolean.takeIfTrue(): Boolean? = if (this) this else null

    private inline fun <T> tryOrNull(block: () -> T): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    private fun MutableMap<String, Any?>.putMeaningful(key: String, value: Any?) {
        when (value) {
            null -> return
            is Boolean -> if (value) this[key] = true
            is String -> if (value.isNotBlank()) this[key] = value
            is Collection<*> -> if (value.isNotEmpty()) this[key] = value
            is Array<*> -> if (value.isNotEmpty()) this[key] = value
            is Map<*, *> -> if (value.isNotEmpty()) this[key] = value
            else -> this[key] = value
        }
    }
}

private fun JsonObject.isEmpty(): Boolean = this.size == 0

private fun JsonArray.isEmpty(): Boolean = this.size == 0
