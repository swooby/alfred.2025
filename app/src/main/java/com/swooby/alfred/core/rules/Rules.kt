package com.swooby.alfred.core.rules

import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.sources.SourceEventTypes
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class RulesConfig(
    val enabledTypes: Set<String> =
        setOf(
            SourceEventTypes.MEDIA_START,
            SourceEventTypes.MEDIA_STOP,
            SourceEventTypes.NOTIFICATION_POST,
            SourceEventTypes.DISPLAY_ON,
            SourceEventTypes.DISPLAY_OFF,
            SourceEventTypes.DEVICE_UNLOCK,
        ),
    val disabledApps: Set<String> = emptySet(),
    val quietHours: QuietHours? = null,
    val speakWhenScreenOffOnly: Boolean = false,
    val rateLimits: List<RateLimit> =
        listOf(
            RateLimit(SourceEventTypes.MEDIA_START, perSeconds = 30, maxEvents = 4),
            RateLimit(SourceEventTypes.NOTIFICATION_POST, perSeconds = 10, maxEvents = 6),
        ),
)

data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
)

data class RateLimit(
    val keyPrefix: String,
    val perSeconds: Int,
    val maxEvents: Int,
)

sealed interface Decision {
    data class Speak(
        val reason: String = "ok",
    ) : Decision

    data class Skip(
        val reason: String,
    ) : Decision

    data class Defer(
        val reason: String,
    ) : Decision
}

interface RulesEngine {
    fun decide(
        e: EventEntity,
        state: DeviceState,
        cfg: RulesConfig,
    ): Decision
}

data class DeviceState(
    val interactive: Boolean?,
    val audioActive: Boolean?,
    val tz: TimeZone,
)

class RulesEngineImpl : RulesEngine {
    private val counters = mutableMapOf<String, MutableList<Long>>()

    override fun decide(
        e: EventEntity,
        state: DeviceState,
        cfg: RulesConfig,
    ): Decision {
        if (e.eventType !in cfg.enabledTypes && cfg.enabledTypes.none { e.eventType.startsWith(it) }) {
            return Decision.Skip("type_disabled")
        }
        if (e.appPkg != null && e.appPkg in cfg.disabledApps) {
            return Decision.Skip("app_disabled")
        }
        cfg.quietHours?.let { q ->
            val local = e.tsStart.toLocalDateTime(state.tz).time
            val inQuiet = if (q.start <= q.end) (local >= q.start && local < q.end) else (local >= q.start || local < q.end)
            if (inQuiet) return Decision.Skip("quiet_hours")
        }
        if (cfg.speakWhenScreenOffOnly && state.interactive == true) {
            return Decision.Skip("screen_on")
        }
        val now = System.currentTimeMillis()
        for (rl in cfg.rateLimits) {
            if (e.eventType.startsWith(rl.keyPrefix)) {
                val key = rl.keyPrefix
                val list = counters.getOrPut(key) { mutableListOf() }
                val windowMs = rl.perSeconds * 1000L
                list.removeAll { now - it > windowMs }
                if (list.size >= rl.maxEvents) return Decision.Skip("rate_limited:$key")
                list += now
            }
        }
        return Decision.Speak()
    }
}
