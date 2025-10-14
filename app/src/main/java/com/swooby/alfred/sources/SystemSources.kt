package com.swooby.alfred.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.smartfoo.android.core.logging.FooLog
import com.smartfoo.android.core.platform.FooDisplayListener
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.util.Ulids
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class SystemSources(
    private val ctx: Context,
    private val app: AlfredApp,
) {
    companion object {
        private val TAG = FooLog.TAG(SystemSources::class.java)
    }

    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val ignoredInitialNetworks = Collections.newSetFromMap(ConcurrentHashMap<Network, Boolean>())
    private val wifiNetworks = Collections.newSetFromMap(ConcurrentHashMap<Network, Boolean>())

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                FooLog.v(TAG, "#NETWORK onAvailable(network=$network)")
                val capabilities = cm.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!isWifi) {
                    FooLog.v(TAG, "#NETWORK onAvailable: ignoring non-WIFI network $network")
                    return
                }
                wifiNetworks.add(network)
                if (ignoredInitialNetworks.remove(network)) {
                    FooLog.v(TAG, "#NETWORK onAvailable: ignoring initial WIFI network $network")
                    return
                }
                emitWifiIfAny(SourceEventTypes.NETWORK_WIFI_CONNECT)
            }

            override fun onLost(network: Network) {
                FooLog.v(TAG, "#NETWORK onLost(network=$network)")
                if (wifiNetworks.remove(network)) {
                    emitWifiIfAny(SourceEventTypes.NETWORK_WIFI_DISCONNECT)
                } else {
                    FooLog.v(TAG, "#NETWORK onLost: ignoring non-tracked network $network")
                }
            }
        }

    @Volatile
    private var networkCallbackRegistered: Boolean = false

    private val screenListener = FooDisplayListener(ctx)
    private val screenCallbacks =
        object : FooDisplayListener.FooDisplayListenerCallbacks {
            override fun onDisplayOff(displayId: Int) {
                emitDisplayEvent(SourceEventTypes.DISPLAY_OFF, userInteractive = false, coalesceKey = "display_state")
            }

            override fun onDisplayOn(
                displayId: Int,
                isDeviceLocked: Boolean,
            ) {
                emitDisplayEvent(SourceEventTypes.DISPLAY_ON, userInteractive = true, coalesceKey = "display_state")
            }

            override fun onDeviceUnlocked() {
                emitDisplayEvent(SourceEventTypes.DEVICE_UNLOCK, userInteractive = true, coalesceKey = "device_unlock")
            }
        }

    @Volatile
    private var screenListenerAttached: Boolean = false

    fun start() {
        FooLog.v(TAG, "+start()")
        if (!screenListenerAttached) {
            screenListener.attach(screenCallbacks)
            screenListenerAttached = true
        }

        if (!networkCallbackRegistered) {
            ignoredInitialNetworks.clear()
            wifiNetworks.clear()
            cm.activeNetwork?.let { active ->
                val capabilities = cm.getNetworkCapabilities(active)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    ignoredInitialNetworks.add(active)
                    wifiNetworks.add(active)
                    FooLog.v(TAG, "#NETWORK start: ignoring launch-active WIFI network $active")
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                networkCallback,
            )
            networkCallbackRegistered = true
        }
        FooLog.v(TAG, "-start()")
    }

    fun stop() {
        FooLog.v(TAG, "+stop()")
        if (screenListenerAttached) {
            runCatching { screenListener.detach(screenCallbacks) }
            screenListenerAttached = false
        }
        if (networkCallbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
            ignoredInitialNetworks.clear()
            wifiNetworks.clear()
        }
        FooLog.v(TAG, "-stop()")
    }

    private fun emitDisplayEvent(
        type: String,
        userInteractive: Boolean?,
        coalesceKey: String,
    ) {
        val now = Clock.System.now()
        val action =
            when (type) {
                SourceEventTypes.DISPLAY_ON -> "on"
                SourceEventTypes.DISPLAY_OFF -> "off"
                SourceEventTypes.DEVICE_UNLOCK -> "unlock"
                else -> type.substringAfterLast('.')
            }
        val (category, subject) =
            when (type) {
                SourceEventTypes.DEVICE_UNLOCK -> "device" to "device"
                else -> "display" to "display"
            }
        app.ingest.submit(
            RawEvent(
                EventEntity(
                    eventId = Ulids.newUlid(),
                    schemaVer = 1,
                    userId = "u_local",
                    deviceId = "android:device",
                    eventType = type,
                    eventCategory = category,
                    eventAction = action,
                    subjectEntity = subject,
                    tsStart = now,
                    api = "FooScreenListener",
                    userInteractive = userInteractive,
                ),
                fingerprint = type,
                coalesceKey = coalesceKey,
            ),
        )
    }

    private fun emitWifiIfAny(type: String) {
        val now = Clock.System.now()
        app.ingest.submit(
            RawEvent(
                EventEntity(
                    eventId = Ulids.newUlid(),
                    schemaVer = 1,
                    userId = "u_local",
                    deviceId = "android:device",
                    eventType = type,
                    eventCategory = "network",
                    eventAction = type.substringAfterLast('.'),
                    subjectEntity = "wifi",
                    tsStart = now,
                    api = "ConnectivityMgr",
                ),
                fingerprint = type + now.epochSeconds,
                coalesceKey = "wifi_state",
            ),
        )
    }
}
