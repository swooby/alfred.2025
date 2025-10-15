package com.swooby.alfred.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.smartfoo.android.core.logging.FooLog
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.util.Ulids
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

class SystemSources(
    private val app: AlfredApp,
) {
    companion object {
        private val TAG = FooLog.TAG(SystemSources::class.java)
    }

    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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

    fun start() {
        FooLog.v(TAG, "+start()")
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
        if (networkCallbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
            ignoredInitialNetworks.clear()
            wifiNetworks.clear()
        }
        FooLog.v(TAG, "-stop()")
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
