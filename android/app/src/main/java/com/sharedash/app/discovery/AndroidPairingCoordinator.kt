package com.sharedash.app.discovery

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AndroidPairingCoordinator(private val openConnection: (URL) -> HttpURLConnection) {

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
            val conn = openConnection(url)
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

            try {
                conn.responseCode in 200..299 && conn.inputStream.bufferedReader().use { JSONObject(it.readText()).optBoolean("success") }
            } finally { conn.disconnect() }
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
            val conn = openConnection(url)
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
            val conn = openConnection(url)
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

    suspend fun awaitAcceptance(ip: String, port: Int, localName: String): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 30_000L
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val conn = openConnection(URL("http://$ip:$port/api/v1/pair/status"))
                try {
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500
                    val status = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    if (status.optBoolean("is_paired") && status.optString("paired_device_name") == localName) {
                        confirmPairSession(ip, port)
                        return@withContext true
                    }
                } catch (_: java.io.IOException) {
                } catch (_: org.json.JSONException) {
                } finally {
                    conn.disconnect()
                }
                delay(450)
            }
            false
        }

    companion object {
        private const val TAG = "AndroidPairingCoordinator"
    }
}
