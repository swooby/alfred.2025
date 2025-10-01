package com.swooby.alfred2017.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import com.swooby.alfred2017.AlfredApp
import com.swooby.alfred2017.core.ingest.RawEvent
import com.swooby.alfred2017.data.EventEntity
import com.swooby.alfred2017.util.Ulids
import kotlinx.datetime.Clock

class SystemSources(private val ctx: Context, private val app: AlfredApp) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun start() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        emitDisplay(if (pm.isInteractive) "display.on" else "display.off")
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            object: ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { emitWifiIfAny("network.wifi.connect") }
                override fun onLost(network: Network) { emitWifiIfAny("network.wifi.disconnect") }
            }
        )
    }

    private fun emitDisplay(type: String) {
        val now = Clock.System.now()
        app.ingest.submit(RawEvent(
            EventEntity(
                eventId = Ulids.newUlid(),
                schemaVer = 1,
                userId = "u_local",
                deviceId = "android:device",
                eventType = type,
                eventCategory = "display",
                eventAction = if (type.endsWith("on")) "on" else "off",
                subjectEntity = "display",
                tsStart = now,
                api = "PowerManager"
            ),
            fingerprint = type, coalesceKey = "display_state"
        ))
    }

    private fun emitWifiIfAny(type: String) {
        val now = Clock.System.now()
        app.ingest.submit(RawEvent(
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
                api = "ConnectivityMgr"
            ),
            fingerprint = type + now.epochSeconds, coalesceKey = "wifi_state"
        ))
    }
}