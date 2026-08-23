package com.sharedash.app.model

import java.util.UUID

enum class TransportKind {
    USB,
    WIFI_DIRECT,
    LAN,
    BLUETOOTH
}

enum class ChunkState {
    PENDING,
    IN_FLIGHT,
    COMPLETED,
    CORRUPTED
}

data class DiscoveredPeer(
    val deviceId: String,
    val friendlyName: String,
    val osName: String,
    val ipAddress: String,
    val port: Int = 54321,
    val appVersion: String = "0.1.0",
    val isCompatible: Boolean = true,
    val supportedBridges: List<String> = listOf("Wi-Fi Direct", "LAN"),
    val rssi: Int = -50,
    val isBleDetected: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class ChunkInfo(
    val chunkId: Int,
    val fileIndex: Int,
    val offset: Long,
    val length: Int,
    val sha256: String,
    val blake3: String
)

data class FileMeta(
    val fileIndex: Int,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedTimestamp: Long,
    val chunkStartIndex: Int,
    val chunkCount: Int,
    val sha256Hash: String
)

data class TransferManifest(
    val transferId: UUID,
    val title: String,
    val totalBytes: Long,
    val totalFiles: Int,
    val chunkSize: Int,
    val totalChunks: Int,
    val rootHash: String,
    val files: List<FileMeta>,
    val chunks: List<ChunkInfo>
)

data class ChunkVisualItem(
    val chunkId: Int,
    val state: ChunkState,
    val transportName: String? = null
)

data class TransportStats(
    val name: String,
    val kind: TransportKind,
    val currentMbps: Double,
    val rttMs: Double,
    val completedChunks: Long,
    val isActive: Boolean = true
)

data class SchedulerTelemetry(
    val transferId: UUID,
    val title: String,
    val status: String,
    val aggregateMbps: Double,
    val totalBytes: Long,
    val completedBytes: Long,
    val progressPct: Float,
    val etaSeconds: Long,
    val transports: List<TransportStats>,
    val chunkStates: List<ChunkVisualItem>
)
