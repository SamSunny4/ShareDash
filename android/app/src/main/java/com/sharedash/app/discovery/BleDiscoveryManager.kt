package com.sharedash.app.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.sharedash.app.model.DiscoveredPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BleDiscoveryManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = try {
        bluetoothManager?.adapter
    } catch (_: Exception) {
        null
    }
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private val _discoveredBlePeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredBlePeers: StateFlow<List<DiscoveredPeer>> = _discoveredBlePeers.asStateFlow()

    private val peerMap = mutableMapOf<String, DiscoveredPeer>()

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val device = scanResult.device
                val deviceName = try {
                    device.name ?: scanResult.scanRecord?.deviceName ?: "Nearby PC"
                } catch (_: SecurityException) {
                    scanResult.scanRecord?.deviceName ?: "Nearby PC"
                } catch (_: Exception) {
                    "Nearby PC"
                }
                val deviceAddress = device.address
                
                val serviceData = scanResult.scanRecord?.getServiceData(ParcelUuid(SHAREDASH_SERVICE_UUID))
                var ip = ""
                var port = 54321
                if (serviceData != null && serviceData.size >= 6) {
                    val p1 = serviceData[0].toInt() and 0xFF
                    val p2 = serviceData[1].toInt() and 0xFF
                    val p3 = serviceData[2].toInt() and 0xFF
                    val p4 = serviceData[3].toInt() and 0xFF
                    ip = "$p1.$p2.$p3.$p4"
                    port = ((serviceData[4].toInt() and 0xFF) shl 8) or (serviceData[5].toInt() and 0xFF)
                }

                val peer = DiscoveredPeer(
                    deviceId = "ble-$deviceAddress",
                    friendlyName = deviceName,
                    osName = if (deviceName.contains("PC") || deviceName.contains("DESKTOP")) "Windows 11" else "Android",
                    ipAddress = ip,
                    port = port,
                    supportedBridges = listOf("BLE Discovery", "Wi-Fi Direct"),
                    rssi = scanResult.rssi,
                    isBleDetected = true,
                    lastSeenTimestamp = System.currentTimeMillis()
                )

                peerMap[deviceAddress] = peer
                _discoveredBlePeers.value = peerMap.values.toList()
                Log.d(TAG, "BLE Discovered Peer: $deviceName ($deviceAddress) IP: $ip")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed: $errorCode")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE Advertising started successfully for ShareDash")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasScanPermission()) {
            Log.w(TAG, "BLUETOOTH_SCAN permission not granted, skipping BLE discovery")
            return
        }

        try {
            if (bluetoothAdapter?.isEnabled != true) {
                Log.w(TAG, "Bluetooth is disabled, skipping BLE discovery")
                return
            }

            bleScanner = bluetoothAdapter.bluetoothLeScanner
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SHAREDASH_SERVICE_UUID))
                .build()
                
            val staleScope = CoroutineScope(Dispatchers.Default)
            staleCheckJob?.cancel()
            staleCheckJob = staleScope.launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val keysToRemove = peerMap.filterValues { now - it.lastSeenTimestamp > 30000 }.keys
                    if (keysToRemove.isNotEmpty()) {
                        keysToRemove.forEach { peerMap.remove(it) }
                        _discoveredBlePeers.value = peerMap.values.toList()
                    }
                    delay(5000)
                }
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Stop any existing scan before starting a new one to prevent SCAN_FAILED_ALREADY_STARTED
            try { bleScanner?.stopScan(scanCallback) } catch (_: Exception) {}
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            startAdvertising()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in startDiscovery: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (!hasAdvertisePermission()) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted, skipping BLE advertising")
            return
        }

        try {
            if (bluetoothAdapter?.isEnabled != true) {
                return
            }
            bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (bleAdvertiser == null) {
                Log.w(TAG, "BLE Advertiser not available on this device")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            var myIp = "0.0.0.0"
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (!iface.isLoopback && iface.isUp) {
                        for (addr in iface.inetAddresses) {
                            val host = addr.hostAddress ?: ""
                            if (host.contains(".") && !host.startsWith("127.")) {
                                myIp = host
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val ipParts = myIp.split(".").map { it.toIntOrNull() ?: 0 }
            val serviceDataBytes = buildWifiCapsPayload(context, ipParts, 54321)

            // Keep advertiseData compact: 16-bit UUID + 12-byte payload
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SHAREDASH_SERVICE_UUID))
                .addServiceData(ParcelUuid(SHAREDASH_SERVICE_UUID), serviceDataBytes)
                .build()

            val scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            try { bleAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
            bleAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            Log.i(TAG, "BLE Advertising initiated with IP $myIp")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting BLE advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising: ${e.message}")
        }
    }

    private var staleCheckJob: Job? = null

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        staleCheckJob?.cancel()
        try {
            bleScanner?.stopScan(scanCallback)
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BleDiscoveryManager"
        val SHAREDASH_SERVICE_UUID: UUID = UUID.fromString("00005344-0000-1000-8000-00805F9B34FB") // 'SD' 0x5344

        fun buildWifiCapsPayload(context: Context, ipParts: List<Int>, port: Int): ByteArray {
            val p1 = if (ipParts.size == 4) ipParts[0] else 0
            val p2 = if (ipParts.size == 4) ipParts[1] else 0
            val p3 = if (ipParts.size == 4) ipParts[2] else 0
            val p4 = if (ipParts.size == 4) ipParts[3] else 0

            var std = 6 // Default to Wi-Fi 6 on modern Android
            var maxFreq = 60 // 6.0 GHz (Wi-Fi 6E/7 ready)
            var maxBw = 160
            var maxPhy = 1200
            var bandsMask = 0x07 // 2.4G (bit 0) | 5G (bit 1) | 6G (bit 2)

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val wifiInfo = wifiManager?.connectionInfo

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && wifiInfo != null) {
                when (wifiInfo.wifiStandard) {
                    6 -> { std = 6; maxPhy = 1200; maxBw = 160 }
                    7 -> { std = 6; maxFreq = 60; maxPhy = 2402; maxBw = 160 }
                    8 -> { std = 7; maxFreq = 60; maxPhy = 4804; maxBw = 320 }
                    else -> {}
                }
            }

            return byteArrayOf(
                p1.toByte(), p2.toByte(), p3.toByte(), p4.toByte(),
                (port shr 8).toByte(), (port and 0xFF).toByte(),
                std.toByte(),
                maxFreq.toByte(),
                maxBw.toByte(),
                ((maxPhy shr 8) and 0xFF).toByte(),
                (maxPhy and 0xFF).toByte(),
                bandsMask.toByte()
            )
        }
    }
}
