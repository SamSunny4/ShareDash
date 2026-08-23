package com.sharedash.app.discovery

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AndroidPairingCoordinator {

    private var pollingJob: Job? = null

    suspend fun sendPairRequest(
        targetIp: String,
        targetPort: Int,
        pin: String,
        deviceName: String,
        localIp: String = "127.0.0.1",
        localPort: Int = 54321
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$targetIp:$targetPort/api/v1/pair/request")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("initiator_device_id", com.sharedash.app.DeviceIdentity.id)
                put("initiator_name", deviceName)
                put("initiator_ip", localIp)
                put("initiator_port", localPort)   // Port for PC SYN-ACK callback
                put("pin_code", pin)
                put("app_version", "0.1.0")
                put("status", "PENDING")
                put("step", "SYN")
                put("timestamp_ms", System.currentTimeMillis())
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send pair request to $targetIp:$targetPort: ${e.message}")
            false
        }
    }

    suspend fun respondToPairRequest(
        targetIp: String,
        targetPort: Int,
        accept: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$targetIp:$targetPort/api/v1/pair/respond")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("action", if (accept) "ACCEPT" else "REJECT")
                put("target_name", Build.MODEL)
                put("target_device_id", com.sharedash.app.DeviceIdentity.id)
                put("step", "SYN_ACK")
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to respond to pair request: ${e.message}")
            false
        }
    }

    suspend fun confirmPairSession(
        targetIp: String,
        targetPort: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$targetIp:$targetPort/api/v1/pair/confirm")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("step", "ACK")
                put("status", "ESTABLISHED")
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to confirm pair session: ${e.message}")
            false
        }
    }

    fun startPairingPoller(
        scope: CoroutineScope,
        targetIp: String,
        targetPort: Int,
        onIncoming: (pin: String, name: String) -> Unit,
        onAccepted: (name: String) -> Unit
    ) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            var confirmed = false
            while (isActive && !confirmed) {
                try {
                    val url = URL("http://$targetIp:$targetPort/api/v1/pair/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500
                    conn.requestMethod = "GET"

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val text = reader.readText()
                        reader.close()

                        val json = JSONObject(text)
                        val isPaired = json.optBoolean("is_paired", false)
                        val pairedName = json.optString("paired_device_name", "")

                        if (isPaired && pairedName.isNotEmpty()) {
                            confirmed = true
                            // Complete 3-Way Handshake Step 3: ACK
                            confirmPairSession(targetIp, targetPort)
                            withContext(Dispatchers.Main) {
                                onAccepted(pairedName)
                            }
                        }

                        val pending = json.optJSONObject("pending_request")
                        if (pending != null && pending.optString("status") == "PENDING") {
                            val pin = pending.optString("pin_code", "")
                            val name = pending.optString("initiator_name", "Nearby Device")
                            withContext(Dispatchers.Main) {
                                onIncoming(pin, name)
                            }
                        }
                    }
                } catch (_: Exception) {}

                delay(1000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    companion object {
        private const val TAG = "AndroidPairingCoordinator"
    }
}
