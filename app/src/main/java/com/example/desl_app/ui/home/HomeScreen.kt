package com.example.desl_app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.desl_app.R
import com.example.desl_app.ble.BleProvisioningManager
import com.example.desl_app.data.network.PiConnectionState
import com.example.desl_app.ui.settings.SettingsViewModel
import com.example.desl_app.ui.theme.DisplayBlue
import com.example.desl_app.ui.theme.GreyBorder
import com.example.desl_app.ui.theme.GreyDisabled
import com.example.desl_app.ui.theme.DisplayButton
import com.example.desl_app.ui.theme.DisplayContainer
import com.example.desl_app.ui.theme.DisplayHeader
import com.example.desl_app.ui.theme.DisplayTextField
import com.example.desl_app.ui.theme.DisplayTheme
import com.example.desl_app.data.network.NetworkHelper
import com.example.desl_app.data.network.NetworkMode
import com.example.desl_app.ui.theme.DisplayBox
import com.example.desl_app.ui.theme.DisplayDialog
import com.example.desl_app.ui.theme.DisplaySecondaryText
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import org.json.JSONObject

// Color options for text on the Pi display
private data class TextColorOption(
    val name: String,
    val color: Color,
    val hexValue: String
)

private val textColorOptions = listOf(
    TextColorOption("White", Color.White, "#FFFFFF"),
    TextColorOption("Black", Color.Black, "#000000"),
    TextColorOption("Green", Color(0xFF00FF00), "#00FF00"),
    TextColorOption("Yellow", Color(0xFFFFFF00), "#FFFF00"),
    TextColorOption("Red", Color(0xFFFF0000), "#FF0000"),
)

@Composable
fun MainActivity(
    modifier: Modifier = Modifier.background(Color.White),
    settingsVm: SettingsViewModel? = null,
    homeVm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    DisplayTheme {
        // Get developer mode and BLE state
        val developerMode by settingsVm?.developerMode?.collectAsState() ?: remember { mutableStateOf(false) }
        val bleEnabled by settingsVm?.bleEnabled?.collectAsState() ?: remember { mutableStateOf(true) }

        // Get status from ViewModel
        val statusLog by homeVm.statusLog.collectAsState()
        val isSending by homeVm.isSending.collectAsState()

        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // ===== target endpoint (HTTP) =====
        val targetIp by PiConnectionState.targetIp.collectAsState(initial = null)
        val isConnected = targetIp != null
        var isConnecting by remember { mutableStateOf(false) }
        var targetPort by remember { mutableIntStateOf(5000) }
        var discoveryStatus by remember { mutableStateOf("Not connected (BLE not started)") }

        // ===== Remember last known IP for ping-first logic =====
        var lastKnownIp by remember { mutableStateOf<String?>(null) }

        // ===== Manual IP state (outside BLE block) =====
        var manualIp by remember { mutableStateOf("") }

        // ===== BLE provisioning UI state =====
        var ssid by remember { mutableStateOf("") }
        var psk by remember { mutableStateOf("") }
        var bleStatus by remember { mutableStateOf("IDLE") }
        var bleResult by remember { mutableStateOf("") }
        var bleLog by remember { mutableStateOf("") }
        var bleReady by remember { mutableStateOf(false) }

        // ===== Network detection state =====
        var networkMode by remember { mutableStateOf<NetworkMode>(NetworkMode.None) }
        var showPasswordDialog by remember { mutableStateOf(false) }
        var pendingWifiSsid by remember { mutableStateOf("") }
        val networkHelper = remember { NetworkHelper(context) }

        // ===== Text color state =====
        var selectedColorIndex by remember { mutableIntStateOf(0) }

        // ===== Brightness state =====
        var brightnessValue by remember { mutableFloatStateOf(100f) }

        // Scroll state for logs
        val logScrollState = rememberScrollState()

        fun appendBleLog(line: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date())
            bleLog = (bleLog + "\n[$timestamp] $line").trim()
        }

        // Auto-scroll to bottom when new log is added
        LaunchedEffect(bleLog) {
            logScrollState.animateScrollTo(logScrollState.maxValue)
        }

        // Track last known IP when connection changes
        LaunchedEffect(targetIp) {
            if (targetIp != null) {
                lastKnownIp = targetIp
            }
        }

        // ===== Permissions (SDK 35) =====
        val neededPermissions =
            remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            }

        var permissionsGranted by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            permissionsGranted = result.values.all { it }
            appendBleLog("Permissions granted: $permissionsGranted")
        }

        fun hasAllPermissions(): Boolean =
            neededPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }

        LaunchedEffect(Unit) {
            permissionsGranted = hasAllPermissions()
            appendBleLog("Permissions already granted: $permissionsGranted")

            if (!permissionsGranted) {
                awaitFrame()
                permissionLauncher.launch(neededPermissions)
            }
        }

        // ===== BLE manager instance =====
        val bleMgr =
            remember {
                BleProvisioningManager(
                    context = context,
                    targetName = "MBDisplay-Setup",
                    onLog = { appendBleLog(it) },
                    onStatus = {
                        bleStatus = it
                        appendBleLog("STATUS <- $it")

                        if (it.contains("timeout", true) || it.contains("failed", true) || it.contains("disconnected", true) || it.contains("error", true)) {
                            scope.launch { isConnecting = false }
                            discoveryStatus = "Connection failed"
                        }
                    },
                    onResult = { json ->
                        bleResult = json
                        appendBleLog("RESULT <- $json")

                        runCatching {
                            val obj = JSONObject(json)
                            val ip = obj.optString("ip", "")
                            val port = obj.optInt("port", 5000)
                            appendBleLog("Parsed: ip=$ip port=$port")
                            if (ip.isNotBlank()) {
                                PiConnectionState.setConnection(ip, targetPort)
                                discoveryStatus = "Connected: $ip:$targetPort"
                                scope.launch { isConnecting = false }
                                appendBleLog("SUCCESS: targetIp set to $ip")
                            } else {
                                scope.launch { isConnecting = false }
                                appendBleLog("WARNING: ip is blank!")
                            }
                        }.onFailure { e ->
                            scope.launch { isConnecting = false }
                            appendBleLog("RESULT parse error: ${e.message}")
                        }
                    },
                    onReady = {
                        appendBleLog("onReady callback triggered")
                        bleReady = true
                    }
                )
            }

        // LaunchedEffect reagiert auf bleReady, sends wifi credential when ble is connected
        LaunchedEffect(bleReady) {
            if (bleReady && ssid.isNotBlank() && psk.isNotBlank()) {
                appendBleLog("BLE ready - sending WiFi credentials (payload hidden)")
                bleMgr.sendWifiCredentials(ssid, psk)
                bleReady = false
            }
        }

        // Helper: start BLE provisioning flow with network detection
        fun startBleProvisioning() {
            networkHelper.detectNetworkMode { mode ->
                networkMode = mode
                appendBleLog("Network mode detected: $mode")

                when (mode) {
                    is NetworkMode.Hotspot -> {
                        ssid = mode.ssid
                        psk = mode.password
                        appendBleLog("Hotspot detected")
                        bleMgr.startScan(timeoutMs = 12_000L)
                        discoveryStatus = "Scanning for Pi via BLE..."
                    }
                    is NetworkMode.WiFi -> {
                        pendingWifiSsid = mode.ssid
                        ssid = mode.ssid
                        showPasswordDialog = true
                        appendBleLog("WiFi detected - requesting password")
                    }
                    NetworkMode.None -> {
                        appendBleLog("No network detected. Enable WiFi or Hotspot.")
                        discoveryStatus = "No network - enable WiFi or Hotspot"
                        isConnecting = false
                    }
                }
            }
        }

        // Helper: disconnect everything
        fun performDisconnect() {
            appendBleLog("Disconnect pressed")
            bleMgr.disconnect()
            PiConnectionState.clearConnection()
            isConnecting = false
            bleStatus = "IDLE"
            bleResult = ""
            bleReady = false
            discoveryStatus = "Disconnected"
            appendBleLog("Disconnected from Pi")
        }

        // ===== existing message sending state =====
        var message by remember { mutableStateOf("") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                DisplayHeader(text = "Display Door Sill",
                    leftLogoRes = R.drawable.display_mark,
                    rightLogoRes = R.drawable.display_mark,
                    logoSize = 80.dp)
            },
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ====== Developer Mode: Debug Panel ======
                if (developerMode) {
                    DisplayBox {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DisplaySecondaryText("🔧 Developer Debug Panel")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Target IP:", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    targetIp ?: "NOT SET",
                                    color = if (targetIp != null) Color(0xFF4CAF50) else Color.Red,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Port:", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    "$targetPort",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Last Known IP:", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    lastKnownIp ?: "none",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("BLE Status:", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    bleStatus,
                                    color = when (bleStatus) {
                                        "CONNECTED" -> Color(0xFF4CAF50)
                                        "IDLE" -> Color.Gray
                                        else -> Color(0xFFFF9800)
                                    },
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Discovery:", color = Color.Gray, fontSize = 14.sp)
                                Text(
                                    discoveryStatus,
                                    color = Color.Black,
                                    fontSize = 12.sp
                                )
                            }

                            if (bleResult.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("BLE Result:", color = Color.Gray, fontSize = 14.sp)
                                    Text(
                                        bleResult,
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            DisplaySecondaryText("Manual IP (Testing)")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DisplayTextField(
                                    value = manualIp,
                                    onValueChange = { manualIp = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = "cmd: hostname -I"
                                )
                                DisplayButton(
                                    text = "Set IP",
                                    onClick = {
                                        if (manualIp.isNotBlank()) {
                                            PiConnectionState.setConnection(manualIp, 5000)
                                            discoveryStatus = "Manual IP: $manualIp:5000"
                                            appendBleLog("Manual IP set: $manualIp")
                                        }
                                    },
                                    modifier = Modifier.weight(0.5f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            DisplayButton(
                                text = "Clear Logs",
                                onClick = { bleLog = "" },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    DisplayBox {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "📋 BLE Log Console",
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 150.dp, max = 300.dp)
                                    .background(Color(0xFFF5F5F5))
                                    .padding(8.dp)
                                    .verticalScroll(logScrollState)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = if (bleLog.isBlank()) "No logs yet..." else bleLog,
                                    color = if (bleLog.isBlank()) Color.Gray else Color.Black,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ====== BLE Provisioning ======
                if (bleEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (developerMode) {
                            DisplaySecondaryText("BLE Provisioning")

                            val networkStatusText = when (val mode = networkMode) {
                                is NetworkMode.Hotspot -> "Hotspot detected: ${mode.ssid}"
                                is NetworkMode.WiFi -> "WiFi detected: ${mode.ssid}"
                                NetworkMode.None -> "No network detected"
                            }
                            DisplaySecondaryText("Network: $networkStatusText")
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Scan & Connect
                        DisplayButton(
                            onClick = {
                                if (!permissionsGranted) return@DisplayButton

                                isConnecting = true
                                appendBleLog("Scan & Connect pressed")

                                discoveryStatus = "Checking direct connection..."
                                homeVm.tryDirectConnect(
                                    lastKnownIp = lastKnownIp,
                                    onReachable = { ip ->
                                        appendBleLog("Pi reachable at $ip — skipping BLE")
                                        discoveryStatus = "Connected: $ip:5000 (direct)"
                                        isConnecting = false
                                    },
                                    onUnreachable = {
                                        appendBleLog("Pi not reachable — falling back to BLE")
                                        startBleProvisioning()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            text = when {
                                isConnected -> "Connected"
                                isConnecting -> "Connecting..."
                                !permissionsGranted -> "No Permissions"
                                else -> "Scan & Connect"
                            },
                            isLoading = isConnecting,
                            enabled = !isConnected && !isConnecting && permissionsGranted
                        )

                        // Disconnect
                        DisplayButton(
                            onClick = { performDisconnect() },
                            modifier = Modifier.fillMaxWidth(),
                            text = "Disconnect",
                            enabled = isConnected || isConnecting
                        )
                    }

                    if (showPasswordDialog) {
                        DisplayDialog(
                            title = "WiFi Passwort eingeben",
                            onDismiss = {
                                showPasswordDialog = false
                                isConnecting = false
                            },
                            onConfirm = {
                                showPasswordDialog = false
                                if (psk.isNotBlank()) {
                                    appendBleLog("Password entered, starting scan...")
                                    isConnecting = true
                                    bleMgr.startScan(timeoutMs = 12_000L)
                                    discoveryStatus = "Scanning for Pi via BLE..."
                                } else {
                                    isConnecting = false
                                }
                            },
                            confirmText = "Connect"
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Network: $pendingWifiSsid", color = Color.Black)
                                DisplayTextField(
                                    value = psk,
                                    onValueChange = { psk = it },
                                    placeholder = "Password",
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                )
                            }
                        }
                    }
                }

                // ====== Message Sending Box ======
                DisplayBox {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))

                        DisplaySecondaryText("Type Message")

                        DisplayTextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Type Message"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== Text Color Circles =====
                        DisplaySecondaryText("Text Color")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            textColorOptions.forEachIndexed { index, option ->
                                val isSelected = selectedColorIndex == index
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(option.color, CircleShape)
                                        .border(
                                            width = if (isSelected) 4.dp else 2.dp,
                                            color = if (isSelected) DisplayBlue else GreyBorder,
                                            shape = CircleShape
                                        )
                                        .clickable(enabled = isConnected) {
                                            selectedColorIndex = index
                                            homeVm.sendColor(option.hexValue)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isConnected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Color.Black.copy(alpha = 0.4f),
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        if (!isConnected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            DisplaySecondaryText(
                                "Connect to Pi to change color",
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== Brightness Slider =====
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DisplaySecondaryText("Brightness")
                            DisplaySecondaryText(
                                "${brightnessValue.toInt()}%",
                                color = if (isConnected) Color.Black else Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = brightnessValue,
                            onValueChange = { brightnessValue = it },
                            onValueChangeFinished = {
                                if (isConnected) {
                                    homeVm.sendBrightness(brightnessValue.toInt())
                                }
                            },
                            valueRange = 0f..100f,
                            steps = 0,
                            enabled = isConnected,
                            colors = SliderDefaults.colors(
                                thumbColor = DisplayBlue,
                                activeTrackColor = DisplayBlue,
                                inactiveTrackColor = GreyBorder,
                                disabledThumbColor = GreyDisabled,
                                disabledActiveTrackColor = GreyDisabled,
                                disabledInactiveTrackColor = GreyBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        DisplayButton(
                            text = if (isSending) "Sending..." else "Send Text To Pi",
                            onClick = {
                                appendBleLog("Sending text: '$message' to ${targetIp ?: "NO IP"}")
                                homeVm.sendText(message)
                            },
                            enabled = isConnected && !isSending,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (developerMode) {
                            DisplayContainer(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "HTTP Status: $statusLog",
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
