package com.example.desl_app.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {

    private val _developerMode = MutableStateFlow(false)
    val developerMode: StateFlow<Boolean> = _developerMode.asStateFlow()

    private val _bleEnabled = MutableStateFlow(true)  // BLE on by default
    val bleEnabled: StateFlow<Boolean> = _bleEnabled.asStateFlow()

    fun setDeveloperMode(enabled: Boolean) {
        _developerMode.value = enabled
        // When developer mode is turned off, re-enable BLE
        if (!enabled) {
            _bleEnabled.value = true
        }
    }

    fun setBleEnabled(enabled: Boolean) {
        _bleEnabled.value = enabled
    }
}