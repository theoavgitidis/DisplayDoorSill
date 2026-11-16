// File: data/network/PiConnectionState.kt
package com.example.desl_app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to share Pi connection state across the app
 */
object PiConnectionState {
    private val _targetIp = MutableStateFlow<String?>(null)
    val targetIp: StateFlow<String?> = _targetIp.asStateFlow()

    private val _targetPort = MutableStateFlow(5000)
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    fun setConnection(ip: String, port: Int = 5000) {
        _targetIp.value = ip
        _targetPort.value = port
    }

    fun clearConnection() {
        _targetIp.value = null
        _targetPort.value = 5000
    }

    fun getUploadUrl(): String? {
        val ip = _targetIp.value ?: return null
        return "http://$ip:${_targetPort.value}/upload"
    }

    fun getTextUrl(): String? {
        val ip = _targetIp.value ?: return null
        return "http://$ip:${_targetPort.value}/send-text"
    }

    fun isConnected(): Boolean = _targetIp.value != null
}