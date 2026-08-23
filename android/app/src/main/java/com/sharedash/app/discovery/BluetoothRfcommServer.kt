package com.sharedash.app.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM Server — runs on the Android phone.
 *
 * Provides a high-speed, reliable bidirectional streaming socket over Bluetooth Classic (SPP/RFCOMM).
 * Used alongside BLE GATT for robust control, pairing exchange, and data transfer.
 */
class BluetoothRfcommServer(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = try {
        bluetoothManager?.adapter
    } catch (_: Exception) {
        null
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var isListening = false
    private var acceptJob: Job? = null
    private val activeSockets = mutableListOf<BluetoothSocket>()

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    var onWifiConnectRequest: ((ssid: String, password: String) -> Unit)? = null
    var onUsbTetherRequest: (() -> Unit)? = null
    var onStartHotspotRequest: (() -> Unit)? = null
    var onCommandReceived: ((cmd: String, payload: JSONObject) -> Unit)? = null
    var onIncomingPairRequest: ((initiatorId: String, initiatorName: String, initiatorIp: String, pin: String, appVer: String) -> Unit)? = null

    companion object {
        private const val TAG = "BluetoothRfcommServer"
        val RFCOMM_UUID: UUID = UUID.fromString("00005344-0000-1000-8000-00805F9B34FB")
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    val isRunning: Boolean
        get() = isListening

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (!hasConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, skipping RFCOMM server start")
            return
        }

        try {
            if (bluetoothAdapter?.isEnabled != true) {
                Log.w(TAG, "Bluetooth not enabled, skipping RFCOMM server start")
                return
            }

            if (isListening) {
                Log.i(TAG, "RFCOMM server already listening")
                return
            }

            acceptJob = scope.launch(Dispatchers.IO) {
                try {
                    // Try insecure RFCOMM first (faster, no OS PIN prompt required), fallback to secure
                    serverSocket = try {
                        bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ShareDash", RFCOMM_UUID)
                    } catch (_: Exception) {
                        bluetoothAdapter.listenUsingRfcommWithServiceRecord("ShareDash", RFCOMM_UUID)
                    }

                    isListening = true
                    Log.i(TAG, "🚀 Bluetooth RFCOMM Server listening on UUID $RFCOMM_UUID")

                    while (isActive && isListening) {
                        try {
                            val socket = serverSocket?.accept() ?: break
                            val remoteName = try {
                                socket.remoteDevice?.name ?: "Unknown"
                            } catch (_: SecurityException) {
                                "Unknown"
                            }
                            Log.i(TAG, "🔗 Bluetooth RFCOMM client connected: $remoteName")
                            synchronized(activeSockets) { activeSockets.add(socket) }
                            launch(Dispatchers.IO) {
                                handleClientSocket(socket)
                            }
                        } catch (e: Exception) {
                            if (isListening) {
                                Log.w(TAG, "RFCOMM accept loop error: ${e.message}")
                            }
                            break
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException creating RFCOMM server: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create RFCOMM server socket: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in RFCOMM start: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting RFCOMM: ${e.message}")
        }
    }

    private suspend fun handleClientSocket(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)

            while (socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                Log.d(TAG, "📨 RFCOMM Msg: $line")
                try {
                    val json = JSONObject(line)
                    val cmd = json.optString("cmd", "")
                    onCommandReceived?.invoke(cmd, json)

                    when (cmd) {
                        "ping" -> {
                            val resp = JSONObject().apply {
                                put("status", "pong")
                                put("device_name", Build.MODEL)
                                put("device_id", com.sharedash.app.DeviceIdentity.id)
                                put("app_version", "0.1.0")
                            }
                            writer.println(resp.toString())
                        }

                        "wifi_connect" -> {
                            val ssid = json.optString("ssid", "")
                            val password = json.optString("password", "")
                            if (ssid.isNotBlank()) {
                                onWifiConnectRequest?.invoke(ssid, password)
                                val resp = JSONObject().apply {
                                    put("status", "wifi_connecting")
                                    put("ssid", ssid)
                                }
                                writer.println(resp.toString())
                            }
                        }

                        "usb_tether_on" -> {
                            onUsbTetherRequest?.invoke()
                            val resp = JSONObject().apply {
                                put("status", "usb_tether_opening")
                            }
                            writer.println(resp.toString())
                        }

                        "start_hotspot" -> {
                            onStartHotspotRequest?.invoke()
                            val resp = JSONObject().apply {
                                put("status", "hotspot_starting")
                            }
                            writer.println(resp.toString())
                        }

                        "pair_request" -> {
                            val initiatorId = json.optString("device_id", "pc")
                            val initiatorName = json.optString("friendly_name", "PC")
                            val initiatorIp = json.optString("ip_address", "127.0.0.1")
                            val pin = json.optString("pin", "000000")
                            val appVer = json.optString("app_version", "0.1.0")
                            onIncomingPairRequest?.invoke(initiatorId, initiatorName, initiatorIp, pin, appVer)
                            val resp = JSONObject().apply {
                                put("status", "pair_prompt_shown")
                                put("pin", pin)
                            }
                            writer.println(resp.toString())
                        }

                        else -> {
                            val resp = JSONObject().apply {
                                put("status", "ack")
                                put("cmd", cmd)
                            }
                            writer.println(resp.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling RFCOMM JSON: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RFCOMM client disconnected: ${e.message}")
        } finally {
            synchronized(activeSockets) { activeSockets.remove(socket) }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun broadcastJson(json: JSONObject) {
        val payload = json.toString()
        synchronized(activeSockets) {
            activeSockets.forEach { socket ->
                try {
                    val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)
                    writer.println(payload)
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        isListening = false
        acceptJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        synchronized(activeSockets) {
            activeSockets.forEach { try { it.close() } catch (_: Exception) {} }
            activeSockets.clear()
        }
        Log.i(TAG, "Bluetooth RFCOMM Server stopped")
    }
}
