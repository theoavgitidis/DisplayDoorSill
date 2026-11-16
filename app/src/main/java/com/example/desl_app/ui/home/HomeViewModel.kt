package com.example.desl_app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.desl_app.data.network.PiConnectionState
import com.example.desl_app.data.repository.ApiResult
import com.example.desl_app.data.repository.PiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PiRepository()

    private val _statusLog = MutableStateFlow("System Ready")
    val statusLog: StateFlow<String> = _statusLog.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    // Debounce job for brightness slider
    private var brightnessJob: Job? = null

    /**
     * Tries to reach the Pi directly via HTTP before starting BLE.
     * If the Pi already has an IP from a previous session or the same network,
     * we can skip BLE provisioning entirely.
     *
     * @param lastKnownIp Optional IP from a previous connection
     * @param onReachable Called with the reachable IP if found — skip BLE
     * @param onUnreachable Called if Pi is not reachable — proceed with BLE
     */
    fun tryDirectConnect(
        lastKnownIp: String?,
        onReachable: (String) -> Unit,
        onUnreachable: () -> Unit
    ) {
        viewModelScope.launch {
            // Try last known IP first
            if (!lastKnownIp.isNullOrBlank()) {
                _statusLog.value = "Pinging $lastKnownIp..."
                if (repository.pingPi(lastKnownIp)) {
                    _statusLog.value = "Pi found at $lastKnownIp"
                    PiConnectionState.setConnection(lastKnownIp, 5000)
                    onReachable(lastKnownIp)
                    return@launch
                }
            }

            // Not reachable — fall back to BLE
            _statusLog.value = "Pi not reachable, starting BLE..."
            onUnreachable()
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch {
            _isSending.value = true
            _statusLog.value = "Sending..."

            when (val result = repository.sendText(text)) {
                is ApiResult.Success -> {
                    _statusLog.value = "Successfully Sent"
                }
                is ApiResult.Error -> {
                    _statusLog.value = "Error: ${result.message}"
                }
                is ApiResult.NotConnected -> {
                    _statusLog.value = "No Pi target yet, use BLE provisioning first"
                }
            }

            _isSending.value = false
        }
    }

    fun sendColor(hexColor: String) {
        viewModelScope.launch {
            when (val result = repository.sendColor(hexColor)) {
                is ApiResult.Success -> {
                    _statusLog.value = "Color set: $hexColor"
                }
                is ApiResult.Error -> {
                    _statusLog.value = "Color error: ${result.message}"
                }
                is ApiResult.NotConnected -> {
                    _statusLog.value = "No Pi connection"
                }
            }
        }
    }

    /**
     * Sends brightness with debounce so we don't flood the Pi
     * while the user drags the slider.
     */
    fun sendBrightness(brightness: Int) {
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            delay(150) // debounce: wait 150ms after last change
            when (val result = repository.sendBrightness(brightness)) {
                is ApiResult.Success -> {
                    _statusLog.value = "Brightness: $brightness%"
                }
                is ApiResult.Error -> {
                    _statusLog.value = "Brightness error: ${result.message}"
                }
                is ApiResult.NotConnected -> {
                    _statusLog.value = "No Pi connection"
                }
            }
        }
    }

    fun showIpAndPort(targetIp: String?, targetPort: Int) {
        _statusLog.value = if (targetIp != null) {
            "IP: $targetIp | Port: $targetPort"
        } else {
            "No Pi connected yet"
        }
    }

    fun clearStatus() {
        _statusLog.value = "System Ready"
    }
}