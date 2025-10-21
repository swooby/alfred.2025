package com.swooby.alfred.sources.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooPlatformUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Lifecycle-aware entry point for observing device-level system signals (screen, power, boot).
 *
 * The module exposes a hot [SharedFlow] of strongly typed [SystemEvent] instances. Consumers can
 * collect the flow from foreground services (e.g. [com.swooby.alfred.pipeline.PipelineService]) or
 * UI layers without worrying about broadcast receiver management or memory leaks.
 */
class SystemEventIngestion(
    context: Context,
    private val externalScope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    companion object {
        private val TAG = FooLog.TAG(SystemEventIngestion::class.java)
        private const val STARTUP_TIMEOUT_MS = 5_000L
    }

    private val appContext = context.applicationContext

    private val eventChannel = Channel<SystemEvent>(capacity = Channel.BUFFERED)
    private val sharedEvents =
        eventChannel
            .receiveAsFlow()
            .shareIn(
                externalScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STARTUP_TIMEOUT_MS),
                replay = 0,
            )

    val events: SharedFlow<SystemEvent> = sharedEvents

    private val mutex = Mutex()
    private var started = false
    private var lastPowerSnapshot: PowerSnapshot? = null
    private val telephonyManager: TelephonyManager? = appContext.getSystemService(TelephonyManager::class.java)
    private val callStateInternal = MutableStateFlow(CallStatus.UNKNOWN)
    val callState: StateFlow<CallStatus> = callStateInternal.asStateFlow()
    private var callCallbackRegistered = false
    private var lastCallStatus: CallStatus = CallStatus.UNKNOWN
    private val callStateCallback =
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChanged(state, "telephony")
            }
        }

    private val displayReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                handleDisplayIntent(intent)
            }
        }

    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                handlePowerIntent(intent)
            }
        }

    private val shutdownReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                handleShutdownIntent(intent)
            }
        }

    private fun hasCallStatePermission(): Boolean = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun mapCallState(state: Int): CallStatus =
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> CallStatus.IDLE
            TelephonyManager.CALL_STATE_RINGING -> CallStatus.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallStatus.ACTIVE
            else -> CallStatus.UNKNOWN
        }

    @SuppressLint("MissingPermission")
    private fun resolveCallStateSnapshot(): CallStatus {
        val tm = telephonyManager ?: return CallStatus.UNKNOWN
        if (!hasCallStatePermission()) return CallStatus.UNKNOWN
        return mapCallState(tm.callStateForSubscription)
    }

    init {
        publishCallState(resolveCallStateSnapshot(), "init", emitWhenUnchanged = true)
    }

    private fun publishCallState(
        status: CallStatus,
        source: String,
        emitWhenUnchanged: Boolean = false,
    ) {
        val previous = lastCallStatus
        val changed = status != previous
        lastCallStatus = status
        callStateInternal.value = status
        if (!changed && !emitWhenUnchanged) return

        FooLog.v(TAG, "publishCallState: status=${status.name}, previous=${previous.name}, source=$source")
        if (status == CallStatus.UNKNOWN) {
            return
        }
        offer(
            CallStateEvent(
                status = status,
                timestamp = clock.now(),
                source = source,
            ),
        )
    }

    fun currentCallStatus(): CallStatus = callStateInternal.value

    private fun handleCallStateChanged(
        state: Int,
        source: String,
    ) {
        val status = mapCallState(state)
        externalScope.launch {
            mutex.withLock {
                publishCallState(status, "$source:${status.name.lowercase()}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateCallStateListenerLocked(reason: String) {
        val tm = telephonyManager
        if (tm == null) {
            FooLog.v(TAG, "updateCallStateListenerLocked: telephonyManager unavailable; reason=$reason")
            publishCallState(CallStatus.UNKNOWN, "$reason:no_telephony", emitWhenUnchanged = true)
            return
        }

        if (!hasCallStatePermission()) {
            FooLog.i(TAG, "updateCallStateListenerLocked: READ_PHONE_STATE missing; reason=$reason")
            if (callCallbackRegistered) {
                runCatching { tm.unregisterTelephonyCallback(callStateCallback) }
                    .onFailure { FooLog.w(TAG, "updateCallStateListenerLocked: unregister while revoking permission", it) }
                callCallbackRegistered = false
            }
            publishCallState(CallStatus.UNKNOWN, "$reason:permission_missing", emitWhenUnchanged = true)
            return
        }

        var stateBefore: CallStatus? = null

        if (!callCallbackRegistered) {
            // Capture state before registration
            stateBefore = mapCallState(tm.callStateForSubscription)

            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(appContext), callStateCallback)
            callCallbackRegistered = true
            FooLog.v(TAG, "updateCallStateListenerLocked: registered call state listener; reason=$reason")
        }

        val stateAfter = mapCallState(tm.callStateForSubscription)

        // If we just registered, check for race
        if (stateBefore != null && stateBefore != stateAfter) {
            publishCallState(stateBefore, "$reason:initial_pre_register")
            publishCallState(stateAfter, "$reason:initial_post_register")
        } else {
            publishCallState(stateAfter, "$reason:initial")
        }
    }

    private fun unregisterCallStateListenerLocked(reason: String) {
        if (!callCallbackRegistered) return
        val tm = telephonyManager
        runCatching { tm?.unregisterTelephonyCallback(callStateCallback) }
            .onFailure { FooLog.w(TAG, "unregisterCallStateListenerLocked: reason=$reason", it) }
        callCallbackRegistered = false
        publishCallState(CallStatus.UNKNOWN, "$reason:unregistered", emitWhenUnchanged = true)
    }

    /**
     * Registers the underlying broadcast receivers. Safe to call multiple times.
     */
    fun start() {
        externalScope.launch {
            mutex.withLock {
                if (started) {
                    FooLog.v(TAG, "start: already started")
                    return@withLock
                }
                withContext(mainDispatcher) {
                    registerDisplayReceiver()
                    registerPowerReceiver()
                    registerShutdownReceiver()
                }
                // Populate initial charging snapshot so we can immediately emit summaries.
                withContext(mainDispatcher) {
                    fetchBatteryIntent()?.let { handlePowerIntent(it) }
                    updateCallStateListenerLocked("start")
                }
                started = true
                FooLog.v(TAG, "start: receivers registered")
            }
        }
    }

    /**
     * Unregisters any active receivers and clears cached state.
     */
    fun stop() {
        externalScope.launch {
            mutex.withLock {
                if (!started) return@withLock
                withContext(mainDispatcher) {
                    runCatching { appContext.unregisterReceiver(displayReceiver) }
                        .onFailure { FooLog.w(TAG, "stop: displayReceiver", it) }
                    runCatching { appContext.unregisterReceiver(powerReceiver) }
                        .onFailure { FooLog.w(TAG, "stop: powerReceiver", it) }
                    runCatching { appContext.unregisterReceiver(shutdownReceiver) }
                        .onFailure { FooLog.w(TAG, "stop: shutdownReceiver", it) }
                    unregisterCallStateListenerLocked("stop")
                }
                lastPowerSnapshot = null
                started = false
                FooLog.v(TAG, "stop: receivers unregistered")
            }
        }
    }

    /**
     * Re-evaluates call state monitoring. Invoke after runtime permission changes.
     */
    fun refreshCallStateObserver() {
        externalScope.launch {
            mutex.withLock {
                withContext(mainDispatcher) {
                    updateCallStateListenerLocked("refresh")
                }
            }
        }
    }

    /**
     * Allows manifest receivers (e.g. boot completed) to enqueue lifecycle events even before
     * the foreground service has started collecting the shared flow.
     */
    fun onBootCompleted(
        source: String = Intent.ACTION_BOOT_COMPLETED,
        timestamp: Instant = clock.now(),
    ) {
        FooLog.i(TAG, "onBootCompleted: timestamp=$timestamp, source=${FooString.quote(source)}")
        offer(
            DeviceEvent(
                lifecycle = DeviceLifecycle.BOOT_COMPLETED,
                timestamp = timestamp,
                source = source,
            ),
        )
    }

    private fun registerDisplayReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_USER_UNLOCKED)
            }
        appContext.registerReceiver(
            displayReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun registerPowerReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
        appContext.registerReceiver(
            powerReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun registerShutdownReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SHUTDOWN)
            }
        appContext.registerReceiver(
            shutdownReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun handleDisplayIntent(intent: Intent) {
        val action = intent.action ?: return
        val ts = clock.now()
        val event =
            when (action) {
                Intent.ACTION_SCREEN_ON -> DisplayEvent(DisplayState.SCREEN_ON, interactive = true, timestamp = ts, source = action)
                Intent.ACTION_SCREEN_OFF -> DisplayEvent(DisplayState.SCREEN_OFF, interactive = false, timestamp = ts, source = action)
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_UNLOCKED,
                -> DisplayEvent(DisplayState.UNLOCKED, interactive = true, timestamp = ts, source = action)
                else -> null
            } ?: return
        offer(event)
    }

    private fun handlePowerIntent(intent: Intent) {
        val action = intent.action
        val batteryIntent =
            when (action) {
                Intent.ACTION_BATTERY_CHANGED -> intent
                else -> fetchBatteryIntent()
            } ?: return

        val snapshot = PowerSnapshot.fromIntent(batteryIntent) ?: return
        val previous = lastPowerSnapshot
        lastPowerSnapshot = snapshot
        val ts = clock.now()

        if (previous == null) {
            if (snapshot.plugged) {
                offer(
                    PowerConnectedEvent(
                        plugType = snapshot.plugType,
                        status = snapshot.status,
                        batteryPercent = snapshot.batteryPercent,
                        timestamp = ts,
                        source = action ?: Intent.ACTION_BATTERY_CHANGED,
                    ),
                )
            }
            offer(
                PowerStatusEvent(
                    status = snapshot.status,
                    plugType = snapshot.plugType,
                    batteryPercent = snapshot.batteryPercent,
                    timestamp = ts,
                    source = action ?: Intent.ACTION_BATTERY_CHANGED,
                ),
            )
            return
        }

        if (!previous.plugged && snapshot.plugged) {
            offer(
                PowerConnectedEvent(
                    plugType = snapshot.plugType,
                    status = snapshot.status,
                    batteryPercent = snapshot.batteryPercent,
                    timestamp = ts,
                    source = action ?: Intent.ACTION_POWER_CONNECTED,
                ),
            )
        } else if (previous.plugged && !snapshot.plugged) {
            offer(
                PowerDisconnectedEvent(
                    previousPlugType = previous.plugType,
                    batteryPercent = snapshot.batteryPercent,
                    timestamp = ts,
                    source = action ?: Intent.ACTION_POWER_DISCONNECTED,
                ),
            )
        } else if (snapshot.plugged && snapshot.plugType != previous.plugType) {
            // Treat plug swaps as a fresh connection so downstream summaries stay accurate.
            offer(
                PowerConnectedEvent(
                    plugType = snapshot.plugType,
                    status = snapshot.status,
                    batteryPercent = snapshot.batteryPercent,
                    timestamp = ts,
                    source = action ?: Intent.ACTION_BATTERY_CHANGED,
                ),
            )
        }

        if (snapshot.status != previous.status) {
            offer(
                PowerStatusEvent(
                    status = snapshot.status,
                    plugType = snapshot.plugType,
                    batteryPercent = snapshot.batteryPercent,
                    timestamp = ts,
                    source = action ?: Intent.ACTION_BATTERY_CHANGED,
                ),
            )
        }
    }

    private fun handleShutdownIntent(intent: Intent) {
        FooLog.i(TAG, "handleShutdownIntent: intent=${FooPlatformUtils.toString(intent)}")
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_SHUTDOWN,
            -> {
                val event =
                    DeviceEvent(
                        lifecycle = DeviceLifecycle.SHUTDOWN,
                        timestamp = clock.now(),
                        source = action,
                    )
                offer(event)
            }
        }
    }

    private fun offer(event: SystemEvent) {
        val ok = eventChannel.trySend(event).isSuccess
        if (!ok) {
            FooLog.w(TAG, "offer: dropping event=$event (channel full)")
        }
    }

    private fun fetchBatteryIntent(): Intent? =
        appContext.registerReceiver(
            // receiver =
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )

    private data class PowerSnapshot(
        val plugged: Boolean,
        val plugType: PlugType,
        val status: ChargingStatus,
        val batteryPercent: Int?,
    ) {
        companion object {
            fun fromIntent(intent: Intent): PowerSnapshot? {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else null
                val pluggedFlags = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val statusRaw = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val plugType = parsePlugType(pluggedFlags)
                val status = parseStatus(statusRaw)
                val plugged = pluggedFlags != 0
                return PowerSnapshot(
                    plugged = plugged,
                    plugType = if (plugged) plugType else PlugType.NONE,
                    status = status,
                    batteryPercent = pct,
                )
            }

            private fun parsePlugType(flags: Int): PlugType =
                when {
                    flags and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PlugType.AC
                    flags and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PlugType.USB
                    flags and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PlugType.WIRELESS
                    // BATTERY_PLUGGED_DOCK (deprecated but still surfaced) and others land here.
                    flags and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> PlugType.CAR
                    else -> PlugType.OTHER
                }

            private fun parseStatus(raw: Int): ChargingStatus =
                when (raw) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
                    BatteryManager.BATTERY_STATUS_FULL -> ChargingStatus.FULL
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingStatus.DISCHARGING
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingStatus.NOT_CHARGING
                    else -> ChargingStatus.UNKNOWN
                }
        }
    }
}
