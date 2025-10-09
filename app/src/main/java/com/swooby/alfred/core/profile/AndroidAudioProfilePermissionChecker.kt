package com.swooby.alfred.core.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class AndroidAudioProfilePermissionChecker(
    private val context: Context
) : AudioProfilePermissionChecker {

    override fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        return granted == PackageManager.PERMISSION_GRANTED
    }
}
