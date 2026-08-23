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

    val deviceId = com.sharedash.app.DeviceIdentity.id
    val deviceName = Build.MODEL

    data class ActiveChunkTransfer(
        val transferId: String,
        val fileName: String,
        val totalBytes: Long,
        val totalChunks: Int,
        val targetFile: File,
        val raf: java.io.RandomAccessFile,
        val receivedChunks: java.util.concurrent.ConcurrentHashMap.KeySetView<Int, Boolean> = java.util.concurrent.ConcurrentHashMap.newKeySet(),
        val startTime: Long = System.currentTimeMillis()
    )

    private val activeChunkTransfers = java.util.concurrent.ConcurrentHashMap<String, ActiveChunkTransfer>()

    var activePairedPeerName: String? = null
    var pendingPairRequest: JSONObject? = null
    var onWifiConnectRequest: ((ssid: String, password: String) -> Unit)? = null
    var onStartHotspotRequest: ((callback: (ssid: String, password: String, gateway: String) -> Unit) -> Unit)? = null

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

                // 6. Adaptive Out-of-Order Chunk Receiver: POST /api/v1/transfers/chunk
                method == "POST" && (path.startsWith("/api/v1/transfers/chunk") || headers.containsKey("x-chunk-id")) -> {
                    socket.soTimeout = 0
                    try {
                        handleIncomingChunkUpload(input, headers, contentLength, output)
                    } finally {
                        socket.soTimeout = 8000
                    }
                }

                // 7. Direct File Stream Receiver: POST /api/v1/transfers/send
                method == "POST" && path.startsWith("/api/v1/transfers/send") -> {
                    socket.soTimeout = 0
                    try {
                        handleIncomingFileUpload(input, headers, contentLength, output)
                    } finally {
                        socket.soTimeout = 8000
                    }
                }

                // 8. Wi-Fi Hardware Capabilities: GET /api/v1/wifi_caps
                method == "GET" && path == "/api/v1/wifi_caps" -> {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    var wifiStandard = "Wi-Fi 6 (802.11ax)"
                    var maxFreqGhz = 6.0
                    var maxChannelWidthMhz = 160
                    var maxPhyRateMbps = 1200

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val wifiInfo = wifiManager?.connectionInfo
                            if (wifiInfo != null) {
                                when (wifiInfo.wifiStandard) {
                                    6 -> { wifiStandard = "Wi-Fi 6 (802.11ax)"; maxPhyRateMbps = 1200; maxChannelWidthMhz = 160 }
                                    7 -> { wifiStandard = "Wi-Fi 6E (802.11ax)"; maxFreqGhz = 6.0; maxPhyRateMbps = 2402; maxChannelWidthMhz = 160 }
                                    8 -> { wifiStandard = "Wi-Fi 7 (802.11be)"; maxFreqGhz = 6.0; maxPhyRateMbps = 4804; maxChannelWidthMhz = 320 }
                                    else -> {}
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val bands = org.json.JSONArray().apply {
                        put("2.4 GHz")
                        put("5 GHz")
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager?.is6GHzBandSupported == true) {
                                put("6 GHz")
                            }
                        } catch (_: Exception) {}
                    }

                    val resp = JSONObject().apply {
                        put("wifi_standard", wifiStandard)
                        put("max_frequency_ghz", maxFreqGhz)
                        put("max_channel_width_mhz", maxChannelWidthMhz)
                        put("max_phy_rate_mbps", maxPhyRateMbps)
                        put("supported_bands", bands)
                        put("hotspot_supported", true)
                        put("hotspot_5ghz_supported", true)
                    }
                    sendJsonResponse(output, 200, resp)
                }

                // 9. Wi-Fi Connect over USB: POST /api/v1/wifi_connect
                method == "POST" && path == "/api/v1/wifi_connect" -> {
                    val bodyBytes = readExactBytes(input, contentLength)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }
                    val ssid = json.optString("ssid", "")
                    val password = json.optString("password", "")
                    if (ssid.isNotBlank()) {
                        onWifiConnectRequest?.invoke(ssid, password)
                        val resp = JSONObject().apply {
                            put("success", true)
                            put("status", "CONNECTING")
                            put("ssid", ssid)
                        }
                        sendJsonResponse(output, 200, resp)
                    } else {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", "MISSING_SSID")
                        }
                        sendJsonResponse(output, 400, resp)
                    }
                }

                // 10. Start Hotspot over USB: POST /api/v1/hotspot/start
                method == "POST" && path == "/api/v1/hotspot/start" -> {
                    if (onStartHotspotRequest != null) {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var resultSsid = ""
                        var resultPass = ""
                        var resultGw = "192.168.43.1"
                        onStartHotspotRequest?.invoke { s, p, g ->
                            resultSsid = s
                            resultPass = p
                            resultGw = g
                            latch.countDown()
                        }
                        latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                        val resp = JSONObject().apply {
                            put("success", resultSsid.isNotBlank())
                            put("status", if (resultSsid.isNotBlank()) "hotspot_started" else "failed")
                            put("ssid", resultSsid)
                            put("password", resultPass)
                            put("gateway", resultGw)
                        }
                        sendJsonResponse(output, 200, resp)
                    } else {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", "HANDLER_NOT_SET")
                        }
                        sendJsonResponse(output, 500, resp)
                    }
                }

                // 11. CORS Preflight
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

    var onTransferProgress: (fileName: String, bytesReceived: Long, totalBytes: Long, speedMbps: Double) -> Unit = { _, _, _, _ -> }

    private fun computeSha256(bytes: ByteArray): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun handleIncomingChunkUpload(
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

        val chunkId = headers["x-chunk-id"]?.toIntOrNull() ?: 0
        val chunkOffset = headers["x-chunk-offset"]?.toLongOrNull() ?: 0L
        val expectedSha256 = headers["x-chunk-sha256"]?.lowercase()?.trim() ?: ""
        val expectedCrc32 = headers["x-chunk-crc32"]?.lowercase()?.trim() ?: ""
        val totalChunks = headers["x-total-chunks"]?.toIntOrNull() ?: 1
        val transferId = headers["x-transfer-id"] ?: "default"
        val fileSize = headers["x-file-size"]?.toLongOrNull() ?: 0L
        var fileName = headers["x-file-name"]?.let { sanitizeFileName(it) }
            ?: "received_file_${System.currentTimeMillis()}.bin"

        val chunkBytes = readExactBytes(input, contentLength)

        // Automatic error detection: Verify chunk hash (CRC32 or SHA-256) if present
        if (expectedCrc32.isNotBlank()) {
            val crc = java.util.zip.CRC32().apply { update(chunkBytes) }.value
            val actualCrc32 = String.format("%08x", crc)
            if (actualCrc32 != expectedCrc32) {
                Log.w(TAG, "Chunk #$chunkId CRC32 verification failed! Expected $expectedCrc32, got $actualCrc32")
                val resp = JSONObject().apply {
                    put("success", false)
                    put("error", "CORRUPT_CHUNK")
                    put("chunk_id", chunkId)
                    put("message", "CRC32 mismatch - requesting retransmission")
                }
                sendJsonResponse(output, 400, resp)
                return
            }
        } else if (expectedSha256.isNotBlank()) {
            val actualSha256 = computeSha256(chunkBytes)
            if (actualSha256 != expectedSha256) {
                Log.w(TAG, "Chunk #$chunkId verification failed! Expected $expectedSha256, got $actualSha256")
                val resp = JSONObject().apply {
                    put("success", false)
                    put("error", "CORRUPT_CHUNK")
                    put("chunk_id", chunkId)
                    put("message", "SHA-256 mismatch - requesting retransmission")
                }
                sendJsonResponse(output, 400, resp)
                return
            }
        }

        val sessionKey = if (transferId.isNotBlank()) transferId else fileName
        val session = activeChunkTransfers.computeIfAbsent(sessionKey) {
            val targetFile = File(downloadDir, fileName)
            val raf = java.io.RandomAccessFile(targetFile, "rw")
            if (fileSize > 0) {
                try { raf.setLength(fileSize) } catch (_: Exception) {}
            }
            ActiveChunkTransfer(transferId, fileName, fileSize, totalChunks, targetFile, raf)
        }

        // Out-of-order random access write
        synchronized(session.raf) {
            try {
                session.raf.seek(chunkOffset)
                session.raf.write(chunkBytes)
                session.receivedChunks.add(chunkId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing chunk #$chunkId at offset $chunkOffset: ${e.message}")
                val resp = JSONObject().apply {
                    put("success", false)
                    put("error", "WRITE_FAILED")
                    put("chunk_id", chunkId)
                }
                sendJsonResponse(output, 500, resp)
                return
            }
        }

        val completedCount = session.receivedChunks.size
        val total = session.totalChunks

        val elapsedSec = (System.currentTimeMillis() - session.startTime).coerceAtLeast(1) / 1000.0
        val totalBytesWritten = completedCount.toLong() * (contentLength.toLong().coerceAtLeast(1L))
        val speedMbps = ((totalBytesWritten * 8.0) / 1_000_000.0) / elapsedSec
        onTransferProgress(session.fileName, totalBytesWritten, session.totalBytes, speedMbps)

        if (completedCount >= total) {
            try {
                synchronized(session.raf) {
                    session.raf.close()
                }
            } catch (_: Exception) {}
            activeChunkTransfers.remove(sessionKey)
            Log.i(TAG, "🎉 Transfer completed for ${session.fileName}: $completedCount/$total chunks verified & saved")
            onFileReceived(session.fileName, session.targetFile.length())
        }

        val resp = JSONObject().apply {
            put("success", true)
            put("chunk_id", chunkId)
            put("completed_chunks", completedCount)
            put("total_chunks", total)
        }
        sendJsonResponse(output, 200, resp)
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
        var fileName = headers["x-file-name"]?.let { sanitizeFileName(it) }
            ?: "received_file_${System.currentTimeMillis()}.bin"

        val startTime = System.currentTimeMillis()
        var totalWritten = 0L

        if (contentType.contains("multipart/form-data")) {
            val boundaryMatch = Regex("boundary=(.+)").find(contentType)
            val boundary = boundaryMatch?.groupValues?.get(1)?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: ""
            val boundaryBytes = "--$boundary".toByteArray(Charsets.ISO_8859_1)

            var headerBytesConsumed = 0
            var headerLine = readLine(input)
            if (headerLine != null) headerBytesConsumed += headerLine.toByteArray(Charsets.UTF_8).size + 2
            while (!headerLine.isNullOrBlank()) {
                if (headerLine.contains("filename=\"")) {
                    val fnStart = headerLine.indexOf("filename=\"") + 10
                    val fnEnd = headerLine.indexOf("\"", fnStart)
                    if (fnEnd > fnStart) {
                        fileName = sanitizeFileName(headerLine.substring(fnStart, fnEnd))
                    }
                }
                headerLine = readLine(input)
                if (headerLine != null) headerBytesConsumed += headerLine.toByteArray(Charsets.UTF_8).size + 2
            }

            val targetFile = File(downloadDir, fileName)
            val fos = BufferedOutputStream(FileOutputStream(targetFile), 512 * 1024)

            val buf = ByteArray(512 * 1024)
            var remaining = if (contentLength > 0) (contentLength - headerBytesConsumed).toLong().coerceAtLeast(0L) else Long.MAX_VALUE
            while (remaining > 0) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val bytesRead = input.read(buf, 0, toRead)
                if (bytesRead == -1) break
                fos.write(buf, 0, bytesRead)
                remaining -= bytesRead
                totalWritten += bytesRead

                val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                val speedMbps = ((totalWritten * 8.0) / 1_000_000.0) / elapsedSec
                val totalExpected = if (contentLength > 0) contentLength.toLong() else totalWritten
                onTransferProgress(fileName, totalWritten, totalExpected, speedMbps)
            }
            fos.flush()
            fos.close()

            if (boundaryBytes.isNotEmpty()) {
                trimTrailingBoundary(targetFile, boundaryBytes)
            }

            checkAndMergeParts(fileName, downloadDir)
        } else {
            val targetFile = File(downloadDir, fileName)
            val fos = BufferedOutputStream(FileOutputStream(targetFile), 512 * 1024)
            val buf = ByteArray(512 * 1024)
            var remaining = if (contentLength > 0) contentLength.toLong() else Long.MAX_VALUE
            while (remaining > 0) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val bytesRead = input.read(buf, 0, toRead)
                if (bytesRead == -1) break
                fos.write(buf, 0, bytesRead)
                remaining -= bytesRead
                totalWritten += bytesRead

                val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                val speedMbps = ((totalWritten * 8.0) / 1_000_000.0) / elapsedSec
                val totalExpected = if (contentLength > 0) contentLength.toLong() else totalWritten
                onTransferProgress(fileName, totalWritten, totalExpected, speedMbps)
            }
            fos.flush()
            fos.close()
            checkAndMergeParts(fileName, downloadDir)
        }

        val resp = JSONObject().apply {
            put("success", true)
            put("file_name", fileName)
            put("bytes_received", totalWritten)
            put("download_folder", downloadDir.absolutePath)
        }
        sendJsonResponse(output, 200, resp)
    }

    private fun checkAndMergeParts(partFileName: String, downloadDir: File) {
        if (!partFileName.endsWith(".part1") && !partFileName.endsWith(".part2")) {
            val file = File(downloadDir, partFileName)
            onFileReceived(partFileName, file.length())
            return
        }

        val baseName = partFileName.removeSuffix(".part1").removeSuffix(".part2")
        val part1File = File(downloadDir, "$baseName.part1")
        val part2File = File(downloadDir, "$baseName.part2")

        if (part1File.exists() && part2File.exists()) {
            val finalTarget = File(downloadDir, baseName)
            try {
                java.io.FileOutputStream(finalTarget).use { fos ->
                    val outChannel = fos.channel
                    java.io.FileInputStream(part1File).use { fis1 ->
                        fis1.channel.transferTo(0, fis1.channel.size(), outChannel)
                    }
                    java.io.FileInputStream(part2File).use { fis2 ->
                        fis2.channel.transferTo(0, fis2.channel.size(), outChannel)
                    }
                }
                part1File.delete()
                part2File.delete()
                Log.i(TAG, "🎉 Multipath merge complete: $baseName (${finalTarget.length()} bytes)")
                onFileReceived(baseName, finalTarget.length())
            } catch (e: Exception) {
                Log.e(TAG, "Failed merging multipath parts for $baseName: ${e.message}")
            }
        }
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
