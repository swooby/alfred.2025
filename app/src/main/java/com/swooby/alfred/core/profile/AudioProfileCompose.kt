package com.swooby.alfred.core.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

@Composable
fun rememberAudioProfileUiState(controller: AudioProfileController): AudioProfileUiState {
    val uiState by controller.uiState.collectAsState()
    LaunchedEffect(Unit) {
        controller.refreshBluetoothPermission()
    }
    return uiState
}
