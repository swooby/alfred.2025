package com.swooby.alfred.core.ingest

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Persists the relationship between [RawEvent.coalesceKey] and the last fingerprint we emitted.
 * This allows suppressing known-noisy notifications immediately after process restarts.
 */
interface CoalesceHistoryStore {
    /**
     * Returns the stored entries ordered from least recently used to most recently used.
     */
    suspend fun load(): List<Pair<String, String>>

    /**
     * Persists the supplied entries ordered from least recently used to most recently used.
     */
    suspend fun save(entries: List<Pair<String, String>>)

    object InMemory : CoalesceHistoryStore {
        override suspend fun load(): List<Pair<String, String>> = emptyList()

        override suspend fun save(entries: List<Pair<String, String>>) {
            // no-op
        }
    }
}

class SharedPreferencesCoalesceHistoryStore(
    context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : CoalesceHistoryStore {
    companion object {
        private const val PREFS_NAME = "event_ingest"
        private const val KEY_HISTORY = "coalesce_history"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): List<Pair<String, String>> =
        withContext(ioContext) {
            val raw = prefs.getString(KEY_HISTORY, null) ?: return@withContext emptyList()
            try {
                val jsonElement = json.parseToJsonElement(raw)
                val array = jsonElement as? JsonArray ?: return@withContext emptyList()
                array.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull
                    val fingerprint = obj["fingerprint"]?.jsonPrimitive?.contentOrNull
                    if (key.isNullOrEmpty() || fingerprint.isNullOrEmpty()) {
                        null
                    } else {
                        key to fingerprint
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

    override suspend fun save(entries: List<Pair<String, String>>) {
        val normalized = entries.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        withContext(ioContext) {
            if (normalized.isEmpty()) {
                prefs.edit { remove(KEY_HISTORY) }
                return@withContext
            }
            val payload =
                buildJsonArray {
                    normalized.forEach { (key, fingerprint) ->
                        add(
                            buildJsonObject {
                                put("key", JsonPrimitive(key))
                                put("fingerprint", JsonPrimitive(fingerprint))
                            },
                        )
                    }
                }
            prefs.edit { putString(KEY_HISTORY, payload.toString()) }
        }
    }
}
