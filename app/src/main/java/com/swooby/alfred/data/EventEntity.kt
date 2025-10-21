package com.swooby.alfred.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Entity(
    tableName = "events",
    indices = [
        Index("userId", "tsStart"),
        Index("eventType", "tsStart"),
        Index("deviceId", "tsStart"),
        Index("sessionId"),
    ],
)
@Serializable
data class EventEntity(
    @PrimaryKey val eventId: String,
    val schemaVer: Int = 1,
    val userId: String,
    val deviceId: String,
    val appPkg: String? = null,
    val component: String? = null,
    val parserVer: String? = null,
    val eventType: String,
    val eventCategory: String,
    val eventAction: String,
    val subjectEntity: String,
    val subjectEntityId: String? = null,
    val subjectParentId: String? = null,
    @field:TypeConverters(RoomConverters::class)
    @Contextual
    val tsStart: Instant,
    @field:TypeConverters(RoomConverters::class)
    @Contextual
    val tsEnd: Instant? = null,
    val durationMs: Long? = null,
    val timezone: String? = null,
    @field:TypeConverters(RoomConverters::class)
    @Contextual
    val ingestAt: Instant? = null,

    val platform: String = "android",
    val api: String? = null,
    val confidence: Double? = null,
    val rawFingerprint: String? = null,
    val coalesceKey: String? = null,

    val deviceBrand: String? = null,
    val deviceModel: String? = null,
    val deviceSdk: Int? = null,
    val userInteractive: Boolean? = null,
    val userRinger: String? = null,
    val userDnd: Boolean? = null,
    val audioRoute: String? = null,
    val netTransport: String? = null,
    val ssidHash: String? = null,
    val bssidHash: String? = null,
    val vpn: Boolean? = null,
    val locLevel: String? = null,
    val latE7: Int? = null,
    val lngE7: Int? = null,

    val sensitivity: Sensitivity = Sensitivity.METADATA,
    val e2ee: Boolean = false,
    val retention: String? = "default",
    @field:TypeConverters(RoomConverters::class)
    val piiTags: List<String> = emptyList(),

    @field:TypeConverters(RoomConverters::class)
    val attributes: JsonObject = JsonObject(emptyMap()),
    @field:TypeConverters(RoomConverters::class)
    val metrics: JsonObject = JsonObject(emptyMap()),
    @field:TypeConverters(RoomConverters::class)
    val tags: List<String> = emptyList(),

    val sessionId: String? = null,
    @field:TypeConverters(RoomConverters::class)
    val refs: List<EventRef> = emptyList(),
    @field:TypeConverters(RoomConverters::class)
    val attachments: List<AttachmentRef> = emptyList(),

    val integritySig: String? = null,
)

@Serializable
enum class Sensitivity { NONE, METADATA, CONTENT }

@Serializable
data class EventRef(
    val rel: String,
    val eventId: String,
)

@Serializable
data class AttachmentRef(
    val kind: String,
    val uri: String,
    val mime: String? = null,
    val sizeBytes: Long? = null,
)
