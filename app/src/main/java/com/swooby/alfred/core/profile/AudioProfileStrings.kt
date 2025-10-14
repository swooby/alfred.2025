package com.swooby.alfred.core.profile

import android.content.Context
import com.swooby.alfred.R

class AudioProfileStrings(
    private val context: Context,
) {
    val disabledName: String
        get() = context.getString(R.string.audio_profile_disabled_name)

    val disabledDescription: String
        get() = context.getString(R.string.audio_profile_disabled_description)

    val alwaysOnName: String
        get() = context.getString(R.string.audio_profile_always_on_name)

    val alwaysOnDescription: String
        get() = context.getString(R.string.audio_profile_always_on_description)

    val wiredName: String
        get() = context.getString(R.string.audio_profile_wired_name)

    val wiredDescription: String
        get() = context.getString(R.string.audio_profile_wired_description)

    val bluetoothAnyName: String
        get() = context.getString(R.string.audio_profile_bluetooth_any_name)

    val bluetoothAnyDescription: String
        get() = context.getString(R.string.audio_profile_bluetooth_any_description)

    val bluetoothDeviceDescription: String
        get() = context.getString(R.string.audio_profile_bluetooth_device_description)

    val anyHeadsetName: String
        get() = context.getString(R.string.audio_profile_any_headset_name)

    val anyHeadsetDescription: String
        get() = context.getString(R.string.audio_profile_any_headset_description)
}
