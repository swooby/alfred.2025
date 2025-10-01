package com.swooby.alfred2017.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swooby.alfred2017.AlfredApp

@Composable
fun settingsViewModel(app: AlfredApp): SettingsViewModel {
    return viewModel(factory = object: ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(app.settings) as T
        }
    })
}