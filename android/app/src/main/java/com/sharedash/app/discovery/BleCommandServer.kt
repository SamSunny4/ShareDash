package com.sharedash.app.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * BLE GATT Command Server — runs on the Android phone.
 *
 * Exposes a GATT service under ShareDash UUID (0x5344):
 * 1. WIFI_CAPS_CHAR (READ + NOTIFY)  — Returns JSON with phone Wi-Fi capabilities.
 * 2. COMMAND_CHAR   (WRITE)          — Accepts JSON commands from the PC.
 * 3. RESPONSE_CHAR  (READ + NOTIFY)  — Returns command results / status updates.
 */
class BleCommandServer(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = try {
        bluetoothManager?.adapter
    } catch (_: Exception) {
        null
    }
    private var gattServer: BluetoothGattServer? = null

    val isRunning: Boolean
        get() = gattServer != null

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Callbacks wired by MainActivity
    var onWifiConnectRequest: ((ssid: String, password: String) -> Unit)? = null
    var onUsbTetherRequest: (() -> Unit)? = null
    var onStartHotspotRequest: (() -> Unit)? = null
    var onCommandReceived: ((cmd: String, payload: JSONObject) -> Unit)? = null
    var onIncomingPairRequest: ((initiatorId: String, initiatorName: String, initiatorIp: String, pin: String, appVer: String) -> Unit)? = null

    companion object {
        private const val TAG = "BleCommandServer"

        // ShareDash service UUID: 0x5344
        val SERVICE_UUID: UUID = UUID.fromString("00005344-0000-1000-8000-00805F9B34FB")

        // Wi-Fi Capabilities characteristic (READ + NOTIFY): 0x5345
        val WIFI_CAPS_CHAR_UUID: UUID = UUID.fromString("00005345-0000-1000-8000-00805F9B34FB")

        // Command characteristic (WRITE + WRITE_NO_RESPONSE): 0x5346
        val COMMAND_CHAR_UUID: UUID = UUID.fromString("00005346-0000-1000-8000-00805F9B34FB")

        // Response characteristic (READ + NOTIFY): 0x5347
        val RESPONSE_CHAR_UUID: UUID = UUID.fromString("00005347-0000-1000-8000-00805F9B34FB")

        // Client Characteristic Configuration Descriptor
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var lastResponseValue: ByteArray = "{}".toByteArray(Charsets.UTF_8)
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val gattCallback = object : BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val name = device?.name ?: device?.address ?: "Unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    device?.let { connectedDevices.add(it) }
                    Log.i(TAG, "🔗 GATT client connected: $name ($status)")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    device?.let { connectedDevices.remove(it) }
                    Log.i(TAG, "🔌 GATT client disconnected: $name ($status)")
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "✅ GATT Service 0x5344 added successfully: ${service?.uuid}")
            } else {
                Log.e(TAG, "❌ Failed to add GATT Service: status=$status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            Log.i(TAG, "⚡ MTU changed for ${device?.address} to $mtu bytes")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            when (characteristic?.uuid) {
                WIFI_CAPS_CHAR_UUID -> {
                    val caps = buildWifiCapsJson().toByteArray(Charsets.UTF_8)
                    val chunk = if (offset < caps.size) caps.copyOfRange(offset, caps.size) else ByteArray(0)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                    Log.d(TAG, "READ wifi_caps (offset=$offset, len=${chunk.size})")
                }
                RESPONSE_CHAR_UUID -> {
                    val chunk = if (offset < lastResponseValue.size) lastResponseValue.copyOfRange(offset, lastResponseValue.size) else ByteArray(0)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                    Log.d(TAG, "READ response (offset=$offset, len=${chunk.size})")
                }
                else -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        private val preparedWriteBuffer = java.io.ByteArrayOutputStream()

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic?.uuid == COMMAND_CHAR_UUID && value != null) {
                if (preparedWrite) {
                    preparedWriteBuffer.write(value)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }
                    return
                }

                val cmdStr = String(value, Charsets.UTF_8)
                Log.i(TAG, "📨 Command received: $cmdStr")

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                try {
                    val json = JSONObject(cmdStr)
                    handleCommand(json, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid command JSON: ${e.message}")
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            if (execute && preparedWriteBuffer.size() > 0) {
                val cmdStr = preparedWriteBuffer.toString("UTF-8")
                preparedWriteBuffer.reset()
                Log.i(TAG, "📨 Prepared write executed: $cmdStr")
                try {
                    val json = JSONObject(cmdStr)
                    handleCommand(json, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid prepared write command JSON: ${e.message}")
                }
            } else {
                preparedWriteBuffer.reset()
            }
            if (requestId != 0) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // Accept CCCD writes (0x0001 = enable notifications)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            Log.d(TAG, "CCCD descriptor write handled for ${device?.address}")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            val resp = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val chunk = if (offset < resp.size) resp.copyOfRange(offset, resp.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, skipping GATT server start")
            return
        }

        try {
            if (bluetoothAdapter?.isEnabled != true) {
                Log.w(TAG, "Bluetooth not enabled, cannot start GATT server")
                return
            }

            if (gattServer != null) {
                Log.i(TAG, "GATT Server already active")
                return
            }

            gattServer = bluetoothManager?.openGattServer(context, gattCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server (permission or adapter issue)")
                return
            }

            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Wi-Fi Capabilities — READ + NOTIFY
            val wifiCapsChar = BluetoothGattCharacteristic(
                WIFI_CAPS_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(wifiCapsChar)

            // Command — WRITE + WRITE_NO_RESPONSE
            val commandChar = BluetoothGattCharacteristic(
                COMMAND_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(commandChar)

            // Response — READ + NOTIFY
            val responseChar = BluetoothGattCharacteristic(
                RESPONSE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(responseChar)

            val added = gattServer?.addService(service) ?: false
            Log.i(TAG, "🚀 GATT Command Server initialized, addService result=$added")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in GATT server start: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT Command Server: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleCommand(json: JSONObject, device: BluetoothDevice?) {
        val cmd = json.optString("cmd", "")
        onCommandReceived?.invoke(cmd, json)

        when (cmd) {
            "ping", "get_info", "get_network_info" -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val isWifiOn = wifiManager?.isWifiEnabled ?: false
                val ips = getLocalIpAddresses()
                sendResponse(device, JSONObject().apply {
                    put("status", "pong")
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("device_id", com.sharedash.app.DeviceIdentity.id)
                    put("app_version", "0.1.0")
                    put("server_port", 54321)
                    put("wifi_enabled", isWifiOn)
                    put("local_ips", org.json.JSONArray(ips))
                })
            }

            "get_wifi_caps" -> {
                val caps = buildWifiCapsJson()
                sendResponse(device, JSONObject(caps))
            }

            "wifi_connect" -> {
                val ssid = json.optString("ssid", "")
                val password = json.optString("password", "")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val isWifiOn = wifiManager?.isWifiEnabled ?: false
                if (ssid.isNotBlank()) {
                    Log.i(TAG, "📶 Wi-Fi connect request: SSID='$ssid', phone wifi_enabled=$isWifiOn")
                    onWifiConnectRequest?.invoke(ssid, password)
                    sendResponse(device, JSONObject().apply {
                        put("status", "wifi_connecting")
                        put("ssid", ssid)
                        put("wifi_enabled", isWifiOn)
                    })
                } else {
                    sendResponse(device, JSONObject().apply {
                        put("status", "error")
                        put("message", "Missing SSID")
                    })
                }
            }

            "usb_tether_on" -> {
                Log.i(TAG, "🔌 USB tethering request")
                onUsbTetherRequest?.invoke()
                sendResponse(device, JSONObject().apply {
                    put("status", "usb_tether_opening")
                })
            }

            "start_hotspot" -> {
                Log.i(TAG, "📡 Hotspot start request")
                onStartHotspotRequest?.invoke()
                sendResponse(device, JSONObject().apply {
                    put("status", "hotspot_starting")
                })
            }

            "pair_request" -> {
                val initiatorId = json.optString("device_id", "pc")
                val initiatorName = json.optString("friendly_name", "PC")
                val initiatorIp = json.optString("ip_address", "127.0.0.1")
                val pin = json.optString("pin", "000000")
                val appVer = json.optString("app_version", "0.1.0")
                onIncomingPairRequest?.invoke(initiatorId, initiatorName, initiatorIp, pin, appVer)
                sendResponse(device, JSONObject().apply {
                    put("status", "pair_prompt_shown")
                    put("pin", pin)
                })
            }

            else -> {
                Log.w(TAG, "Unknown command: $cmd")
                sendResponse(device, JSONObject().apply {
                    put("status", "error")
                    put("message", "Unknown command: $cmd")
                })
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendResponse(device: BluetoothDevice?, response: JSONObject) {
        try {
            val bytes = response.toString().toByteArray(Charsets.UTF_8)
            lastResponseValue = bytes
            val responseChar = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(RESPONSE_CHAR_UUID)
            if (responseChar != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (device != null) {
                        gattServer?.notifyCharacteristicChanged(device, responseChar, false, bytes)
                    } else {
                        for (dev in connectedDevices) {
                            try {
                                gattServer?.notifyCharacteristicChanged(dev, responseChar, false, bytes)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    responseChar.value = bytes
                    if (device != null) {
                        @Suppress("DEPRECATION")
                        gattServer?.notifyCharacteristicChanged(device, responseChar, false)
                    } else {
                        for (dev in connectedDevices) {
                            try {
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(dev, responseChar, false)
                            } catch (_: Exception) {}
                        }
                    }
                }
                Log.d(TAG, "📤 Response sent: ${response.toString().take(120)}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException sending BLE response: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending BLE response: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendHotspotStartedResponse(ssid: String, password: String, gateway: String) {
        val response = JSONObject().apply {
            put("status", "hotspot_started")
            put("ssid", ssid)
            put("password", password)
            put("gateway", gateway)
        }
        sendResponse(null, response)
    }

    private fun buildWifiCapsJson(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val json = JSONObject()

        val wifiInfo = wifiManager?.connectionInfo
        var wifiStandard = "Wi-Fi 6 (802.11ax)"
        var maxFreqGhz = 6.0
        var maxChannelWidthMhz = 160
        var maxPhyRateMbps = 1200

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiInfo != null) {
            when (wifiInfo.wifiStandard) {
                6 -> { wifiStandard = "Wi-Fi 6 (802.11ax)"; maxPhyRateMbps = 1200; maxChannelWidthMhz = 160 }
                7 -> { wifiStandard = "Wi-Fi 6E (802.11ax)"; maxFreqGhz = 6.0; maxPhyRateMbps = 2402; maxChannelWidthMhz = 160 }
                8 -> { wifiStandard = "Wi-Fi 7 (802.11be)"; maxFreqGhz = 6.0; maxPhyRateMbps = 4804; maxChannelWidthMhz = 320 }
                else -> {}
            }
        }

        val bands = JSONArray()
        bands.put("2.4 GHz")
        bands.put("5 GHz")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (wifiManager?.is6GHzBandSupported == true) {
                    bands.put("6 GHz")
                }
            } catch (_: Exception) {}
        }

        json.put("wifi_standard", wifiStandard)
        json.put("max_frequency_ghz", maxFreqGhz)
        json.put("max_channel_width_mhz", maxChannelWidthMhz)
        json.put("max_phy_rate_mbps", maxPhyRateMbps)
        json.put("supported_bands", bands)

        return json.toString()
    }

    fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return ips
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        addr.hostAddress?.let { ips.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            connectedDevices.clear()
            Log.i(TAG, "GATT Command Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT server: ${e.message}")
        }
    }
}
