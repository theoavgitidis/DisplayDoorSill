/*
* This class scans for the service UUID, connects and discovers
* services (GATT Discovery), enables notify (CCCD), writes the
* WiFi JSON and receives status and results from the Pi.
*/

package com.example.desl_app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.collections.ArrayDeque

// Constructor
class BleProvisioningManager(
    private val context: Context,                       // reference on an android context
    private val targetName: String = "MBDisplay-Setup", // Identification of the pi, used for Scan
    private val onLog: (String) -> Unit,                //
    private val onStatus: (String) -> Unit,             // Function References
    private val onResult: (String) -> Unit,            //
    private val onReady: () -> Unit = {}                // Callback if ble ready
) {
    // ===== UUIDs (match Pi) =====
    // fromString() returns binary
    private val SERVICE_UUID   = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")    // BLE Gatt Service
    private val WIFI_CRED_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")    // Write Characteristic
    private val STATUS_UUID    = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")    // Notify Characteristic
    private val RESULT_UUID    = UUID.fromString("12345678-1234-5678-1234-56789abcdef3")    // Notify Characteristic
    private val CCCD_UUID      = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")    // Standard BLE Characteristic for Notify, BLE-Specification

    private fun log(msg: String) = onLog(msg)   // just for the readability

    // Handler: message queue access, Looper.getMainLooper(): UI Thread Event Loop
    private val mainHandler = Handler(Looper.getMainLooper())

    //get System service return androids service-registry, BluetoothManager is the entry point in the android bluetooth stack
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // as: casting

    // BluetoothAdapter represents the Bluetooth Module of the Phone, no Null Check needed
    private val adapter: BluetoothAdapter = bluetoothManager.adapter
    // Getter: scanner doesn't have a value, called when used as BLE Stack can change
    private val scanner: BluetoothLeScanner? get() = adapter.bluetoothLeScanner

    // prevents double startScan()
    private var scanning = false
    // ScanCallback is an object with virtual methods
    private var scanCb: ScanCallback? = null

    // represents an active BLE connection, contains Connection State, Services, Chars, Descriptor State
    // must be freed with close()
    private var gatt: BluetoothGatt? = null

    // references that point to objects, that were created from the android stack
    private var wifiChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var resultChar: BluetoothGattCharacteristic? = null

    // ===== CCCD write queue (serialize descriptor writes) =====
    // Array of Double ended queues (Deque),
    private val cccdQueue: ArrayDeque<Pair<BluetoothGattDescriptor, ByteArray>> = ArrayDeque()
    private var cccdWriteInFlight = false // Flag to prevent parallel writes

    // gatt.refresh() cant get recognized from the compiler, as google hasn't made it public
    // Called after connectGatt, after disconnect/reconnect, or when onServicesDiscovered fails
    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            refreshMethod.invoke(gatt) as Boolean
        } catch (e: Exception) {
            log("Cache refresh failed: ${e.message}")
            false
        }
    }

    // ========= Public API =========
    @SuppressLint("MissingPermission") // ignore permission errors, tho if permissions missing: SecurityException
    fun startScan(timeoutMs: Long = 12_000L) {
        val s = scanner // property with getter, returns either scanner object or Null
        log("DBG adapterEnabled=${adapter.isEnabled} scannerNull=${(s == null)} scanning=$scanning")

        // prevents double startScan()
        if (scanning) {
            log("DBG already scanning")
            return
        }

        // No Bluetooth no BLE :'(
        if (!adapter.isEnabled) {
            log("DBG Bluetooth is OFF -> no scan possible")
            return
        }

        // null if BT just on, permissions missing, stack bug, no BLE supported (Emulator)
        if (s == null) {
            log("DBG BluetoothLeScanner is null (device/permissions/BT stack)")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // more power consumption
            .build() // builds an immutable ScanSettings Object

        // Best practice: filter by advertised service UUID (more reliable than name)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        // exists in heap, android can call methods on it later
        val cb = object : ScanCallback() {
            // called from Bluetooth Controller, asynchronous, event driven
            override fun onScanResult(callbackType: Int, result: ScanResult) { // Scan Result is an Object that contains a snapshot of the advertising event
                val dev = result.device ?: return // result.device has the type BluetoothDevice, represents a deleted BLE-device
                val nameFromRecord = result.scanRecord?.deviceName // advertised name from pi
                val nameFromDevice = dev.name // can be null or old
                // MAC (Media Access Control) Address
                val addr = dev.address // specific physical address of the BLE-Hardware
                val rssi = result.rssi // signal strength in dBm, the bigger the negative number, the more far away is the device

                log("SEEN: addr=$addr rssi=$rssi nameRec=$nameFromRecord nameDev=$nameFromDevice")

                // if name from record == 0 -> name from device, if null -> ""
                val name = nameFromRecord ?: nameFromDevice ?: ""
                // binary comparison: serviceUuids == SERVICE_UUID?
                val hasService: Boolean = result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true

                // if hasService -> surely the pi, if name -> maybe the pi (fallback)
                if (hasService || name == targetName) {
                    log("MATCH addr=$addr (name=$name hasService=$hasService) -> connect")
                    stopScanInternal(s, this) // current ScanCallback object is "this"
                    connect(dev) // uses the BluetoothDevice handle
                }
            }

            // Android can deliver results as a batch, in SCAN_MODE_LOW_LATENCY not that often
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                log("DBG batch results: ${results.size}")
                results.forEach { r ->
                    val dev = r.device ?: return@forEach
                    log("BATCH: addr=${dev.address} rssi=${r.rssi} name=${r.scanRecord?.deviceName ?: dev.name}")
                }
            }
            // gets called if: missing permissions, BT off, too many parallel scans, HW/stack error
            override fun onScanFailed(errorCode: Int) {
                log("Scan failed: errorCode=$errorCode")
                stopScanInternal(s, this) // scanning=false, scanCb=null, stops scanner
            }
        }

        scanCb = cb // Callback must be saved, so that stopScan(cb) can later be called
        scanning = true // blocks double startScan calls

        // Timeout: stop scan if nothing found: If timeoutMs and Main Thread free stop scan
        mainHandler.postDelayed({
            if (scanning) {
                log("Scan timeout after ${timeoutMs}ms")
                stopScan()
            }
        }, timeoutMs)

        // Start scan (catch SecurityException properly)
        try {
            log("DBG calling startScan(service-filter) now...")
            s.startScan(listOf(filter), settings, cb)
            log("DBG startScan() called")
        } catch (se: SecurityException) {
            log("DBG SecurityException: ${se.message}")
            scanning = false
            scanCb = null
        } catch (e: Exception) {
            log("DBG Scan start exception: ${e::class.java.simpleName}: ${e.message}")
            scanning = false
            scanCb = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        // if no scanner or callback log and stop scan internal
        val s = scanner ?: run { log("DBG stopScan: scanner null"); return }
        val cb = scanCb ?: run { log("DBG stopScan: no callback"); return }
        stopScanInternal(s, cb)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        // Kotlin standard lib, run the block and ignore exceptions
        runCatching { stopScan() } // prevents that the function is interrupted because of an exception

        clearCccdQueue() // old enable notify actions are not wanted
        resetRefs() // chars caches delete, when reconnected new instances are created

        gatt?.disconnect() // async, Android BLE API
        gatt?.close() // end the instance! free object and handle
        gatt = null // old GATT session cannot be used again

        log("Disconnected")
    }

    @SuppressLint("MissingPermission")
    fun sendWifiCredentials(ssid: String, psk: String) {
        // if gatt == 0: not connected, disconnected or connect not finished
        val g = gatt ?: run { log("No GATT connection"); return }
        // wifiChar set onServicesDiscovered, if Services !discovered: wifiChar = null
        val c = wifiChar ?: run { log("WiFi characteristic not ready"); return }
        //ssid or psk blank check
        if (ssid.isBlank() || psk.isBlank()) {
            log("SSID or PSK are blank. WiFi Credentials were not sent")
            return
        }
        // create a JSON object (map) and convert to String
        val json = JSONObject()
            .put("ssid", ssid)
            .put("psk", psk)
            .toString()
        // BLE sends Bytes, not strings, pi decodes in utf-8
        val payload = json.toByteArray(StandardCharsets.UTF_8)

        log("Sending Wi-Fi credentials (payload hidden)")

        if (Build.VERSION.SDK_INT >= 33) {
            // Android takes the writes request and queues or disapproves
            val status = g.writeCharacteristic(
                c,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            if (status != BluetoothStatusCodes.SUCCESS) { // SUCCESS: accepted, queued
                log("writeCharacteristic enqueue failed: status=$status")
            }
            return
        }

        // Legacy fallback (<33)
        @Suppress("DEPRECATION")
        run {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = payload
            val ok = g.writeCharacteristic(c)
            if (!ok) log("legacy writeCharacteristic returned false")
        }
    }

    // ========= Internals =========

    @SuppressLint("MissingPermission")
    private fun stopScanInternal(s: BluetoothLeScanner, cb: ScanCallback) {
        if (!scanning) return
        s.stopScan(cb)
        scanning = false
        scanCb = null
        log("Scan stopped")
    }

    // connect ist async (as callbacks are async), connectGatt delivers result immediately
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        disconnect() // close previous cleanly
        log("Connecting to ${device.address} …")
        // delivers a BluetoothGatt Object, with which later
        // discoveryServices(), writeCharacteristic(), setCharacteristicNotification(), writeDescriptor()
        gatt = device.connectGatt(context,
            false, // connect immediately
            gattCallback,
            BluetoothDevice.TRANSPORT_LE) // force BLE
    }

    private fun resetRefs() {
        wifiChar = null
        statusChar = null
        resultChar = null
    }

    private fun clearCccdQueue() {
        cccdQueue.clear()
        cccdWriteInFlight = false
    }

    // ========= GATT callbacks =========

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        // App calls device.connectGatt(...), Android BT Stack starts connect, if state changes Android calls onConnectionStateChange
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log("Connection state: gattStatus=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected, discovering services…")
                // can add up to 500ms delay
                val refreshed = refreshDeviceCache(g)  // refresh caches to add an extra security layer
                // Android BLE Operations
                log("Cache refreshed : $refreshed")
                g.discoverServices() // Requests Services, Chars, Descriptors from Pi
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected from device")
            }
        }

        @SuppressLint("MissingPermission")
        // onConnectionStateChange calls g.discoverServices() and when this is finished Android calls onServicesDiscovered
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            log("Services discovered: gattStatus=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val svc = g.getService(SERVICE_UUID)
            // svc == null means: wrong device, device advs but service not, discovery did not function, uuid not ok
            if (svc == null) {
                log("Service not found: $SERVICE_UUID")
                return
            }

            wifiChar = svc.getCharacteristic(WIFI_CRED_UUID)
            statusChar = svc.getCharacteristic(STATUS_UUID)
            resultChar = svc.getCharacteristic(RESULT_UUID)

            if (wifiChar == null) log("WiFi char missing: $WIFI_CRED_UUID")
            if (statusChar == null) log("Status char missing: $STATUS_UUID")
            if (resultChar == null) log("Result char missing: $RESULT_UUID")

            // Queue CCCD writes (serialized), GATT Operations shall not be started at the same time
            if (statusChar != null) enqueueNotifyEnable(g, statusChar!!)
            if (resultChar != null) enqueueNotifyEnable(g, resultChar!!)

            // Start CCCD sequence
            writeNextCccdIfIdle(g)

            log("BLE ready (CCCD writes queued)")
        }

        // called as completion event for g.writeCharacteristic(...)
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid == WIFI_CRED_UUID) {
                log(if (status == BluetoothGatt.GATT_SUCCESS) "WRITE complete "
                else "WRITE failed status=$status"
                )
            } else {
                log("WRITE uuid=${c.uuid} status=$status")
            }
        }

        // completion event for g.writeDescriptor(...)
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            log(if (status == BluetoothGatt.GATT_SUCCESS) "CCCD write complete ${d.uuid}" else "CCCD write failed status=$status")
            cccdWriteInFlight = false

            // Check if queue is empty -> all CCCD writes done -> ready!
            if (cccdQueue.isEmpty()) {
                log("All CCCD writes complete - BLE fully ready")
                mainHandler.post { onReady() }
            } else {
                writeNextCccdIfIdle(g)
            }
        }

        // Android 13+
        // Called if server sends a notify for a char, which is enabled for notify
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            log("onCharacteristicChanged: uuid=${c.uuid} len=${value.size} hex=${value.take(20).joinToString("") { "%02x".format(it) }}")
            handleNotify(c.uuid, value)
        }
        
        // Pre Android 13 - needed for older devices
        @Deprecated("Deprecated in API 33", ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)"))
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = c.value ?: return
            log("onCharacteristicChanged (legacy): uuid=${c.uuid} len=${value.size}")
            handleNotify(c.uuid, value)
        }
    }

    // ========= CCCD queue logic =========

    @SuppressLint("MissingPermission")
    private fun enqueueNotifyEnable(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // Enable Notifications for Callbacks
        val localOk = g.setCharacteristicNotification(characteristic, true)
        log("Local notify enable ${characteristic.uuid} => $localOk")

        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            log("CCCD missing for ${characteristic.uuid}")
            return
        }
        // enable notification so that android can receive them from pi for this char
        cccdQueue.addLast(cccd to BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    @SuppressLint("MissingPermission")
    private fun writeNextCccdIfIdle(g: BluetoothGatt) {
        if (cccdWriteInFlight) return
        val next = cccdQueue.removeFirstOrNull() ?: return

        cccdWriteInFlight = true
        val (desc, value) = next

        if (Build.VERSION.SDK_INT >= 33) {
            val status = g.writeDescriptor(desc, value)
            if (status != BluetoothStatusCodes.SUCCESS) {
                log("writeDescriptor enqueue failed: status=$status")
                cccdWriteInFlight = false
                writeNextCccdIfIdle(g)
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                desc.value = value
                val ok = g.writeDescriptor(desc)
                if (!ok) {
                    log("legacy writeDescriptor returned false")
                    cccdWriteInFlight = false
                    writeNextCccdIfIdle(g)
                }
            }
        }
    }

    // ========= Notify handling =========

    private fun handleNotify(uuid: UUID, value: ByteArray) {
        val textRaw = value.toString(StandardCharsets.UTF_8)
        val text = textRaw.replace("\u0000", "").trim()

        mainHandler.post {
            when (uuid) {
                STATUS_UUID -> onStatus(text)
                RESULT_UUID -> onResult(text)
                else -> log("Notify $uuid -> $text")
            }
        }
    }
}
