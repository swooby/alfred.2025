package com.swooby.alfred2017.data

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@ProvidedTypeConverter
class RoomConverters {

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.toInstant()

    @TypeConverter
    fun fromJsonObject(value: JsonObject?): String? =
        value?.let { json.encodeToString(JsonObject.serializer(), it) }

    @TypeConverter
    fun toJsonObject(value: String?): JsonObject? =
        value?.let { json.decodeFromString(JsonObject.serializer(), it) }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(data: String?): List<String> =
        data?.let { json.decodeFromString(it) } ?: emptyList()

    @TypeConverter
    fun fromEventRefList(list: List<EventRef>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toEventRefList(data: String?): List<EventRef> =
        data?.let { json.decodeFromString(it) } ?: emptyList()

    @TypeConverter
    fun fromAttachmentRefList(list: List<AttachmentRef>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toAttachmentRefList(data: String?): List<AttachmentRef> =
        data?.let { json.decodeFromString(it) } ?: emptyList()

    companion object {
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}