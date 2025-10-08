package com.swooby.alfred.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.swooby.alfred.AlfredApp
import com.swooby.alfred.core.ingest.RawEvent
import com.swooby.alfred.data.EventEntity
import com.swooby.alfred.util.Ulids
import com.smartfoo.android.core.platform.FooDisplayListener
import kotlin.time.Clock

class SystemSources(private val ctx: Context, private val app: AlfredApp) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            emitWifiIfAny(SourceEventTypes.NETWORK_WIFI_CONNECT)
        }

        override fun onLost(network: Network) {
            emitWifiIfAny(SourceEventTypes.NETWORK_WIFI_DISCONNECT)
        }
    }
    @Volatile
    private var networkCallbackRegistered: Boolean = false

    private val screenListener = FooDisplayListener(ctx)
    private val screenCallbacks = object : FooDisplayListener.FooDisplayListenerCallbacks {
        override fun onDisplayOff(displayId: Int) {
            emitScreenEvent(SourceEventTypes.DISPLAY_OFF, userInteractive = false, coalesceKey = "display_state")
        }

        override fun onDisplayOn(displayId: Int, isDeviceLocked: Boolean) {
            emitScreenEvent(SourceEventTypes.DISPLAY_ON, userInteractive = true, coalesceKey = "display_state")
        }

        override fun onDeviceUnlocked() {
            emitScreenEvent(SourceEventTypes.DEVICE_UNLOCK, userInteractive = true, coalesceKey = "device_unlock")
        }
    }

    @Volatile
    private var screenListenerAttached: Boolean = false

    fun start() {
        if (!screenListenerAttached) {
            screenListener.attach(screenCallbacks)
            screenListenerAttached = true
        }

        if (!networkCallbackRegistered) {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback
            )
            networkCallbackRegistered = true
        }
    }

    fun stop() {
        if (screenListenerAttached) {
            runCatching { screenListener.detach(screenCallbacks) }
            screenListenerAttached = false
        }
        if (networkCallbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
    }

    private fun emitScreenEvent(type: String, userInteractive: Boolean?, coalesceKey: String) {
        val now = Clock.System.now()
        val action = when (type) {
            SourceEventTypes.DISPLAY_ON -> "on"
            SourceEventTypes.DISPLAY_OFF -> "off"
            SourceEventTypes.DEVICE_UNLOCK -> "unlock"
            else -> type.substringAfterLast('.')
        }
        val (category, subject) = when (type) {
            SourceEventTypes.DEVICE_UNLOCK -> "device" to "device"
            else -> "display" to "display"
        }
        app.ingest.submit(RawEvent(
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
                userInteractive = userInteractive
            ),
            fingerprint = type,
            coalesceKey = coalesceKey
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
