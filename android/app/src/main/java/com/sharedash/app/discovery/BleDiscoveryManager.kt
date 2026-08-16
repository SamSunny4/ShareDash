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
import java.util.UUID

class BleDiscoveryManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null

    private val _discoveredBlePeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredBlePeers: StateFlow<List<DiscoveredPeer>> = _discoveredBlePeers.asStateFlow()

    private val peerMap = mutableMapOf<String, DiscoveredPeer>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val device = scanResult.device
                val deviceName = device.name ?: scanResult.scanRecord?.deviceName ?: "Nearby PC"
                val deviceAddress = device.address

                val peer = DiscoveredPeer(
                    deviceId = "ble-$deviceAddress",
                    friendlyName = deviceName,
                    osName = if (deviceName.contains("PC") || deviceName.contains("DESKTOP")) "Windows 11" else "Android",
                    ipAddress = "", // IP unresolved — will be resolved via HTTP probe after BLE discovery
                    port = 54321,
                    supportedBridges = listOf("BLE Discovery", "Wi-Fi Direct"),
                    rssi = scanResult.rssi,
                    isBleDetected = true,
                    lastSeenTimestamp = System.currentTimeMillis()
                )

                peerMap[deviceAddress] = peer
                _discoveredBlePeers.value = peerMap.values.toList()
                Log.d(TAG, "BLE Discovered Peer: $deviceName ($deviceAddress)")
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
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth is disabled, skipping BLE discovery")
            return
        }

        bleScanner = bluetoothAdapter.bluetoothLeScanner
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SHAREDASH_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            startAdvertising()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SHAREDASH_SERVICE_UUID))
            .build()

        try {
            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
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
    }
}
