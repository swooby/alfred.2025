package com.swooby.alfred.core.profile

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class AudioProfileController(
    context: Context,
    private val audioManager: AudioManager,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val profileStore: AudioProfileStore,
    private val permissionChecker: AudioProfilePermissionChecker,
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val tag = FooLog.TAG(AudioProfileController::class.java)

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(externalScope.coroutineContext + supervisorJob)

    private val profileStrings = AudioProfileStrings(appContext)

    private val baseProfiles: List<AudioProfile> = listOf(
        AudioProfile.Disabled(
            displayName = profileStrings.disabledName,
            metadata = ProfileMetadata(description = profileStrings.disabledDescription)
        ),
        AudioProfile.AlwaysOn(
            displayName = profileStrings.alwaysOnName,
            metadata = ProfileMetadata(description = profileStrings.alwaysOnDescription)
        ),
        AudioProfile.WiredOnly(
            displayName = profileStrings.wiredName,
            metadata = ProfileMetadata(description = profileStrings.wiredDescription)
        ),
        AudioProfile.BluetoothAny(
            displayName = profileStrings.bluetoothAnyName,
            metadata = ProfileMetadata(description = profileStrings.bluetoothAnyDescription)
        ),
        AudioProfile.AnyHeadset(
            displayName = profileStrings.anyHeadsetName,
            metadata = ProfileMetadata(description = profileStrings.anyHeadsetDescription)
        )
    )
    private val defaultProfileId: AudioProfileId =
        (baseProfiles.firstOrNull { it is AudioProfile.AlwaysOn } ?: baseProfiles.first()).id

    private val dynamicProfiles = MutableStateFlow<Map<AudioProfileId, AudioProfile>>(emptyMap())
    private val profilesFlow: StateFlow<List<AudioProfile>> =
        dynamicProfiles
            .map { dynamic -> (baseProfiles + dynamic.values).sortedBy { it.sortKey } }
            .stateIn(scope, SharingStarted.Eagerly, baseProfiles.sortedBy { it.sortKey })

    private val selectedProfileId = MutableStateFlow<AudioProfileId?>(defaultProfileId)
    private val connectedHeadsets = MutableStateFlow(ConnectedHeadsets())
    private val bluetoothPermissionState = MutableStateFlow(BluetoothPermissionState.Unknown)

    private val profileSnapshotsFlow: StateFlow<List<AudioProfileSnapshot>> =
        combine(profilesFlow, selectedProfileId, connectedHeadsets) { profiles, selected, headsets ->
            profiles
                .filter { profile -> shouldDisplayProfile(profile, selected, headsets) }
                .map { profile ->
                    val activeDevices = matchingDevices(profile, headsets)
                    val isActive = isProfileActive(profile, activeDevices)
                    val isSelected = profile.id == selected
                    AudioProfileSnapshot(
                        profile = profile,
                        isSelected = isSelected,
                        isActive = isActive,
                        isEffective = isSelected && isActive,
                        activeDevices = activeDevices
                    )
                }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val effectiveProfileFlow: StateFlow<EffectiveAudioProfile?> =
        profileSnapshotsFlow
            .map { snapshots ->
                snapshots.firstOrNull { it.isEffective }?.let { snapshot ->
                    EffectiveAudioProfile(snapshot.profile, snapshot.activeDevices)
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val events = MutableSharedFlow<HeadsetEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )

    private var bluetoothWatchJob: Job? = null
    private var wiredWatchJob: Job? = null

    val profiles: StateFlow<List<AudioProfileSnapshot>> = profileSnapshotsFlow
    val effectiveProfile: StateFlow<EffectiveAudioProfile?> = effectiveProfileFlow
    val uiState: StateFlow<AudioProfileUiState> =
        combine(
            profileSnapshotsFlow,
            effectiveProfileFlow,
            connectedHeadsets,
            bluetoothPermissionState
        ) { profiles, effective, headsets, permission ->
            val missingPermissions = when (permission) {
                BluetoothPermissionState.Missing -> setOf(Manifest.permission.BLUETOOTH_CONNECT)
                else -> emptySet()
            }
            AudioProfileUiState(
                profiles = profiles,
                selectedProfileId = profiles.firstOrNull { it.isSelected }?.id,
                effectiveProfile = effective,
                connectedHeadsets = headsets,
                missingPermissions = missingPermissions
            )
        }.stateIn(scope, SharingStarted.Eagerly, AudioProfileUiState())

    val bluetoothPermission: StateFlow<BluetoothPermissionState> = bluetoothPermissionState
    val headsetEvents: SharedFlow<HeadsetEvent> = events

    init {
        observeStoredSelection()
        startWiredWatcher()
        refreshBluetoothPermission()
    }

    suspend fun selectProfile(profileId: AudioProfileId?) {
        val resolved = profileId ?: defaultProfileId
        selectedProfileId.value = resolved
        pruneDisconnectedProfiles(resolved)
        withContext(ioDispatcher) {
            val profile = findProfileById(resolved)
            profileStore.persistSelectedProfile(profile?.toStoredProfile() ?: StoredAudioProfile(resolved))
        }
    }

    fun refreshBluetoothPermission() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            FooLog.i(tag, "Bluetooth adapter unavailable on device.")
            bluetoothPermissionState.value = BluetoothPermissionState.Unavailable
            stopBluetoothWatcher()
            return
        }

        val granted = runCatching { permissionChecker.hasBluetoothConnectPermission() }
            .onFailure { failure ->
                FooLog.w(tag, "Failed to query BLUETOOTH_CONNECT permission", failure)
            }
            .getOrDefault(false)

        if (!granted) {
            if (bluetoothPermissionState.value != BluetoothPermissionState.Missing) {
                FooLog.i(tag, "BLUETOOTH_CONNECT permission missing; emitting state.")
                events.tryEmit(
                    HeadsetEvent.PermissionMissing(
                        timestamp = nowInstant(),
                        permission = Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            bluetoothPermissionState.value = BluetoothPermissionState.Missing
            clearBluetoothDevices()
            stopBluetoothWatcher()
            return
        }

        bluetoothPermissionState.value = BluetoothPermissionState.Granted
        startBluetoothWatcher(adapter)
    }

    fun shutdown() {
        stopBluetoothWatcher()
        stopWiredWatcher()
        supervisorJob.cancel()
    }

    private fun observeStoredSelection() {
        scope.launch(ioDispatcher) {
            profileStore.selectedProfile()
                .distinctUntilChanged()
                .collect { stored ->
                    stored?.let { ensureStoredProfile(it) }
                    val resolved = stored?.id ?: defaultProfileId
                    FooLog.d(tag, "Restoring stored audio profile selection id=${resolved.value}")
                    selectedProfileId.value = resolved
                    pruneDisconnectedProfiles(resolved)
                }
        }
    }

    private fun startWiredWatcher() {
        if (wiredWatchJob?.isActive == true) return

        wiredWatchJob = scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                observeModernWiredDevices().collect { change -> handleDeviceChange(change) }
            } else {
                observeLegacyWiredDevices().collect { change -> handleDeviceChange(change) }
            }
        }
    }

    private fun stopWiredWatcher() {
        wiredWatchJob?.cancel()
        wiredWatchJob = null
    }

    private fun startBluetoothWatcher(adapter: BluetoothAdapter) {
        if (bluetoothWatchJob?.isActive == true) return
        bluetoothWatchJob = scope.launch {
            observeBluetoothDevices(adapter).collect { change ->
                handleDeviceChange(change)
            }
        }
    }

    private fun stopBluetoothWatcher() {
        bluetoothWatchJob?.cancel()
        bluetoothWatchJob = null
    }

    private fun clearBluetoothDevices() {
        connectedHeadsets.update { current ->
            if (current.bluetooth.isEmpty()) current else current.copy(bluetooth = emptySet())
        }
        pruneDisconnectedProfiles(selectedProfileId.value)
    }

    private fun handleDeviceChange(change: DeviceChange) {
        when (change) {
            is DeviceChange.Connected -> handleConnected(change.device)
            is DeviceChange.Disconnected -> handleDisconnected(change.device)
        }
    }

    private fun handleConnected(device: HeadsetDevice) {
        when (device) {
            is HeadsetDevice.Wired -> connectedHeadsets.update { current ->
                val updated = current.wired
                    .filterNot { it.id == device.id }
                    .toMutableSet()
                updated.add(device)
                current.copy(wired = updated)
            }

            is HeadsetDevice.Bluetooth -> connectedHeadsets.update { current ->
                val updated = current.bluetooth
                    .filterNot { it.id == device.id }
                    .toMutableSet()
                updated.add(device)
                current.copy(bluetooth = updated)
            }
        }

        if (device is HeadsetDevice.Bluetooth) {
            ensureBluetoothProfile(device)
        }

        FooLog.d(tag, "Device connected: ${device.safeDisplayName} (${device.id})")
        events.tryEmit(
            HeadsetEvent.Connected(
                timestamp = nowInstant(),
                device = device
            )
        )
    }

    private fun handleDisconnected(device: HeadsetDevice) {
        when (device) {
            is HeadsetDevice.Wired -> connectedHeadsets.update { current ->
                val updated = current.wired.filterNot { it.id == device.id }.toSet()
                current.copy(wired = updated)
            }

            is HeadsetDevice.Bluetooth -> connectedHeadsets.update { current ->
                val updated = current.bluetooth.filterNot { it.id == device.id }.toSet()
                current.copy(bluetooth = updated)
            }
        }

        FooLog.d(tag, "Device disconnected: ${device.safeDisplayName} (${device.id})")
        events.tryEmit(
            HeadsetEvent.Disconnected(
                timestamp = nowInstant(),
                device = device
            )
        )

        pruneDisconnectedProfiles(selectedProfileId.value)
    }

    private fun ensureBluetoothProfile(device: HeadsetDevice.Bluetooth) {
        val token = device.id.value.substringAfter("bt:")
        dynamicProfiles.update { current ->
            if (current.values.any { it is AudioProfile.BluetoothDevice && it.metadata.deviceToken == token }) {
                return@update current
            }
            val profile = AudioProfile.BluetoothDevice(
                displayName = device.safeDisplayName,
                deviceToken = token,
                address = device.address,
                metadata = ProfileMetadata(
                    deviceToken = token,
                    deviceAddress = device.address,
                    description = profileStrings.bluetoothDeviceDescription
                )
            )
            FooLog.d(tag, "Registering Bluetooth profile for device=${device.safeDisplayName} id=${profile.id}")
            current + (profile.id to profile)
        }
    }

    private fun matchingDevices(
        profile: AudioProfile,
        connected: ConnectedHeadsets
    ): Set<HeadsetDevice> = when (profile) {
        is AudioProfile.Disabled -> emptySet()
        is AudioProfile.AlwaysOn -> connected.all
        is AudioProfile.WiredOnly -> connected.wired
        is AudioProfile.BluetoothAny -> connected.bluetooth
        is AudioProfile.AnyHeadset -> connected.all
        is AudioProfile.BluetoothDevice -> connected.bluetooth.filter { device ->
            val matchesAddress = run {
                val profileAddress = normalizeAddress(profile.metadata.deviceAddress)
                val deviceAddress = normalizeAddress(device.address)
                profileAddress != null && profileAddress == deviceAddress
            }
            val matchesToken = profile.metadata.deviceToken?.let { token ->
                deviceMatchesToken(token, device)
            } ?: false
            matchesAddress || matchesToken
        }.toSet()
    }

    private fun isProfileActive(profile: AudioProfile, activeDevices: Set<HeadsetDevice>): Boolean {
        return when (profile) {
            is AudioProfile.Disabled -> false
            is AudioProfile.AlwaysOn -> true
            else -> activeDevices.isNotEmpty()
        }
    }

    private fun shouldDisplayProfile(
        profile: AudioProfile,
        selectedId: AudioProfileId?,
        headsets: ConnectedHeadsets
    ): Boolean = when (profile) {
        is AudioProfile.BluetoothDevice -> {
            val isSelected = profile.id == selectedId
            val token = profile.metadata.deviceToken
            val isConnected = token != null && headsets.bluetooth.any { deviceMatchesToken(token, it) }
            isSelected || isConnected
        }

        else -> true
    }

    private fun findProfileById(id: AudioProfileId): AudioProfile? {
        return profilesFlow.value.firstOrNull { it.id == id }
            ?: dynamicProfiles.value[id]
            ?: baseProfiles.firstOrNull { it.id == id }
    }

    private fun AudioProfile.toStoredProfile(): StoredAudioProfile = StoredAudioProfile(
        id = id,
        displayName = displayName,
        category = category,
        deviceAddress = (this as? AudioProfile.BluetoothDevice)?.metadata?.deviceAddress
    )

    private fun ensureStoredProfile(stored: StoredAudioProfile) {
        val token = stored.id.bluetoothTokenOrNull() ?: return
        dynamicProfiles.update { current ->
            val existing = current[stored.id]
            if (existing is AudioProfile.BluetoothDevice) {
                if (stored.displayName != null && existing.displayName != stored.displayName) {
                    current + (stored.id to existing.copy(displayName = stored.displayName))
                } else {
                    current
                }
            } else {
                val profile = AudioProfile.BluetoothDevice(
                    displayName = stored.displayName ?: profileStrings.bluetoothAnyName,
                    deviceToken = token,
                    address = stored.deviceAddress,
                    metadata = ProfileMetadata(
                        deviceToken = token,
                        deviceAddress = stored.deviceAddress,
                        description = profileStrings.bluetoothDeviceDescription
                    )
                )
                current + (profile.id to profile)
            }
        }
    }

    private fun pruneDisconnectedProfiles(selectedId: AudioProfileId?) {
        val headsets = connectedHeadsets.value
        dynamicProfiles.update { current ->
            current.filterValues { profile ->
                if (profile !is AudioProfile.BluetoothDevice) {
                    true
                } else {
                    val isSelected = profile.id == selectedId
                    val token = profile.metadata.deviceToken
                    val isConnected = token != null && headsets.bluetooth.any { deviceMatchesToken(token, it) }
                    isSelected || isConnected
                }
            }
        }
    }

    private fun AudioProfileId.bluetoothTokenOrNull(): String? {
        val prefix = "profile.headset.bluetooth."
        if (!value.startsWith(prefix)) return null
        val token = value.removePrefix(prefix)
        if (token.isBlank() || token == "any") return null
        return token
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun observeModernWiredDevices() = kotlinx.coroutines.flow.callbackFlow<DeviceChange> {
        FooLog.v(tag, "Start observing modern wired audio devices.")
        val handler = Handler(Looper.getMainLooper())
        val currentDevices = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)
            .mapNotNull { it.toWiredHeadsetOrNull() }
            .distinctBy { it.id }
        currentDevices.forEach { trySend(DeviceChange.Connected(it)) }

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                addedDevices.mapNotNull { it.toWiredHeadsetOrNull() }
                    .forEach { device -> trySend(DeviceChange.Connected(device)) }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                removedDevices.mapNotNull { it.toWiredHeadsetOrNull() }
                    .forEach { device -> trySend(DeviceChange.Disconnected(device)) }
            }
        }

        audioManager.registerAudioDeviceCallback(callback, handler)

        awaitClose {
            FooLog.v(tag, "Stop observing modern wired audio devices.")
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun observeLegacyWiredDevices() = kotlinx.coroutines.flow.callbackFlow<DeviceChange> {
        FooLog.v(tag, "Start observing legacy wired audio devices.")

        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra("state", -1)
                val microphone = intent.getIntExtra("microphone", 0) == 1
                val name = intent.getStringExtra("name").orEmpty()
                val device = HeadsetDevice.Wired(
                    id = HeadsetId("legacy_wired"),
                    displayName = name,
                    supportsMicrophone = microphone,
                    supportsOutput = true,
                    rawName = name
                )
                when (state) {
                    0 -> trySend(DeviceChange.Disconnected(device))
                    1 -> trySend(DeviceChange.Connected(device))
                }
            }
        }

        appContext.registerReceiver(receiver, filter)
        awaitClose {
            FooLog.v(tag, "Stop observing legacy wired audio devices.")
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    @Suppress("MissingPermission")
    private fun observeBluetoothDevices(adapter: BluetoothAdapter) =
        kotlinx.coroutines.flow.callbackFlow<DeviceChange> {
            FooLog.v(tag, "Start observing Bluetooth audio devices.")
            val receivers = BluetoothReceiverSet()
            val serviceConnections = BluetoothServiceConnections()

            @Suppress("MissingPermission")
            fun BluetoothDevice.toHeadset(): HeadsetDevice.Bluetooth {
                val normalizedAddress = normalizeAddress(address)
                val token = normalizedAddress ?: "anon_${hashCode().toUInt().toString(16)}"
                return HeadsetDevice.Bluetooth(
                    id = HeadsetId("bt:$token"),
                    displayName = name.orEmpty(),
                    supportsMicrophone = true,
                    supportsOutput = true,
                    rawName = name,
                    address = address,
                    isLeAudio = type == BluetoothDevice.DEVICE_TYPE_LE
                )
            }

            fun dispatchConnected(device: BluetoothDevice) {
                trySend(DeviceChange.Connected(device.toHeadset()))
            }

            fun dispatchDisconnected(device: BluetoothDevice) {
                trySend(DeviceChange.Disconnected(device.toHeadset()))
            }

            fun Intent.connectionState(): Int? = when {
                hasExtra(BluetoothProfile.EXTRA_STATE) ->
                    getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)

                hasExtra(BluetoothHeadset.EXTRA_STATE) ->
                    getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)

                else -> null
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action ?: return
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            device?.let { dispatchConnected(it) }
                        }

                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state = intent.connectionState()
                            when (state) {
                                BluetoothProfile.STATE_CONNECTED -> device?.let { dispatchConnected(it) }
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_DISCONNECTING -> device?.let { dispatchDisconnected(it) }
                                BluetoothProfile.STATE_CONNECTING,
                                null -> Unit
                                else -> {
                                    FooLog.v(tag, "Unhandled Bluetooth state change action=${FooString.quote(action)} state=$state")
                                }
                            }
                        }

                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            device?.let { dispatchDisconnected(it) }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }

            runCatching { appContext.registerReceiver(receiver, filter) }
                .onSuccess { receivers.add(receiver) }
                .onFailure { error ->
                    FooLog.w(tag, "Failed to register Bluetooth receiver", error)
                    events.tryEmit(
                        HeadsetEvent.Error(
                            timestamp = nowInstant(),
                            message = "Bluetooth receiver registration failed",
                            throwableMessage = error.message
                        )
                    )
                }

            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val connectedDevices = runCatching { proxy.connectedDevices }
                        .onFailure { error ->
                            FooLog.w(tag, "Bluetooth profile query failed profile=$profile", error)
                            events.tryEmit(
                                HeadsetEvent.Error(
                                    timestamp = nowInstant(),
                                    message = "Bluetooth profile query failed ($profile)",
                                    throwableMessage = error.message
                                )
                            )
                        }
                        .getOrElse { emptyList() }

                    connectedDevices.forEach { dispatchConnected(it) }
                    serviceConnections.add(profile, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {
                    serviceConnections.remove(profile)
                }
            }

            val neededProfiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
            neededProfiles.forEach { profileId ->
                runCatching { adapter.getProfileProxy(appContext, listener, profileId) }
                    .onFailure { error ->
                        FooLog.w(tag, "Failed to acquire Bluetooth profile proxy id=$profileId", error)
                        events.tryEmit(
                            HeadsetEvent.Error(
                                timestamp = nowInstant(),
                                message = "Bluetooth profile proxy unavailable ($profileId)",
                                throwableMessage = error.message
                            )
                        )
                    }
            }

            awaitClose {
                FooLog.v(tag, "Stop observing Bluetooth audio devices.")
                receivers.unregisterAll(appContext)
                serviceConnections.closeAll(adapter)
            }
        }

    private fun AudioDeviceInfo.toWiredHeadsetOrNull(): HeadsetDevice.Wired? {
        val isRelevantType = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_DOCK -> true
            else -> false
        }
        if (!isRelevantType) return null

        val idValue = address.takeIf { it.isNotBlank() } ?: id.toString()
        val displayName = productName?.toString().orEmpty()

        return HeadsetDevice.Wired(
            id = HeadsetId("wired:$idValue"),
            displayName = displayName,
            supportsMicrophone = isSource,
            supportsOutput = isSink,
            rawName = displayName,
            portAddress = address.takeIf { it.isNotBlank() }
        )
    }

    private fun normalizeAddress(address: String?): String? =
        address?.lowercase(Locale.US)?.replace(":", "")

    private fun deviceMatchesToken(token: String, device: HeadsetDevice.Bluetooth): Boolean {
        val normalizedToken = token.lowercase(Locale.US)
        val deviceToken = device.id.value.substringAfter("bt:").lowercase(Locale.US)
        return normalizedToken == deviceToken
    }

    private fun nowInstant(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    private data class BluetoothReceiverSet(
        private val receivers: MutableList<BroadcastReceiver> = mutableListOf()
    ) {
        fun add(receiver: BroadcastReceiver) {
            receivers.add(receiver)
        }

        fun unregisterAll(context: Context) {
            receivers.forEach { receiver ->
                runCatching { context.unregisterReceiver(receiver) }
            }
            receivers.clear()
        }
    }

    private data class BluetoothServiceConnections(
        private val connections: MutableMap<Int, BluetoothProfile> = mutableMapOf()
    ) {
        fun add(profile: Int, proxy: BluetoothProfile) {
            connections[profile] = proxy
        }

        fun remove(profile: Int) {
            connections.remove(profile)
        }

        fun closeAll(adapter: BluetoothAdapter) {
            connections.forEach { (profile, proxy) ->
                runCatching { adapter.closeProfileProxy(profile, proxy) }
                    .onFailure { error ->
                        FooLog.w(FooLog.TAG(AudioProfileController::class.java), "Failed to close profile proxy id=$profile", error)
                    }
            }
            connections.clear()
        }
    }

    private sealed interface DeviceChange {
        data class Connected(val device: HeadsetDevice) : DeviceChange
        data class Disconnected(val device: HeadsetDevice) : DeviceChange
    }
}
