package com.example.desl_app.data.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission

/**
 * Represents the current network mode of the device
 */
sealed class NetworkMode {
    /** Phone has hotspot enabled - we can get SSID and password */
    data class Hotspot(val ssid: String, val password: String) : NetworkMode()

    /** Phone is connected to WiFi - we can get SSID but user must enter password */
    data class WiFi(val ssid: String) : NetworkMode()

    /** No usable network detected */
    object None : NetworkMode()
}

/**
 * Helper class to detect network mode and retrieve credentials
 */
class NetworkHelper(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Detects current network mode and returns available credentials
     *
     * @param onHotspotInfo Callback for when hotspot info is retrieved (Android 8+)
     */
    fun detectNetworkMode(onResult: (NetworkMode) -> Unit) {
        // First check if hotspot is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            checkHotspotEnabled { isHotspot, ssid, password ->
                if (isHotspot && ssid != null && password != null) {
                    onResult(NetworkMode.Hotspot(ssid, password))
                } else {
                    // Not hotspot, check WiFi
                    val wifiSsid = getCurrentWifiSsid()
                    if (wifiSsid != null) {
                        onResult(NetworkMode.WiFi(wifiSsid))
                    } else {
                        onResult(NetworkMode.None)
                    }
                }
            }
        } else {
            // Below Android 8, just check WiFi
            val wifiSsid = getCurrentWifiSsid()
            if (wifiSsid != null) {
                onResult(NetworkMode.WiFi(wifiSsid))
            } else {
                onResult(NetworkMode.None)
            }
        }
    }

    /**
     * Gets the SSID of the currently connected WiFi network
     * Returns null if not connected to WiFi
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @Suppress("DEPRECATION")
    fun getCurrentWifiSsid(): String? {
        // Check if connected to WiFi
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        val wifiInfo = wifiManager.connectionInfo ?: return null
        var ssid = wifiInfo.ssid ?: return null

        // Remove quotes if present
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }

        // "<unknown ssid>" is returned when location permission is missing
        if (ssid == "<unknown ssid>" || ssid.isBlank()) {
            return null
        }

        return ssid
    }

    /**
     * Checks if hotspot is enabled and retrieves its configuration
     * Only available on Android 8.0 (API 26) and above
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkHotspotEnabled(callback: (isHotspot: Boolean, ssid: String?, password: String?) -> Unit) {
        try {
            // Use reflection to access tethering state as there's no public API
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            val isEnabled = method.invoke(wifiManager) as? Boolean ?: false

            if (isEnabled) {
                // Try to get hotspot configuration
                getHotspotConfig { ssid, password ->
                    callback(true, ssid, password)
                }
            } else {
                callback(false, null, null)
            }
        } catch (e: Exception) {
            // Reflection failed, assume not hotspot
            callback(false, null, null)
        }
    }

    /**
     * Gets hotspot SSID and password using reflection
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getHotspotConfig(callback: (ssid: String?, password: String?) -> Unit) {
        try {
            // For Android 8-9, use WifiConfiguration
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val method = wifiManager.javaClass.getDeclaredMethod("getWifiApConfiguration")
                val config = method.invoke(wifiManager)

                if (config != null) {
                    val ssidField = config.javaClass.getDeclaredField("SSID")
                    val passwordField = config.javaClass.getDeclaredField("preSharedKey")

                    val ssid = ssidField.get(config) as? String
                    val password = passwordField.get(config) as? String

                    callback(ssid, password)
                    return
                }
            } else {
                // For Android 11+, use SoftApConfiguration via TetheringManager
                // This requires OVERRIDE_WIFI_CONFIG permission which normal apps don't have
                // Fall back to requiring user input
                callback(null, null)
                return
            }
        } catch (e: Exception) {
            // Reflection failed
        }
        callback(null, null)
    }

    /**
     * Simple check if WiFi is enabled
     */
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled
}