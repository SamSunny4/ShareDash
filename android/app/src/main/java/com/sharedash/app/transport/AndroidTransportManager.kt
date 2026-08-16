package com.sharedash.app.transport

import android.util.Log
import com.sharedash.app.model.Protocol
import com.sharedash.app.model.TransportKind
import com.sharedash.app.model.TransportStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class AndroidTransportManager {

    private val _transportStats = MutableStateFlow<List<TransportStats>>(emptyList())
    val transportStats: StateFlow<List<TransportStats>> = _transportStats.asStateFlow()

    private val activeSockets = mutableMapOf<String, Socket>()

    suspend fun connectTransport(
        name: String,
        kind: TransportKind,
        host: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 4 * 1024 * 1024
            socket.receiveBufferSize = 4 * 1024 * 1024
            socket.connect(InetSocketAddress(host, port), 3000)

            activeSockets[name] = socket
            updateStats(name, kind, currentMbps = 0.0, rttMs = 1.5, completedChunks = 0)
            Log.i(TAG, "Connected transport $name ($host:$port)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect transport $name ($host:$port): ${e.message}")
            false
        }
    }

    suspend fun sendFrame(transportName: String, frame: Protocol.Frame): Boolean = withContext(Dispatchers.IO) {
        val socket = activeSockets[transportName] ?: return@withContext false
        try {
            val bytes = Protocol.encodeFrame(frame)
            val os: OutputStream = socket.getOutputStream()
            os.write(bytes)
            os.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame on $transportName: ${e.message}")
            false
        }
    }

    suspend fun readExact(transportName: String, buffer: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val socket = activeSockets[transportName] ?: return@withContext false
        try {
            val `is`: InputStream = socket.getInputStream()
            var offset = 0
            while (offset < buffer.size) {
                val read = `is`.read(buffer, offset, buffer.size - offset)
                if (read == -1) return@withContext false
                offset += read
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from $transportName: ${e.message}")
            false
        }
    }

    private fun updateStats(
        name: String,
        kind: TransportKind,
        currentMbps: Double,
        rttMs: Double,
        completedChunks: Long
    ) {
        val list = _transportStats.value.toMutableList()
        val index = list.indexOfFirst { it.name == name }
        val newStat = TransportStats(name, kind, currentMbps, rttMs, completedChunks)
        if (index >= 0) {
            list[index] = newStat
        } else {
            list.add(newStat)
        }
        _transportStats.value = list
    }

    fun closeAll() {
        activeSockets.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }

    companion object {
        private const val TAG = "AndroidTransportMgr"
    }
}
