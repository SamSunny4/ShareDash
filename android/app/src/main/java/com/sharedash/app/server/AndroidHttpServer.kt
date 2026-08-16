package com.sharedash.app.server

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.sharedash.app.discovery.UdpDiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class AndroidHttpServer(
    private val context: Context,
    private val port: Int = 54321,
    private val onIncomingPairRequest: (initiatorId: String, initiatorName: String, initiatorIp: String, pin: String, appVersion: String) -> Unit = { _, _, _, _, _ -> },
    private val onPairAccepted: (targetId: String, targetName: String) -> Unit = { _, _ -> },
    private val onPairConfirmed: () -> Unit = {},
    private val onFileReceived: (fileName: String, totalBytes: Long) -> Unit = { _, _ -> }
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    val deviceId = "android-" + Build.MODEL.replace(" ", "-")
    val deviceName = Build.MODEL

    var activePairedPeerName: String? = null
    var pendingPairRequest: JSONObject? = null

    fun start(scope: CoroutineScope) {
        if (serverJob != null && serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
                Log.i(TAG, "🚀 AndroidHttpServer listening on 0.0.0.0:$port")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClientConnection(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error on port $port: ${e.message}")
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP request line
            val firstLine = readLine(input) ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = readLine(input)
            while (!line.isNullOrBlank()) {
                val colon = line.indexOf(':')
                if (colon != -1) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
                line = readLine(input)
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

            // Route handler
            when {
                // 1. Peer Discovery Probe: GET /api/v1/info
                method == "GET" && path == "/api/v1/info" -> {
                    val info = JSONObject().apply {
                        put("device_id", deviceId)
                        put("device_name", deviceName)
                        put("os_name", "Android " + Build.VERSION.RELEASE)
                        put("app_version", UdpDiscoveryManager.CURRENT_APP_VERSION)
                        put("server_port", port)
                        put("local_ips", JSONArray(getLocalIpAddresses()))
                    }
                    sendJsonResponse(output, 200, info)
                }

                // 2. 3-Way Handshake Step 1 (SYN): POST /api/v1/pair/request
                method == "POST" && path == "/api/v1/pair/request" -> {
                    val bodyBytes = readExactBytes(input, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                    val initiatorId = json.optString("initiator_device_id", "unknown")
                    val initiatorName = json.optString("initiator_name", "Remote Device")
                    var initiatorIp = json.optString("initiator_ip", "")
                    if (initiatorIp.isBlank() || initiatorIp == "127.0.0.1") {
                        initiatorIp = socket.inetAddress.hostAddress ?: "127.0.0.1"
                    }
                    val pin = json.optString("pin_code", "000000")
                    val appVer = json.optString("app_version", "0.1.0")

                    if (!UdpDiscoveryManager.isVersionCompatible(appVer)) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("status", "VERSION_INCOMPATIBLE")
                            put("message", "Version mismatch. Minimum required version: ${UdpDiscoveryManager.MIN_SUPPORTED_APP_VERSION}")
                        }
                        sendJsonResponse(output, 400, resp)
                    } else {
                        pendingPairRequest = json.apply {
                            put("initiator_ip", initiatorIp)
                            put("status", "PENDING")
                            put("timestamp_ms", System.currentTimeMillis())
                        }

                        onIncomingPairRequest(initiatorId, initiatorName, initiatorIp, pin, appVer)

                        val resp = JSONObject().apply {
                            put("success", true)
                            put("status", "PENDING")
                            put("step", "SYN_RECEIVED")
                        }
                        sendJsonResponse(output, 200, resp)
                    }
                }

                // 3. 3-Way Handshake Step 2 (SYN-ACK): POST /api/v1/pair/respond
                method == "POST" && path == "/api/v1/pair/respond" -> {
                    val bodyBytes = readExactBytes(input, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                    val action = json.optString("action", "REJECT")
                    val targetName = json.optString("target_name", "Remote PC")
                    val targetId = json.optString("target_device_id", "")

                    if (action.equals("ACCEPT", ignoreCase = true)) {
                        activePairedPeerName = targetName
                        onPairAccepted(targetId, targetName)

                        val resp = JSONObject().apply {
                            put("success", true)
                            put("status", "ACCEPTED")
                            put("step", "SYN_ACK_ACCEPTED")
                        }
                        sendJsonResponse(output, 200, resp)
                    } else {
                        activePairedPeerName = null
                        pendingPairRequest = null
                        val resp = JSONObject().apply {
                            put("success", true)
                            put("status", "REJECTED")
                        }
                        sendJsonResponse(output, 200, resp)
                    }
                }

                // 4. 3-Way Handshake Step 3 (ACK): POST /api/v1/pair/confirm
                method == "POST" && path == "/api/v1/pair/confirm" -> {
                    onPairConfirmed()
                    val resp = JSONObject().apply {
                        put("success", true)
                        put("status", "ESTABLISHED")
                        put("step", "ACK_CONFIRMED")
                    }
                    sendJsonResponse(output, 200, resp)
                }

                // 5. Pairing Status: GET /api/v1/pair/status
                method == "GET" && path == "/api/v1/pair/status" -> {
                    val resp = JSONObject().apply {
                        put("is_paired", activePairedPeerName != null)
                        put("paired_device_name", activePairedPeerName)
                        put("pending_request", pendingPairRequest)
                    }
                    sendJsonResponse(output, 200, resp)
                }

                // 6. Direct File Receiver: POST /api/v1/transfers/send
                method == "POST" && path.startsWith("/api/v1/transfers/send") -> {
                    handleIncomingFileUpload(input, headers, contentLength, output)
                }

                // 7. CORS Preflight
                method == "OPTIONS" -> {
                    sendRawResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                }

                else -> {
                    sendRawResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleIncomingFileUpload(
        input: BufferedInputStream,
        headers: Map<String, String>,
        contentLength: Int,
        output: BufferedOutputStream
    ) {
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ShareDash"
        )
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val contentType = headers["content-type"] ?: ""
        var fileName = "received_file_${System.currentTimeMillis()}.bin"

        if (contentType.contains("multipart/form-data")) {
            // Extract boundary from Content-Type header
            val boundaryMatch = Regex("boundary=(.+)").find(contentType)
            val boundary = boundaryMatch?.groupValues?.get(1)?.trim() ?: ""
            val boundaryBytes = "--$boundary".toByteArray(Charsets.ISO_8859_1)
            val endBoundaryBytes = "--$boundary--".toByteArray(Charsets.ISO_8859_1)

            // Read the multipart headers (first boundary + part headers)
            // Read lines until we hit the empty line separating headers from body
            val partHeaders = StringBuilder()
            var headerLine = readLine(input)
            // Skip initial boundary line
            if (headerLine != null && headerLine.startsWith("--")) {
                headerLine = readLine(input)
            }
            while (!headerLine.isNullOrEmpty()) {
                partHeaders.append(headerLine).append("\n")
                // Extract filename from Content-Disposition
                if (headerLine.contains("filename=\"")) {
                    val fnStart = headerLine.indexOf("filename=\"") + 10
                    val fnEnd = headerLine.indexOf("\"", fnStart)
                    if (fnEnd > fnStart) {
                        fileName = sanitizeFileName(headerLine.substring(fnStart, fnEnd))
                    }
                }
                headerLine = readLine(input)
            }

            // Now stream the file body to disk, watching for the closing boundary
            val targetFile = File(downloadDir, fileName)
            val fos = FileOutputStream(targetFile)
            val buffer = ByteArray(8192)
            var totalRead = 0
            // Calculate approximate bytes remaining for file data
            // (contentLength - headers already read - boundary overhead)
            val boundaryLen = endBoundaryBytes.size + 4 // \r\n--boundary--\r\n

            // Stream data, but we need to detect the trailing boundary.
            // Strategy: buffer last (boundaryLen + 10) bytes and check for boundary.
            val tailBufferSize = endBoundaryBytes.size + 20
            val ringBuffer = ByteArray(tailBufferSize)
            var ringLen = 0

            // Simple streaming: read and write, then trim trailing boundary at the end
            try {
                var remaining = contentLength
                // Account for headers we already consumed via readLine
                // We can't know exact bytes consumed by readLine, so just stream remaining
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size)
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    totalRead += read
                    remaining -= read
                }
            } finally {
                fos.flush()
                fos.close()
            }

            // Trim trailing boundary from the file
            trimTrailingBoundary(targetFile, boundaryBytes)

            onFileReceived(fileName, targetFile.length())
        } else {
            // Non-multipart: stream directly
            val targetFile = File(downloadDir, fileName)
            val fos = FileOutputStream(targetFile)
            val buffer = ByteArray(8192)
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = minOf(remaining, buffer.size)
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                remaining -= read
            }
            fos.flush()
            fos.close()
            onFileReceived(fileName, targetFile.length())
        }

        val resp = JSONObject().apply {
            put("success", true)
            put("file_name", fileName)
            put("download_folder", downloadDir.absolutePath)
        }
        sendJsonResponse(output, 200, resp)
    }

    private fun findDoubleCRLF(bytes: ByteArray): Int {
        for (i in 0 until bytes.size - 3) {
            if (bytes[i] == 13.toByte() && bytes[i + 1] == 10.toByte() &&
                bytes[i + 2] == 13.toByte() && bytes[i + 3] == 10.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun readLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var b: Int
        while (true) {
            b = input.read()
            if (b == -1) {
                if (baos.size() == 0) return null
                break
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString("UTF-8")
    }

    private fun readExactBytes(input: BufferedInputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) bytes else bytes.copyOf(offset)
    }

    private fun sendJsonResponse(output: BufferedOutputStream, code: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        sendRawResponse(output, code, if (code == 200) "OK" else "Error", "application/json; charset=utf-8", body)
    }

    private fun sendRawResponse(
        output: BufferedOutputStream,
        code: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val headers = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun getLocalIpAddresses(): List<String> {
        val list = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isLoopback && iface.isUp) {
                    for (addr in iface.inetAddresses) {
                        val host = addr.hostAddress ?: ""
                        if (host.contains(".") && !host.startsWith("127.")) {
                            list.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun stop() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun sanitizeFileName(name: String): String {
        // Strip path separators and directory traversal
        var sanitized = name.replace("/", "_").replace("\\", "_").replace("..", "_")
        // Limit length
        if (sanitized.length > 200) {
            val ext = sanitized.substringAfterLast('.', "")
            sanitized = sanitized.take(190) + if (ext.isNotEmpty()) ".$ext" else ""
        }
        if (sanitized.isBlank()) sanitized = "received_file_${System.currentTimeMillis()}.bin"
        return sanitized
    }

    private fun trimTrailingBoundary(file: File, boundaryBytes: ByteArray) {
        // Read the last portion of the file and remove the multipart boundary
        val searchLen = boundaryBytes.size + 20 // boundary + CRLF overhead
        if (file.length() < searchLen) return

        val raf = java.io.RandomAccessFile(file, "rw")
        try {
            val tailStart = maxOf(0L, file.length() - searchLen)
            raf.seek(tailStart)
            val tail = ByteArray((file.length() - tailStart).toInt())
            raf.readFully(tail)

            // Search for the boundary pattern in the tail: \r\n--boundary
            val searchPattern = "\r\n--".toByteArray(Charsets.ISO_8859_1) + boundaryBytes.drop(2).toByteArray()
            val boundaryStr = String(boundaryBytes, Charsets.ISO_8859_1)
            val tailStr = String(tail, Charsets.ISO_8859_1)
            val idx = tailStr.lastIndexOf("\r\n$boundaryStr")
            if (idx >= 0) {
                raf.setLength(tailStart + idx)
            } else {
                // Try just the boundary without CRLF prefix
                val idx2 = tailStr.lastIndexOf(boundaryStr)
                if (idx2 >= 0) {
                    raf.setLength(tailStart + idx2)
                }
            }
        } finally {
            raf.close()
        }
    }

    companion object {
        private const val TAG = "AndroidHttpServer"
    }
}
