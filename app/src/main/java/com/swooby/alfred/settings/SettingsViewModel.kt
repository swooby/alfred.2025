package com.swooby.alfred.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swooby.alfred.core.rules.RulesConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository): ViewModel() {
    val rules = repo.rulesConfigFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RulesConfig())
    fun updateSpeakWhenScreenOff(checked: Boolean) = viewModelScope.launch { repo.setSpeakWhenScreenOffOnly(checked) }
    fun updateQuietHours(start: String?, end: String?) = viewModelScope.launch { repo.setQuietHours(start, end) }
    fun updateDisabledAppsCsv(csv: String) = viewModelScope.launch { repo.setDisabledAppsCsv(csv) }
    fun updateEnabledTypesCsv(csv: String) = viewModelScope.launch { repo.setEnabledTypesCsv(csv) }
}