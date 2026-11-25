package com.sharedash.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sharedash.app.discovery.HotspotManager
import com.sharedash.app.discovery.HotspotState
import com.sharedash.app.discovery.UdpDiscoveryManager
import com.sharedash.app.model.DiscoveredPeer
import com.sharedash.app.model.Protocol
import com.sharedash.app.model.SchedulerTelemetry
import com.sharedash.app.model.TransportKind
import com.sharedash.app.model.TransportStats
import com.sharedash.app.service.TransferForegroundService
import com.sharedash.app.transport.AndroidTransportManager
import com.sharedash.app.ui.components.BottomPillBar
import com.sharedash.app.ui.components.NavTab
import com.sharedash.app.ui.screens.HomeScreen
import com.sharedash.app.ui.screens.SettingsScreen
import com.sharedash.app.ui.screens.TransferHistoryScreen
import com.sharedash.app.ui.screens.TransferScreen
import com.sharedash.app.storage.SettingsManager
import com.sharedash.app.storage.TransferHistoryManager
import com.sharedash.app.model.formatTransferSpeed
import com.sharedash.app.model.TransferRecord
import com.sharedash.app.model.TransferDirection
import com.sharedash.app.model.TransferStatus
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import com.sharedash.app.ui.theme.ShareDashTheme
import com.sharedash.app.ui.theme.NeoBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.io.InputStream
import java.util.UUID

data class TransportEndpoint(
    val ip: String,
    val port: Int,
    val name: String,
    val kind: TransportKind,
    val deviceId: String,
    val deviceName: String,
    val osName: String
)

private enum class PostResult {
    SUCCESS,
    NOT_FOUND,
    RETRY
}

object DeviceIdentity {
    val id = "android-" + Build.MODEL.replace(" ", "-") + "-" + UUID.randomUUID().toString().take(4)
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var historyManager: TransferHistoryManager
    private lateinit var udpManager: UdpDiscoveryManager
    private lateinit var hotspotManager: HotspotManager
    private var httpServer: com.sharedash.app.server.AndroidHttpServer? = null
    private val transportManager = AndroidTransportManager()
    @Volatile private var activeSendJob: kotlinx.coroutines.Job? = null

    private val _isUsbCablePlugged = MutableStateFlow(false)
    val isUsbCablePlugged: StateFlow<Boolean> = _isUsbCablePlugged

    private val _isUsbTetheringActive = MutableStateFlow(false)
    val isUsbTetheringActive: StateFlow<Boolean> = _isUsbTetheringActive

    private var pendingFiles by mutableStateOf(emptyList<Uri>())

    fun openDownloadsFolder() {
        try {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Files saved in Downloads/ShareDash", Toast.LENGTH_LONG).show()
        }
    }

    fun checkUsbState() {
        try {
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val hasUsbDev = try {
                val usbManager = getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
                (usbManager?.deviceList?.isNotEmpty() == true) || (usbManager?.accessoryList?.isNotEmpty() == true)
            } catch (_: Exception) { false }

            val hasUsbNet = hasUsbNetworkInterface()
            val isPlugged = plugged == android.os.BatteryManager.BATTERY_PLUGGED_USB || 
                            hasUsbDev ||
                            hasUsbNet
            _isUsbCablePlugged.value = isPlugged

            _isUsbTetheringActive.value = hasUsbNet
        } catch (_: Exception) {}
    }

    private fun hasUsbNetworkInterface(): Boolean {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                val isUsbName = name.contains("rndis") || name.contains("usb") || name.contains("ncm") || name.contains("geth") || (name.contains("eth") && !name.contains("wlan"))
                if (iface.isUp && isUsbName) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Start discovery now that permissions dialog was handled
        startDiscovery()
    }

    private var onFilesPickedCallback: ((List<Uri>) -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesPickedCallback?.invoke(uris)
        }
    }

    private val filteredPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesPickedCallback?.invoke(uris)
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            lifecycleScope.launch(Dispatchers.IO) {
                val uris = collectFilesFromTree(treeUri)
                withContext(Dispatchers.Main) {
                    if (uris.isNotEmpty()) {
                        onFilesPickedCallback?.invoke(uris)
                    } else {
                        Toast.makeText(this@MainActivity, "No files found in selected folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun collectFilesFromTree(treeUri: Uri): List<Uri> {
        val fileUris = mutableListOf<Uri>()
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return emptyList()
        }

        fun traverse(parentDocId: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx)
                    val mime = cursor.getString(mimeIdx)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverse(childId)
                    } else {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        fileUris.add(fileUri)
                    }
                }
            }
        }

        try {
            traverse(docId)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed traversing directory tree: ${e.message}")
        }
        return fileUris
    }

    private var onIncomingPairCallback: ((initiatorId: String, initiatorName: String, initiatorIp: String, pin: String, appVer: String) -> Unit)? = null
    private var onPairAcceptedCallback: ((targetId: String, targetName: String) -> Unit)? = null
    private var onPairConfirmedCallback: (() -> Unit)? = null
    private var onUsbConnectedCallback: (() -> Unit)? = null
    private var onTransferProgressCallback: ((String, Long, Long, Double, Long?) -> Unit)? = null
    private var onFileReceivedCallback: ((String, Long) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager.getInstance(this)
        historyManager = TransferHistoryManager.getInstance(this)

        hotspotManager = HotspotManager(this)
        udpManager = UdpDiscoveryManager(this,
            directHostIp = { (hotspotManager.hotspotState.value as? HotspotState.Active)?.ipAddress },
            onDirectHost = { ip ->
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = currentWifi()
                if (network != null && cm.getLinkProperties(network)?.routes.orEmpty().any { it.gateway?.hostAddress == ip }) {
                    directClientWifi = network
                }
            },
            transferBusy = { activeSendJob?.isActive == true || TransferForegroundService.isServiceRunning },
            allowedPeerIp = { ip -> externalWifiRoutes().none { it.matches(java.net.InetAddress.getByName(ip)) } }
        )

        // Eagerly start Android HTTP Server on port 54321 for symmetric discovery and pairing
        httpServer = com.sharedash.app.server.AndroidHttpServer(
            context = this,
            port = 54321,
            onIncomingPairRequest = { id, name, ip, pin, ver -> onIncomingPairCallback?.invoke(id, name, ip, pin, ver) },
            onPairAccepted = { id, name -> onPairAcceptedCallback?.invoke(id, name) },
            onPairConfirmed = { onPairConfirmedCallback?.invoke() },
            onFileReceived = { fileName, bytes -> onFileReceivedCallback?.invoke(fileName, bytes) }
        )
        httpServer?.directHostIp = { (hotspotManager.hotspotState.value as? HotspotState.Active)?.ipAddress }
        httpServer?.isAllowedInterface = { local ->
            local.isLoopbackAddress || externalWifiRoutes().none { it.matches(local) }
        }
        var lastNotificationTimeMs = 0L
        httpServer?.onTransferProgress = { fileName, bytesRecv, totalBytes, speedMbps, usbBytes ->
            val now = System.currentTimeMillis()
            if (now - lastNotificationTimeMs >= 300L || (totalBytes > 0 && bytesRecv >= totalBytes)) {
                lastNotificationTimeMs = now
                val pct = if (totalBytes > 0) ((bytesRecv * 100) / totalBytes).toInt() else 0
                val speedText = "${formatTransferSpeed(speedMbps)} · $pct%"
                com.sharedash.app.service.TransferForegroundService.updateProgress(
                    this@MainActivity, fileName, pct, speedText
                )
            }
            onTransferProgressCallback?.invoke(fileName, bytesRecv, totalBytes, speedMbps, usbBytes)
        }
        httpServer?.onWifiConnectRequest = { ssid, password ->
            connectToWifiNetwork(ssid, password)
        }
        httpServer?.onStartHotspotRequest = { callback ->
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val result = kotlinx.coroutines.withTimeoutOrNull(90_000L) {
                        val choice = kotlinx.coroutines.CompletableDeferred<Boolean>()
                        wirelessDecision = choice
                        prepareWireless()
                        if (!choice.await()) return@withTimeoutOrNull null
                        hotspotManager.hotspotState.first { it is HotspotState.Active || it is HotspotState.Error }
                    }
                    when (result) {
                        is HotspotState.Active -> callback("hotspot_started", result.ssid, result.password, result.ipAddress)
                        is HotspotState.Error -> callback("failed", "", "", "")
                        else -> callback("usb_only", "", "", "")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in onStartHotspotRequest: ${e.message}")
                    callback("failed", "", "", "")
                }
            }
        }
        httpServer?.start(lifecycleScope)

        handleIntent(intent)
        checkAndRequestPermissions()
        checkUsbState()

        val usbFilter = android.content.IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(this, usbReceiver, usbFilter, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
            try {
                registerReceiver(usbReceiver, usbFilter)
            } catch (_: Exception) {}
        }

        val wifiConnectFilter = android.content.IntentFilter("com.sharedash.app.WIFI_CONNECT")
        try {
            ContextCompat.registerReceiver(this, wifiConnectReceiver, wifiConnectFilter, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
            try {
                registerReceiver(wifiConnectReceiver, wifiConnectFilter)
            } catch (_: Exception) {}
        }

        setContent {
            val themeModeState by settingsManager.themeModeFlow.collectAsState()
            ShareDashTheme(themeMode = themeModeState) {
                var currentNavTab by remember { mutableStateOf(NavTab.HOME) }
                var activeTarget by remember { mutableStateOf<DiscoveredPeer?>(null) }
                var activeTelemetry by remember { mutableStateOf<SchedulerTelemetry?>(null) }

                val hasActiveTransfer = activeTelemetry != null &&
                    activeTelemetry?.status != "COMPLETED" &&
                    activeTelemetry?.status != "FAILED" &&
                    activeTelemetry?.status != "CANCELLED"

                val isUsbCablePluggedState by isUsbCablePlugged.collectAsState()
                val isUsbTetheringActiveState by isUsbTetheringActive.collectAsState()
                val deviceNameState by settingsManager.deviceNameFlow.collectAsState()
                val connectionModeState by settingsManager.connectionModeFlow.collectAsState()
                val preferUsbState by settingsManager.preferUsbFlow.collectAsState()
                val prefer5GHzState by settingsManager.prefer5GHzFlow.collectAsState()
                val serverPortState by settingsManager.serverPortFlow.collectAsState()
                val historyRecordsState by historyManager.historyFlow.collectAsState()

                androidx.activity.compose.BackHandler(enabled = currentNavTab != NavTab.HOME && pendingFiles.isEmpty()) {
                    currentNavTab = NavTab.HOME
                }

                val pairingCoordinator = remember { com.sharedash.app.discovery.AndroidPairingCoordinator(::openPeerConnection) }

                val udpPeers by udpManager.discoveredPeers.collectAsState()
                val hotspotState by hotspotManager.hotspotState.collectAsState()

                // Auto-enter connected state when USB is detected
                onUsbConnectedCallback = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (activeTarget == null && settingsManager.preferUsb &&
                            connectionModeState == SettingsManager.MODE_PHONE_TO_PC) {
                            val endpoint = resolveMultipathEndpoints(DiscoveredPeer(
                                deviceId = "usb-pc", friendlyName = "PC", osName = "Windows",
                                ipAddress = "192.168.42.1", port = 54321
                            )).firstOrNull { it.kind == TransportKind.USB }
                            if (endpoint != null && activeTarget == null && settingsManager.preferUsb &&
                                settingsManager.connectionMode == SettingsManager.MODE_PHONE_TO_PC) {
                                activeTarget = DiscoveredPeer(endpoint.deviceId, endpoint.deviceName, endpoint.osName,
                                    ipAddress = endpoint.ip, port = endpoint.port)
                            }
                        }
                    }
                }

                onIncomingPairCallback = { initiatorId, initiatorName, initiatorIp, pin, appVer ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (hasActiveTransfer && activeTarget?.deviceId != initiatorId) {
                            pairingCoordinator.respondToPairRequest(initiatorIp, 54321, false)
                            return@launch
                        }
                        httpServer?.activePairedPeerName = initiatorName
                        httpServer?.pendingPairRequest = null
                        pairingCoordinator.respondToPairRequest(initiatorIp, 54321, true)

                        val isInitiatorPc = !initiatorId.startsWith("android-")
                        activeTarget = DiscoveredPeer(
                            deviceId = initiatorId,
                            friendlyName = initiatorName,
                            osName = if (isInitiatorPc) "Windows" else "Android",
                            ipAddress = initiatorIp,
                            port = 54321,
                            appVersion = appVer
                        )
                        Toast.makeText(this@MainActivity, "Connected to $initiatorName", Toast.LENGTH_SHORT).show()
                    }
                }

                onPairAcceptedCallback = { targetId, targetName ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Connected to $targetName", Toast.LENGTH_SHORT).show()
                    }
                }

                onPairConfirmedCallback = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Device connected", Toast.LENGTH_SHORT).show()
                    }
                }

                val receiving = remember { mutableMapOf<String, SchedulerTelemetry>() }
                onTransferProgressCallback = { fileName, bytesRecv, totalBytes, speedMbps, usbBytes ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val previous = receiving[fileName]
                        val peerName = activeTarget?.friendlyName.orEmpty()
                        val telem = SchedulerTelemetry(
                            transferId = previous?.transferId ?: UUID.randomUUID(), title = fileName,
                            status = if (totalBytes > 0 && bytesRecv >= totalBytes) "VERIFYING" else "ACTIVE",
                            usbBytes = usbBytes, aggregateMbps = speedMbps, totalBytes = totalBytes, completedBytes = bytesRecv,
                            progressPct = if (totalBytes > 0) (bytesRecv.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
                            etaSeconds = if (speedMbps > 0) ((totalBytes - bytesRecv).coerceAtLeast(0) * 8 / (speedMbps * 1_000_000)).toLong() else 0,
                            transports = emptyList(), chunkStates = emptyList(),
                            direction = TransferDirection.RECEIVED, peerName = peerName
                        )
                        receiving[fileName] = telem
                        if (activeSendJob?.isActive != true) activeTelemetry = telem
                    }
                }

                onFileReceivedCallback = { fileName, bytes ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val last = receiving.remove(fileName)
                        val peerName = last?.peerName?.ifBlank { null } ?: activeTarget?.friendlyName ?: "Nearby device"
                        val completed = SchedulerTelemetry(
                            transferId = last?.transferId ?: UUID.randomUUID(), title = fileName,
                            status = "COMPLETED", aggregateMbps = last?.aggregateMbps ?: 0.0,
                            totalBytes = bytes, completedBytes = bytes, progressPct = 1f, etaSeconds = 0,
                            transports = emptyList(), chunkStates = emptyList(),
                            direction = TransferDirection.RECEIVED, peerName = peerName
                        )
                        if (activeSendJob?.isActive != true) {
                            activeTelemetry = receiving.values.lastOrNull() ?: completed
                            if (receiving.isEmpty()) TransferForegroundService.complete(this@MainActivity, fileName)
                        }
                        historyManager.addRecord(TransferRecord(
                            fileName = fileName, fileSize = bytes, direction = TransferDirection.RECEIVED,
                            status = TransferStatus.COMPLETED, peerName = peerName, transportUsed = "",
                            speedMbps = completed.aggregateMbps, filePath = java.io.File(
                                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                "ShareDash/$fileName"
                            ).absolutePath
                        ))
                    }
                }

                // Discovery peers from UDP (Wi-Fi Direct / Zero-LAN / Hotspot / USB)
                val phoneMode = connectionModeState == SettingsManager.MODE_PHONE_TO_PHONE
                val combinedPeers = udpPeers.filter { it.osName.startsWith("Android", true) == phoneMode }

                fun startSending(peer: DiscoveredPeer, uris: List<Uri>) {
                    if (uris.isEmpty() || activeSendJob?.isActive == true) return
                    pendingFiles = emptyList()
                    val firstFileName = queryFileName(uris[0]) ?: "file.bin"
                    val totalFiles = uris.size
                    val initialTelem = SchedulerTelemetry(
                        transferId = UUID.randomUUID(),
                        title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                        status = "ACTIVE",
                        aggregateMbps = 0.0,
                        totalBytes = 0L,
                        completedBytes = 0L,
                        progressPct = 0f,
                        etaSeconds = 0L,
                        transports = emptyList(), chunkStates = emptyList(), peerName = peer.friendlyName
                    )
                    activeTelemetry = initialTelem
                    currentNavTab = NavTab.TRANSFER
                    executeRealTransfer(peer, uris) { telem ->
                        activeTelemetry = telem.copy(peerName = peer.friendlyName)
                    }
                }

                onFilesPickedCallback = { uris ->
                    val peer = activeTarget
                    if (hasActiveTransfer) {
                        Toast.makeText(this, "Finish the current transfer first", Toast.LENGTH_SHORT).show()
                    } else if (peer != null) {
                        startSending(peer, uris)
                    } else {
                        pendingFiles = uris
                        startDiscovery()
                    }
                }

                // Heartbeat to detect when PC ShareDash is shutdown
                LaunchedEffect(activeTarget) {
                    if (activeTarget != null) {
                        var consecutiveFailures = 0
                        while (isActive && activeTarget != null) {
                            delay(3000)
                            if (activeSendJob?.isActive == true) continue
                            val targetPeer = activeTarget ?: break
                            val ep = resolveBestTargetEndpoint(targetPeer)
                            if (ep == null) {
                                consecutiveFailures++
                                if (consecutiveFailures >= 2) {
                                    val peerName = targetPeer.friendlyName.ifEmpty { "Remote device" }
                                    Toast.makeText(this@MainActivity, "$peerName disconnected or shutdown", Toast.LENGTH_SHORT).show()
                                    activeTarget = null
                                    break
                                }
                            } else {
                                consecutiveFailures = 0
                            }
                        }
                    }
                }

                var connectingId by remember { mutableStateOf<String?>(null) }
                val handleDeviceSelect: (DiscoveredPeer) -> Unit = select@{ peer ->
                    if (connectingId != null || hasActiveTransfer) return@select
                    if (!peer.isCompatible) {
                        Toast.makeText(
                            this@MainActivity,
                            "Incompatible App Version: ${peer.friendlyName} is running v${peer.appVersion}. Please update both apps.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val pin = String.format("%06d", java.util.Random().nextInt(900000) + 100000)
                        val myIp = udpManager.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
                        connectingId = peer.deviceId
                        lifecycleScope.launch {
                            val paths = resolveMultipathEndpoints(peer)
                            if (paths.isEmpty()) {
                                prepareWireless()
                                return@launch
                            }
                            var sent = false
                            var connectedIp = paths.first().ip
                            var connectedPort = paths.first().port
                            for (path in paths) {
                                val candIp = path.ip
                                val portToTry = path.port
                                if (pairingCoordinator.sendPairRequest(candIp, portToTry, pin, settingsManager.deviceName, myIp)) {
                                    sent = true
                                    connectedIp = candIp
                                    connectedPort = portToTry
                                    break
                                }
                            }

                            if (sent && pairingCoordinator.awaitAcceptance(connectedIp, connectedPort, settingsManager.deviceName)) {
                                val connected = peer.copy(ipAddress = connectedIp, port = connectedPort)
                                activeTarget = connected
                                if (pendingFiles.isNotEmpty() && !hasActiveTransfer) startSending(connected, pendingFiles)
                            } else if (sent) {
                                Toast.makeText(this@MainActivity, "Connection request was not accepted.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Could not reach ${peer.friendlyName}. Open ShareDash on the other device and check its connection.", Toast.LENGTH_LONG).show()
                            }
                        }.invokeOnCompletion { connectingId = null }
                    }
                }

                val handleDisconnect: () -> Unit = {
                    httpServer?.activePairedPeerName = null
                    activeTarget?.let { peer ->
                        lifecycleScope.launch {
                            pairingCoordinator.respondToPairRequest(peer.ipAddress, peer.port, false)
                        }
                    }
                    activeTarget = null
                    pendingFiles = emptyList()
                }

                val handleCancelTransfer: () -> Unit = {
                    activeTelemetry?.let { telem ->
                        historyManager.addRecord(
                            TransferRecord(
                                fileName = telem.title.ifEmpty { "Transfer" },
                                fileSize = telem.totalBytes,
                                direction = telem.direction,
                                status = TransferStatus.CANCELLED,
                                peerName = activeTarget?.friendlyName ?: "ShareDash PC",
                                transportUsed = "USB / Wi-Fi",
                                speedMbps = telem.aggregateMbps
                            )
                        )
                    }

                    receiving.clear()
                    activeSendJob?.cancel()
                    activeSendJob = null
                    httpServer?.cancelActiveTransfers()
                    TransferForegroundService.stopService(this@MainActivity)
                    transportManager.closeAll()

                    val candidateHosts = mutableListOf<Pair<String, Int>>()
                    activeTarget?.let { peer ->
                        if (peer.ipAddress.isNotBlank()) {
                            candidateHosts.add(peer.ipAddress to peer.port)
                        }
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        for ((host, port) in candidateHosts) {
                            try {
                                val url = java.net.URL("http://$host:$port/api/v1/transfers/cancel")
                                val conn = openPeerConnection(url)
                                conn.requestMethod = "POST"
                                conn.connectTimeout = 800
                                conn.readTimeout = 800
                                conn.responseCode
                                conn.disconnect()
                            } catch (_: Exception) {}
                        }
                    }

                    activeTelemetry = null
                    currentNavTab = NavTab.HOME
                    Toast.makeText(this@MainActivity, "Transfer stopped", Toast.LENGTH_SHORT).show()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NeoBg)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    when (currentNavTab) {
                        NavTab.HOME -> {
                            HomeScreen(
                                connectionMode = connectionModeState,
                                onConnectionModeChange = { activeTarget = null; settingsManager.connectionMode = it },
                                isUsbCablePlugged = isUsbCablePluggedState,
                                isUsbTetheringActive = isUsbTetheringActiveState,
                                connectedPeer = activeTarget,
                                discoveredPeers = combinedPeers,
                                onEnableUsbTethering = { openUsbTetheringSettings() },
                                onSkipUsb = { prepareWireless() },
                                onDeviceSelected = handleDeviceSelect,
                                connectingId = connectingId,
                                onDisconnect = { if (!hasActiveTransfer) handleDisconnect() else Toast.makeText(this@MainActivity, "Finish the current transfer first", Toast.LENGTH_SHORT).show() },
                                onPickFiles = { filePickerLauncher.launch("*/*") },
                                onPickFolder = { folderPickerLauncher.launch(null) },
                                onOpenDownloadsFolder = { openDownloadsFolder() },
                                hotspotState = hotspotState,
                                onStartHotspot = { prepareWireless() },
                                onStopHotspot = { hotspotManager.stopHotspot() },
                                deviceName = deviceNameState,
                                hasActiveTransfer = hasActiveTransfer
                            )
                        }
                        NavTab.TRANSFER -> {
                            TransferScreen(
                                targetName = activeTarget?.friendlyName.orEmpty(),
                                telemetry = activeTelemetry,
                                onCancel = handleCancelTransfer,
                                onFinish = {
                                    pendingFiles = emptyList()
                                    activeTelemetry = null
                                    httpServer?.resetCancellation()
                                    currentNavTab = NavTab.HOME
                                },
                                onSendAnother = {
                                    pendingFiles = emptyList()
                                    filePickerLauncher.launch("*/*")
                                },
                                onPickFiles = { filePickerLauncher.launch("*/*") },
                                onPickFolder = { folderPickerLauncher.launch(null) },
                                onPickGallery = { filteredPicker.launch(arrayOf("image/*", "video/*")) },
                                onPickDocuments = { filteredPicker.launch(arrayOf("application/*", "text/*")) },
                                onNavigateToHistory = { currentNavTab = NavTab.HISTORY }
                            )
                        }
                        NavTab.HISTORY -> {
                            TransferHistoryScreen(
                                records = historyRecordsState,
                                onClearAll = { historyManager.clearAll() },
                                onDeleteRecord = { id -> historyManager.deleteRecord(id) },
                                onOpenDownloadsFolder = { openDownloadsFolder() }
                            )
                        }
                        NavTab.SETTINGS -> {
                            SettingsScreen(
                                deviceName = deviceNameState,
                                onDeviceNameChange = { settingsManager.deviceName = it },
                                connectionMode = connectionModeState,
                                onConnectionModeChange = { activeTarget = null; settingsManager.connectionMode = it },
                                preferUsb = preferUsbState,
                                onPreferUsbChange = { settingsManager.preferUsb = it },
                                prefer5GHz = prefer5GHzState,
                                onPrefer5GHzChange = { settingsManager.prefer5GHz = it },
                                serverPort = serverPortState,
                                localIpAddresses = udpManager.getLocalIpAddresses(),
                                isUsbCablePlugged = isUsbCablePluggedState,
                                isUsbTetheringActive = isUsbTetheringActiveState,
                                onOpenDownloadsFolder = { openDownloadsFolder() },
                                onOpenTetheringSettings = { openUsbTetheringSettings() },
                                themeMode = themeModeState,
                                onThemeModeChange = { settingsManager.themeMode = it },
                                hasActiveTransfer = hasActiveTransfer
                            )
                        }
                    }

                    // Persistent Floating Active Transfer Bar (accessible from any screen except Transfer tab)
                    activeTelemetry?.let { telem ->
                        if (currentNavTab != NavTab.TRANSFER && telem.status != "CANCELLED") {
                            // Auto-dismiss completed/failed banner after 3 seconds
                            val isFinished = telem.status == "COMPLETED" || telem.status == "FAILED"
                            LaunchedEffect(telem.transferId, isFinished) {
                                if (isFinished) {
                                    delay(3000)
                                    activeTelemetry = null
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                                    .fillMaxWidth()
                            ) {
                                com.sharedash.app.ui.theme.NeoCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentNavTab = NavTab.TRANSFER },
                                    cornerRadius = 18.dp,
                                    elevation = 10.dp,
                                    backgroundColor = com.sharedash.app.ui.theme.NeoCard
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (telem.status == "COMPLETED") com.sharedash.app.ui.theme.NeoGreen.copy(alpha = 0.2f)
                                                        else if (telem.status == "FAILED") com.sharedash.app.ui.theme.NeoRed.copy(alpha = 0.2f)
                                                        else com.sharedash.app.ui.theme.NeoCyan.copy(alpha = 0.2f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = if (telem.status == "COMPLETED") Icons.Default.Check
                                                    else if (telem.status == "FAILED") Icons.Default.Close
                                                    else Icons.Default.Sync,
                                                    contentDescription = null,
                                                    tint = if (telem.status == "COMPLETED") com.sharedash.app.ui.theme.NeoGreen
                                                    else if (telem.status == "FAILED") com.sharedash.app.ui.theme.NeoRed
                                                    else com.sharedash.app.ui.theme.NeoCyan,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                androidx.compose.material3.Text(
                                                    text = telem.title.ifEmpty { "Active Transfer" },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = com.sharedash.app.ui.theme.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                androidx.compose.material3.Text(
                                                    text = if (telem.status == "COMPLETED") "Transfer Complete · Tap to view"
                                                    else if (telem.status == "FAILED") "Transfer Failed · Tap to view"
                                                    else "${formatTransferSpeed(telem.aggregateMbps)} · ${(telem.progressPct * 100).toInt()}%",
                                                    fontSize = 11.sp,
                                                    color = com.sharedash.app.ui.theme.TextSecondary
                                                )
                                            }
                                        }

                                        androidx.compose.material3.Text(
                                            text = "VIEW",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = com.sharedash.app.ui.theme.NeoCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating Bottom Pill Navigation Bar
                    BottomPillBar(
                        currentTab = currentNavTab,
                        onTabSelected = { currentNavTab = it },
                        hasActiveTransfer = hasActiveTransfer,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )



                    if (pendingFiles.isNotEmpty() && !hasActiveTransfer) {
                        val peers = (listOfNotNull(activeTarget) + udpPeers).distinctBy { it.deviceId }
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { pendingFiles = emptyList() },
                            title = { androidx.compose.material3.Text("Choose a recipient") },
                            text = {
                                androidx.compose.foundation.lazy.LazyColumn {
                                    item { androidx.compose.material3.Text(if (peers.isEmpty())
                                        "Open ShareDash on the other device and connect using USB tethering or Wi-Fi Direct."
                                        else if (connectingId != null) "Connecting…" else "Select the device that should receive your files.") }
                                    items(peers.size) { index ->
                                        val peer = peers[index]
                                        androidx.compose.material3.TextButton(enabled = connectingId == null, onClick = {
                                            if (peer.deviceId == activeTarget?.deviceId) startSending(peer, pendingFiles)
                                            else handleDeviceSelect(peer)
                                        }) { androidx.compose.material3.Text(peer.friendlyName + " · " + peer.ipAddress) }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingFiles = emptyList() }) {
                                androidx.compose.material3.Text("Cancel")
                            } }
                        )
                    }

                }
            }
        }
    }

    private var wirelessDecision: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    @Volatile private var allowedWifi: android.net.Network? = null
    private var resumeWirelessSetup = false
    private var wifiDialog: android.app.AlertDialog? = null

    private fun currentWifi(): android.net.Network? {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun externalWifiRoutes(): List<android.net.RouteInfo> {
        val network = currentWifi() ?: return emptyList()
        val ssid = hotspotManager.getConnectedWifiSsid().orEmpty()
        if (ssid.startsWith("DIRECT-", true) || ssid.startsWith("ShareDash", true) || network == fallbackWifi || network == directClientWifi) return emptyList()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.getLinkProperties(network)?.routes.orEmpty().filter { !it.isDefaultRoute && (it.gateway == null || it.gateway?.isAnyLocalAddress == true) }
    }

    @Volatile private var directClientWifi: android.net.Network? = null
    @Volatile private var fallbackWifi: android.net.Network? = null

    private fun openPeerConnection(url: java.net.URL): java.net.HttpURLConnection {
        val network = fallbackWifi
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val address = java.net.InetAddress.getByName(url.host)
        val useFallback = network != null && !address.isLoopbackAddress &&
            cm.getLinkProperties(network)?.routes.orEmpty().any { !it.isDefaultRoute && it.matches(address) }
        return (if (useFallback) network!!.openConnection(url) else url.openConnection()) as java.net.HttpURLConnection
    }

    private fun prepareWireless() {
        if (wifiDialog?.isShowing == true) return
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        val network = currentWifi()
        fun startDirect() {
            hotspotManager.start5GHzHotspot { _, _, _ -> startDiscovery() }
            wirelessDecision?.complete(true)
        }
        if (wifi.isWifiEnabled && (hotspotManager.hotspotState.value is HotspotState.Active ||
                network == null || !hotspotManager.isConnectedToExternalWifi() || allowedWifi == network)) {
            startDirect()
            return
        }
        val builder = android.app.AlertDialog.Builder(this)
        if (!wifi.isWifiEnabled) {
            builder.setTitle("Turn on Wi-Fi")
                .setMessage("Wi-Fi Direct needs the Wi-Fi radio. You can leave it disconnected from your router.")
        } else {
            builder.setTitle("Switch to Wi-Fi Direct?")
                .setMessage("Sharing the radio with this network can slow transfers. Turn off auto-reconnect for this network, then disconnect. Leave Wi-Fi on.")
                .setNeutralButton("Continue on this Wi-Fi") { _, _ ->
                    allowedWifi = network
                    startDirect()
                }
        }
        wifiDialog = builder.setPositiveButton("Wi-Fi settings") { _, _ ->
            resumeWirelessSetup = true
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }.setNegativeButton("Cancel") { _, _ -> wirelessDecision?.complete(false) }
            .setOnCancelListener { wirelessDecision?.complete(false) }.show()
    }

    private suspend fun resolveMultipathEndpoints(target: DiscoveredPeer): List<TransportEndpoint> = withContext(Dispatchers.IO) {
        val verified = mutableListOf<TransportEndpoint>()
        val autoUsbPc = target.deviceId == "usb-pc"
        val seenIps = mutableSetOf<String>()

        val initialCandidates = mutableListOf<Pair<String, Int>>()

        // 1. Known Target IP & Disclosed Candidate IPs from discovery / pairing
        if (target.ipAddress.isNotEmpty() && target.ipAddress != "127.0.0.1" && target.ipAddress != "0.0.0.0") {
            initialCandidates.add(target.ipAddress to target.port)
            initialCandidates.add(target.ipAddress to 54321)
        }
        for (cand in target.candidateIps) {
            if (cand.isNotEmpty() && cand != "127.0.0.1" && cand != "0.0.0.0") {
                initialCandidates.add(cand to target.port)
                initialCandidates.add(cand to 54321)
            }
        }

        // 2. Direct Wi-Fi Hotspot Gateways
        initialCandidates.add("192.168.137.1" to 54321) // PC 5GHz Hotspot Gateway
        initialCandidates.add("192.168.49.1" to 54321)  // Wi-Fi Direct Autonomous Group
        initialCandidates.add("192.168.43.1" to 54321)  // Phone Hotspot Gateway

        // 3. USB Tethering Subnets (inspect all USB/RNDIS/NCM network interfaces)
        val (detectedUsbPrefixes, _) = udpManager.getInterfaceSubnetPrefixes()
        val usbSubnetPrefixes = detectedUsbPrefixes.toMutableSet()

        // Read kernel ARP table for active USB tethering peers
        try {
            java.io.File("/proc/net/arp").forEachLine { line ->
                val tokens = line.split(Regex("\\s+"))
                if (tokens.size >= 6 && tokens[3] != "00:00:00:00:00:00") {
                    val arpIp = tokens[0]
                    val dev = tokens[5].lowercase()
                    if (dev.contains("rndis") || dev.contains("usb") || dev.contains("ncm") || dev.contains("eth")) {
                        initialCandidates.add(arpIp to target.port)
                        initialCandidates.add(arpIp to 54321)
                    }
                }
            }
        } catch (_: Exception) {}

        for (prefix in usbSubnetPrefixes) {
            // Android USB Tethering DHCP server assigns PC client IP starting at .2, or .142 on Samsung One UI
            val offsets = listOf("1", "2", "142", "129", "100", "254", "3", "4")
            for (off in offsets) {
                initialCandidates.add("$prefix.$off" to target.port)
                initialCandidates.add("$prefix.$off" to 54321)
            }
        }

        // 4. USB ADB Reverse port 54325 (PC USB fast path)
        initialCandidates.add("127.0.0.1" to 54325)

        val checkIsUsb: (String, Int) -> Boolean = { candIp, candPort ->
            (candIp == "127.0.0.1" && candPort == 54325) ||
                usbSubnetPrefixes.any { candIp.startsWith("$it.") }
        }

        // Probe all initial candidate endpoints concurrently in parallel (1500ms timeout)
        val stationRoutes = externalWifiRoutes()
        fun routeAllowed(ip: String) = runCatching { stationRoutes.none { it.matches(java.net.InetAddress.getByName(ip)) } }.getOrDefault(false)
        val candidateList = initialCandidates.distinct().filter { (ip, port) ->
            (port != 54325 || ip == "127.0.0.1") && routeAllowed(ip)
        }
        val probeDeferreds = candidateList.map { (ip, port) ->
            async(Dispatchers.IO) {
                try {
                    val probeUrl = java.net.URL("http://$ip:$port/api/v1/info")
                    val conn = openPeerConnection(probeUrl)
                    conn.connectTimeout = 1500
                    conn.readTimeout = 2000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        val json = org.json.JSONObject(body)
                        val devId = json.optString("device_id", "")

                        // Loopback check: Prevent self-transfer, but allow remote Android phones and PCs!
                        val os = json.optString("os_name")
                        if (devId.isBlank() || devId == DeviceIdentity.id ||
                            (autoUsbPc && (os.startsWith("Android", true) || !checkIsUsb(ip, port))) ||
                            (!autoUsbPc && devId != target.deviceId)) {
                            return@async null
                        }

                        val isUsb = checkIsUsb(ip, port)
                        val name = if (isUsb) "USB Cable" else "Wi-Fi"
                        val kind = if (isUsb) TransportKind.USB else TransportKind.LAN

                        val peerLocalIps = mutableListOf<String>()
                        val jsonIps = json.optJSONArray("local_ips")
                        if (jsonIps != null) {
                            for (i in 0 until jsonIps.length()) {
                                val peerIp = jsonIps.optString(i, "")
                                if (peerIp.isNotEmpty() && peerIp != "127.0.0.1" && peerIp != "0.0.0.0") {
                                    peerLocalIps.add(peerIp)
                                }
                            }
                        }
                        Triple(TransportEndpoint(ip, port, name, kind, devId, json.optString("device_name", target.friendlyName), os), peerLocalIps, ip)
                    } else {
                        conn.disconnect()
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        val initialResults = probeDeferreds.mapNotNull { it.await() }
        val secondaryCandidateIps = mutableListOf<String>()
        for ((endpoint, peerIps, ip) in initialResults) {
            if (seenIps.add(ip)) {
                verified.add(endpoint)
                secondaryCandidateIps.addAll(peerIps)
            }
        }

        // Secondary parallel probes for additional interface IPs disclosed by peer
        val secondaryFiltered = secondaryCandidateIps.distinct().filter { !seenIps.contains(it) && routeAllowed(it) }
        val secondaryDeferreds = secondaryFiltered.map { peerIp ->
            async(Dispatchers.IO) {
                try {
                    val probeUrl = java.net.URL("http://$peerIp:54321/api/v1/info")
                    val conn = openPeerConnection(probeUrl)
                    conn.connectTimeout = 1500
                    conn.readTimeout = 2000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        val json = org.json.JSONObject(body)
                        val devId = json.optString("device_id", "")
                        if (devId != verified.firstOrNull()?.deviceId) return@async null

                        val isUsb = checkIsUsb(peerIp, 54321)
                        val name = if (isUsb) "USB Cable" else "Wi-Fi"
                        val kind = if (isUsb) TransportKind.USB else TransportKind.LAN
                        TransportEndpoint(peerIp, 54321, name, kind, devId, json.optString("device_name"), json.optString("os_name"))
                    } else {
                        conn.disconnect()
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        val secondaryResults = secondaryDeferreds.mapNotNull { it.await() }
        for (ep in secondaryResults) {
            if (seenIps.add(ep.ip)) {
                verified.add(ep)
            }
        }

        // Prioritize hardware USB NIC (RNDIS / Tethering, e.g. 192.168.42.x, ~35-42 MB/s)
        // over ADB reverse loopback (127.0.0.1:54325, ~20 MB/s user-space proxy)
        verified.sortWith(compareBy<TransportEndpoint> {
            if (!settingsManager.preferUsb && it.kind != TransportKind.USB) -1
            else if (it.kind == TransportKind.USB && it.ip != "127.0.0.1") 0
            else if (it.kind == TransportKind.USB) 1
            else 2
        })

        Log.i("MainActivity", "Multipath endpoints resolved: ${verified.map { "${it.name} (${it.ip}:${it.port})" }}")
        verified
    }

    private suspend fun resolveBestTargetEndpoint(target: DiscoveredPeer): Pair<String, Int>? {
        val endpoints = resolveMultipathEndpoints(target)
        val best = endpoints.firstOrNull()
        return best?.let { it.ip to it.port }
    }

    private fun readChunkBytes(uri: Uri, offset: Long, length: Int): ByteArray? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { channel ->
                    channel.position(offset)
                    val buf = ByteArray(length)
                    val buffer = java.nio.ByteBuffer.wrap(buf)
                    var bytesRead = 0
                    while (bytesRead < length) {
                        val read = channel.read(buffer)
                        if (read <= 0) break
                        bytesRead += read
                    }
                    if (bytesRead == length) {
                        buf
                    } else if (bytesRead > 0) {
                        buf.copyOf(bytesRead)
                    } else {
                        ByteArray(0)
                    }
                }
            }
        } catch (t: Throwable) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    var skipped = 0L
                    while (skipped < offset) {
                        val s = stream.skip(offset - skipped)
                        if (s > 0) skipped += s else if (stream.read() >= 0) skipped++ else return null
                    }
                    val buf = ByteArray(length)
                    var total = 0
                    while (total < length) {
                        val r = stream.read(buf, total, length - total)
                        if (r <= 0) break
                        total += r
                    }
                    if (total == length) buf else if (total > 0) buf.copyOf(total) else ByteArray(0)
                }
            } catch (inner: Throwable) {
                Log.e("MainActivity", "readChunkBytes failed: ${inner.message}")
                null
            }
        }
    }

    private fun postChunkDetailed(
        endpoint: TransportEndpoint,
        transferId: String,
        fileName: String,
        fileSize: Long,
        chunkId: Int,
        chunkOffset: Long,
        totalChunks: Int,
        chunkBytes: ByteArray
    ): PostResult {
        val crc32 = java.util.zip.CRC32().apply { update(chunkBytes) }.value
        val crcHex = String.format("%08x", crc32)
        val safeFileName = fileName.filter { it.code in 32..126 && it != '"' && it != '\\' }.ifEmpty { "file.bin" }

        val url = java.net.URL("http://${endpoint.ip}:${endpoint.port}/api/v1/transfers/chunk")
        var conn: java.net.HttpURLConnection? = null
        var isSuccess = false
        try {
            conn = openPeerConnection(url)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(chunkBytes.size)
            conn.connectTimeout = 4000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("X-Transfer-Id", transferId)
            conn.setRequestProperty("X-File-Name", safeFileName)
            conn.setRequestProperty("X-File-Name-Encoded", java.net.URLEncoder.encode(fileName, "UTF-8"))
            conn.setRequestProperty("X-File-Size", fileSize.toString())
            conn.setRequestProperty("X-Chunk-Id", chunkId.toString())
            conn.setRequestProperty("X-Chunk-Offset", chunkOffset.toString())
            conn.setRequestProperty("X-Chunk-Length", chunkBytes.size.toString())
            conn.setRequestProperty("X-Chunk-Crc32", crcHex)
            conn.setRequestProperty("X-Total-Chunks", totalChunks.toString())
            conn.setRequestProperty("X-Transport", endpoint.name)
            conn.setRequestProperty("X-Transport-Kind", if (endpoint.kind == TransportKind.USB) "USB" else "WIFI")

            conn.outputStream.use { it.write(chunkBytes) }

            val code = conn.responseCode
            return when (code) {
                in 200..299 -> {
                    try {
                        conn.inputStream?.use { input ->
                            val drain = ByteArray(1024)
                            while (input.read(drain) != -1) {}
                        }
                    } catch (_: Throwable) {}
                    isSuccess = true
                    PostResult.SUCCESS
                }
                404 -> {
                    try { conn.errorStream?.close() } catch (_: Throwable) {}
                    PostResult.NOT_FOUND
                }
                else -> {
                    try { conn.errorStream?.close() } catch (_: Throwable) {}
                    PostResult.RETRY
                }
            }
        } catch (_: Throwable) {
            return PostResult.RETRY
        } finally {
            // Only disconnect on error; Keep-Alive retains the TCP socket in the pool for zero handshake overhead!
            if (!isSuccess) {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
    }

    private data class ChunkTask(
        val uri: Uri,
        val fileTransferId: String,
        val fileName: String,
        val fileSize: Long,
        val chunkId: Int,
        val chunkOffset: Long,
        val chunkLength: Int,
        val totalChunks: Int
    )

    private fun executeRealTransfer(
        target: DiscoveredPeer,
        uris: List<Uri>,
        onUpdate: (SchedulerTelemetry) -> Unit
    ) {
        if (uris.isEmpty()) return
        val transferId = UUID.randomUUID()
        val firstFileName = queryFileName(uris[0]) ?: "file.bin"
        val totalFiles = uris.size

        activeSendJob?.cancel()
        httpServer?.resetCancellation()
        val onSendError = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            Log.e("MainActivity", "Send failed", error)
            lifecycleScope.launch(Dispatchers.Main) {
                TransferForegroundService.stopService(this@MainActivity)
                onUpdate(SchedulerTelemetry(transferId, firstFileName, "FAILED", 0.0, 0, 0, 0f, 0, emptyList(), emptyList()))
            }
        }
        activeSendJob = lifecycleScope.launch(Dispatchers.IO + onSendError) {
            val endpoints = resolveMultipathEndpoints(target)
            if (endpoints.isEmpty()) {
                withContext(Dispatchers.Main) {
                    prepareWireless()
                    Toast.makeText(this@MainActivity, "Cannot reach peer. Ensure ShareDash is active on peer device.", Toast.LENGTH_LONG).show()
                    onUpdate(
                        SchedulerTelemetry(
                            transferId = transferId,
                            title = firstFileName,
                            status = "FAILED",
                            aggregateMbps = 0.0,
                            totalBytes = 0L,
                            completedBytes = 0L,
                            progressPct = 0f,
                            etaSeconds = 0,
                            transports = emptyList(),
                            chunkStates = emptyList()
                        )
                    )
                }
                return@launch
            }

            // Identify USB and Wi-Fi endpoints for simultaneous multipath streaming
            val usbEndpoint = endpoints.firstOrNull { it.kind == TransportKind.USB && it.ip != "127.0.0.1" }
                ?: endpoints.firstOrNull { it.kind == TransportKind.USB }
            val wifiEndpoint = endpoints.firstOrNull { it.kind != TransportKind.USB }
            val hasUsb = usbEndpoint != null
            val hasWifi = wifiEndpoint != null
            val isMultipath = hasUsb && hasWifi

            // Start Foreground Service safely
            try {
                val serviceIntent = Intent(this@MainActivity, TransferForegroundService::class.java).apply {
                    putExtra(TransferForegroundService.EXTRA_TITLE, firstFileName)
                    putExtra(TransferForegroundService.EXTRA_PROGRESS, 0)
                    putExtra(TransferForegroundService.EXTRA_SPEED, if (isMultipath) "Multipath streaming (USB + Wi-Fi) to ${target.friendlyName}..." else "Streaming to ${target.friendlyName}...")
                }
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed starting transfer foreground service: ${e.message}")
            }

            // Calculate total bytes across all URIs
            var totalBytes = 0L
            val fileSizes = mutableListOf<Long>()
            uris.forEach { uri ->
                val size = try {
                    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
                    }?.takeIf { it >= 0 } ?: contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                        ?.takeIf { it >= 0 } ?: contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(128 * 1024)
                            var bytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                            }
                            bytes
                        }
                } catch (_: Exception) { null }
                if (size == null) {
                    withContext(Dispatchers.Main) {
                        onUpdate(SchedulerTelemetry(transferId, firstFileName, "FAILED", 0.0, 0, 0, 0f, 0, emptyList(), emptyList()))
                    }
                    TransferForegroundService.stopService(this@MainActivity)
                    return@launch
                }
                fileSizes.add(size)
                totalBytes += size
            }
            val effectiveTotalBytes = totalBytes

            // Setup chunking tasks across files
            val allTasks = mutableListOf<ChunkTask>()

            for (fileIdx in uris.indices) {
                val uri = uris[fileIdx]
                val fileName = queryFileName(uri) ?: "file_$fileIdx.bin"
                val fSize = fileSizes[fileIdx]
                val fileTransferId = if (uris.size == 1) transferId.toString() else UUID.randomUUID().toString()

                // Dynamic Chunk Sizing tuned for mobile Dalvik heap safety:
                // Cap max chunk size at 4 MB to guarantee zero OutOfMemoryError even for 10GB+ files,
                // while maintaining line-rate throughput across USB and Wi-Fi multipath.
                val baseChunkSize: Long = when {
                    fSize < 10 * 1024 * 1024L -> 1024 * 1024L // 1 MB for < 10 MB
                    fSize < 50 * 1024 * 1024L -> 2 * 1024 * 1024L // 2 MB for 10 - 50 MB
                    else -> 4 * 1024 * 1024L // 4 MB for >= 50 MB (optimal for mobile sockets & PC)
                }

                // Dynamic Tail-End Chunk Tapering: taper chunk sizes down towards end of file
                val fileChunks = mutableListOf<Pair<Long, Int>>()
                var currentOffset = 0L
                while (currentOffset < fSize) {
                    val remaining = fSize - currentOffset
                    val chunkSize = when {
                        remaining <= 8 * 1024 * 1024L && baseChunkSize > 2 * 1024 * 1024L -> 2 * 1024 * 1024L // Final 8 MB -> 2 MB micro-chunks
                        remaining <= 2 * 1024 * 1024L -> 1024 * 1024L // Final 2 MB -> 1 MB chunks
                        else -> baseChunkSize
                    }
                    val length = minOf(chunkSize, remaining).toInt()
                    fileChunks.add(currentOffset to length)
                    currentOffset += length
                }

                if (fSize == 0L) fileChunks.add(0L to 0)
                val totalChunks = fileChunks.size

                for (chunkIdx in fileChunks.indices) {
                    val (offset, length) = fileChunks[chunkIdx]
                    allTasks.add(
                        ChunkTask(
                            uri = uri,
                            fileTransferId = fileTransferId,
                            fileName = fileName,
                            fileSize = fSize,
                            chunkId = chunkIdx,
                            chunkOffset = offset,
                            chunkLength = length,
                            totalChunks = totalChunks
                        )
                    )
                }
            }

            val queue = ConcurrentLinkedQueue<ChunkTask>(allTasks)
            val usbBytesSent = AtomicLong(0L)
            val wifiBytesSent = AtomicLong(0L)
            val usbActive = AtomicBoolean(hasUsb)
            val wifiActive = AtomicBoolean(hasWifi)
            val acknowledgedChunks = AtomicInteger(0)
            val transferFailed = AtomicBoolean(false)
            val fallbackNeeded = AtomicBoolean(false)
            val transferFinished = AtomicBoolean(false)

            val initialTelem = SchedulerTelemetry(
                transferId = transferId,
                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                status = "ACTIVE",
                aggregateMbps = 0.0,
                totalBytes = effectiveTotalBytes,
                completedBytes = 0,
                progressPct = 0f,
                etaSeconds = 0,
                transports = listOfNotNull(
                    if (hasUsb) TransportStats("USB Fast-Path", TransportKind.USB, 0.0, 0.4, 0, true) else null,
                    if (hasWifi) TransportStats("Wi-Fi", TransportKind.LAN, 0.0, 1.5, 0, true) else null
                ),
                chunkStates = emptyList()
            )
            withContext(Dispatchers.Main) { onUpdate(initialTelem) }

            // Concurrent Telemetry Reporting Loop
            val telemetryJob = launch(Dispatchers.IO) {
                var lastTime = System.currentTimeMillis()
                var lastUsb = 0L
                var lastWifi = 0L
                var smoothUsbMbps = 0.0
                var smoothWifiMbps = 0.0

                while (isActive && !transferFinished.get() && !transferFailed.get()) {
                    delay(150)
                    val now = System.currentTimeMillis()
                    val dt = (now - lastTime).coerceAtLeast(1) / 1000.0
                    val curUsb = usbBytesSent.get()
                    val curWifi = wifiBytesSent.get()
                    val dUsb = curUsb - lastUsb
                    val dWifi = curWifi - lastWifi

                    val instUsbMbps = (dUsb * 8.0) / (dt * 1_000_000.0)
                    val instWifiMbps = (dWifi * 8.0) / (dt * 1_000_000.0)
                    smoothUsbMbps = if (smoothUsbMbps == 0.0) instUsbMbps else (smoothUsbMbps * 0.35 + instUsbMbps * 0.65)
                    smoothWifiMbps = if (smoothWifiMbps == 0.0) instWifiMbps else (smoothWifiMbps * 0.35 + instWifiMbps * 0.65)

                    lastTime = now
                    lastUsb = curUsb
                    lastWifi = curWifi

                    val totalSent = curUsb + curWifi
                    val aggMbps = (smoothUsbMbps + smoothWifiMbps).coerceAtLeast(0.0)
                    val remainingBytes = (effectiveTotalBytes - totalSent).coerceAtLeast(0L)
                    val eta = if (aggMbps > 0) (remainingBytes * 8 / (aggMbps * 1_000_000.0)).toLong() else 0L

                    val telem = SchedulerTelemetry(
                        transferId = transferId,
                        title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                        status = "ACTIVE",
                        aggregateMbps = aggMbps,
                        usbBytes = curUsb,
                        totalBytes = effectiveTotalBytes,
                        completedBytes = totalSent.coerceAtMost(effectiveTotalBytes),
                        progressPct = (totalSent.toFloat() / effectiveTotalBytes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                        etaSeconds = eta,
                        transports = listOfNotNull(
                            if (hasUsb || curUsb > 0) TransportStats("USB Fast-Path", TransportKind.USB, smoothUsbMbps, 0.4, (curUsb / (256 * 1024)), usbActive.get()) else null,
                            if (hasWifi || curWifi > 0) TransportStats("Wi-Fi", TransportKind.LAN, smoothWifiMbps, 1.5, (curWifi / (256 * 1024)), wifiActive.get()) else null
                        ),
                        chunkStates = emptyList()
                    )
                    TransferForegroundService.updateProgress(this@MainActivity, telem.title, (telem.progressPct * 100).toInt(), formatTransferSpeed(telem.aggregateMbps))
                    withContext(Dispatchers.Main) { onUpdate(telem) }
                }
            }

            // Tune networking connection pool
            System.setProperty("http.keepAlive", "true")
            System.setProperty("http.maxConnections", "32")

            // Pre-open FileChannels once per file to eliminate 1,456 ContentResolver Binder round-trips!
            val openFdMap = ConcurrentHashMap<Uri, java.nio.channels.FileChannel>()
            for (uri in uris.distinct().take(64)) {
                try {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        openFdMap[uri] = ParcelFileDescriptor.AutoCloseInputStream(pfd).channel
                    }
                } catch (e: Throwable) {
                    Log.w("MainActivity", "Failed to pre-open channel for $uri: ${e.message}")
                }
            }

            val readChunkFromChannel: (ChunkTask, ByteArray) -> ByteArray? = { task, buf ->
                val cached = openFdMap[task.uri]
                if (cached != null) {
                    try {
                        val channel = cached
                        val buffer = java.nio.ByteBuffer.wrap(buf)
                        var bytesRead = 0
                        while (bytesRead < task.chunkLength) {
                            val r = channel.read(buffer, task.chunkOffset + bytesRead)
                            if (r <= 0) break
                            bytesRead += r
                        }
                        if (bytesRead == task.chunkLength) buf else if (bytesRead > 0) buf.copyOf(bytesRead) else ByteArray(0)
                    } catch (_: Throwable) {
                        readChunkBytes(task.uri, task.chunkOffset, task.chunkLength)
                    }
                } else {
                    readChunkBytes(task.uri, task.chunkOffset, task.chunkLength)
                }
            }

            fun sendChunk(endpoint: TransportEndpoint, task: ChunkTask, bytes: ByteArray) =
                postChunkDetailed(endpoint, task.fileTransferId, task.fileName, task.fileSize,
                    task.chunkId, task.chunkOffset, task.totalChunks, bytes)

            suspend fun runWorker(primary: TransportEndpoint, alternate: TransportEndpoint?) {
                val primaryActive = if (primary.kind == TransportKind.USB) usbActive else wifiActive
                var buffer = ByteArray(0)
                while (kotlinx.coroutines.currentCoroutineContext().isActive && !transferFailed.get() && !fallbackNeeded.get()) {
                    val task = queue.poll() ?: return
                    if (buffer.size != task.chunkLength) buffer = ByteArray(task.chunkLength)
                    val bytes = readChunkFromChannel(task, buffer)
                    if (bytes == null || bytes.size != task.chunkLength) {
                        transferFailed.set(true)
                        return
                    }
                    var endpoint = if (primaryActive.get() || alternate == null) primary else alternate
                    var result = sendChunk(endpoint, task, bytes)
                    if (result == PostResult.RETRY) {
                        result = sendChunk(endpoint, task, bytes)
                    }
                    if (result == PostResult.RETRY && alternate != null) {
                        if (endpoint == primary) {
                            primaryActive.set(false)
                            endpoint = alternate
                            result = sendChunk(endpoint, task, bytes)
                        }
                    }
                    when (result) {
                        PostResult.SUCCESS -> {
                            val counter = if (endpoint.kind == TransportKind.USB) usbBytesSent else wifiBytesSent
                            counter.addAndGet(bytes.size.toLong())
                            acknowledgedChunks.incrementAndGet()
                        }
                        PostResult.NOT_FOUND -> fallbackNeeded.set(true)
                        PostResult.RETRY -> transferFailed.set(true)
                    }
                }
            }

            val sendStarted = android.os.SystemClock.elapsedRealtime()
            try {
                coroutineScope {
                    if (usbEndpoint != null) repeat(if (isMultipath) 3 else 4) {
                        launch(Dispatchers.IO) { runWorker(usbEndpoint, wifiEndpoint) }
                    }
                    if (wifiEndpoint != null) repeat(if (isMultipath) 4 else 5) {
                        launch(Dispatchers.IO) { runWorker(wifiEndpoint, usbEndpoint) }
                    }
                }
            } finally {
                openFdMap.values.forEach { channel ->
                    try { channel.close() } catch (_: Throwable) {}
                }
                openFdMap.clear()
            }

            telemetryJob.cancel()
            telemetryJob.join()
            transferFinished.set(true)

            // If remote server returns 404 (only supports single-stream /send), execute backward-compatible fallback
            if (fallbackNeeded.get()) {
                val primaryEndpoint = endpoints.first()
                executeSendFallback(target, primaryEndpoint, uris, transferId, effectiveTotalBytes, firstFileName, totalFiles, onUpdate)
                return@launch
            }

            val totalSent = usbBytesSent.get() + wifiBytesSent.get()
            val averageMbps = totalSent * 8.0 / ((android.os.SystemClock.elapsedRealtime() - sendStarted).coerceAtLeast(1) * 1000.0)
            val isSuccess = !transferFailed.get() && totalSent == effectiveTotalBytes && acknowledgedChunks.get() == allTasks.size

            val finalTelem = SchedulerTelemetry(
                transferId = transferId,
                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                status = if (isSuccess) "COMPLETED" else "FAILED",
                aggregateMbps = averageMbps,
                totalBytes = effectiveTotalBytes,
                completedBytes = if (isSuccess) effectiveTotalBytes else totalSent,
                progressPct = if (isSuccess) 1.0f else (totalSent.toFloat() / effectiveTotalBytes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                etaSeconds = 0,
                usbBytes = usbBytesSent.get(),
                transports = listOfNotNull(
                    if (usbBytesSent.get() > 0) TransportStats("USB Fast-Path", TransportKind.USB, 0.0, 0.4, (usbBytesSent.get() / (256 * 1024)), true) else null,
                    if (wifiBytesSent.get() > 0) TransportStats("Wi-Fi", TransportKind.LAN, 0.0, 1.5, (wifiBytesSent.get() / (256 * 1024)), true) else null
                ),
                chunkStates = emptyList()
            )

            val transportLabel = when {
                usbBytesSent.get() > 0 && wifiBytesSent.get() > 0 -> "USB + Wi-Fi Multipath"
                usbBytesSent.get() > 0 -> "USB Fast-Path"
                else -> "Wi-Fi"
            }

            withContext(Dispatchers.Main) {
                onUpdate(finalTelem)
                if (finalTelem.status == "COMPLETED") TransferForegroundService.complete(this@MainActivity, finalTelem.title)
                else TransferForegroundService.stopService(this@MainActivity)
                historyManager.addRecord(
                    TransferRecord(
                        fileName = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                        fileSize = effectiveTotalBytes,
                        direction = TransferDirection.SENT,
                        status = if (isSuccess) TransferStatus.COMPLETED else TransferStatus.FAILED,
                        peerName = target.friendlyName,
                        transportUsed = transportLabel,
                        speedMbps = averageMbps
                    )
                )
            }
        }
    }

    private suspend fun executeSendFallback(
        target: DiscoveredPeer,
        endpoint: TransportEndpoint,
        uris: List<Uri>,
        transferId: UUID,
        effectiveTotalBytes: Long,
        firstFileName: String,
        totalFiles: Int,
        onUpdate: (SchedulerTelemetry) -> Unit
    ) {
        val resolvedIp = endpoint.ip
        val resolvedPort = endpoint.port
        val isUsb = endpoint.kind == TransportKind.USB

        var transferredBytes = 0L
        val startTime = System.currentTimeMillis()
        var lastSendUiMs = startTime
        var lastCalcBytes = 0L
        var smoothMbps = 0.0
        var success = true

        for (uri in uris) {
            val fileName = queryFileName(uri) ?: "file.bin"
            try {
                val boundary = "ShareDash-${System.currentTimeMillis()}-${transferId.toString().take(8)}"
                val url = java.net.URL("http://$resolvedIp:$resolvedPort/api/v1/transfers/send")
                val conn = openPeerConnection(url)
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setRequestProperty("Connection", "Keep-Alive")
                conn.setChunkedStreamingMode(2 * 1024 * 1024)
                conn.connectTimeout = 10000
                conn.readTimeout = 0

                val os = java.io.BufferedOutputStream(conn.outputStream, 2 * 1024 * 1024)
                val header = "--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$fileName\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                os.write(header.toByteArray(Charsets.UTF_8))

                val input = contentResolver.openInputStream(uri)?.buffered(2 * 1024 * 1024)
                    ?: throw java.io.IOException("Cannot open selected file")
                input.use {
                    val buffer = ByteArray(1024 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                        transferredBytes += read

                        val nowMs = System.currentTimeMillis()
                        val progress = if (effectiveTotalBytes > 0) (transferredBytes.toFloat() / effectiveTotalBytes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) else 0f

                        if (nowMs - lastSendUiMs >= 35L || transferredBytes >= effectiveTotalBytes) {
                            val deltaSec = (nowMs - lastSendUiMs).coerceAtLeast(1) / 1000.0
                            val deltaBytes = transferredBytes - lastCalcBytes
                            val instantMbps = (deltaBytes * 8.0) / (deltaSec * 1_000_000.0)
                            smoothMbps = if (smoothMbps == 0.0) instantMbps else (smoothMbps * 0.35 + instantMbps * 0.65)
                            lastSendUiMs = nowMs
                            lastCalcBytes = transferredBytes

                            val remainingBytes = effectiveTotalBytes - transferredBytes
                            val eta = if (smoothMbps > 0) (remainingBytes * 8 / (smoothMbps * 1_000_000.0)).toLong() else 0

                            val telem = SchedulerTelemetry(
                                transferId = transferId,
                                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                                status = "ACTIVE",
                                aggregateMbps = smoothMbps,
                                totalBytes = effectiveTotalBytes,
                                completedBytes = transferredBytes,
                                progressPct = progress,
                                usbBytes = if (isUsb) transferredBytes else 0,
                                etaSeconds = eta,
                                transports = listOf(
                                    TransportStats(if (isUsb) "USB Fast-Path" else "Wi-Fi", endpoint.kind, smoothMbps, if (isUsb) 0.4 else 1.5, (transferredBytes / (256 * 1024)), true)
                                ),
                                chunkStates = emptyList()
                            )
                            TransferForegroundService.updateProgress(this@MainActivity, telem.title, (telem.progressPct * 100).toInt(), formatTransferSpeed(telem.aggregateMbps))
                            withContext(Dispatchers.Main) { onUpdate(telem) }
                        }
                    }
                }

                val footer = "\r\n--$boundary--\r\n"
                os.write(footer.toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    success = false
                }
                conn.disconnect()
            } catch (_: Throwable) {
                success = false
            }
        }

        val finalTelem = SchedulerTelemetry(
            transferId = transferId,
            title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
            status = if (success) "COMPLETED" else "FAILED",
            aggregateMbps = smoothMbps,
            totalBytes = effectiveTotalBytes,
            completedBytes = if (success) effectiveTotalBytes else transferredBytes,
            usbBytes = if (isUsb) transferredBytes else 0,
            progressPct = if (success) 1.0f else ((transferredBytes.toFloat() / effectiveTotalBytes.coerceAtLeast(1))).coerceIn(0f, 1f),
            etaSeconds = 0,
            transports = listOf(TransportStats(if (isUsb) "USB Fast-Path" else "Wi-Fi", endpoint.kind, smoothMbps, if (isUsb) 0.4 else 1.5, 0, true)),
            chunkStates = emptyList()
        )

        withContext(Dispatchers.Main) {
            onUpdate(finalTelem)
            if (success) TransferForegroundService.complete(this@MainActivity, finalTelem.title)
            else TransferForegroundService.stopService(this@MainActivity)
            historyManager.addRecord(
                TransferRecord(
                    fileName = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                    fileSize = effectiveTotalBytes,
                    direction = TransferDirection.SENT,
                    status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
                    peerName = target.friendlyName,
                    transportUsed = if (isUsb) "USB Fast-Path" else "Wi-Fi",
                    speedMbps = smoothMbps
                )
            )
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                    pendingFiles = listOf(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    pendingFiles = uris
                }
            }
            "com.sharedash.app.WIFI_CONNECT" -> {
                val ssid = intent.getStringExtra("ssid") ?: ""
                val password = intent.getStringExtra("password") ?: ""
                if (ssid.isNotBlank()) {
                    connectToWifiNetwork(ssid, password)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val neededPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            neededPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val ungranted = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        } else {
            startDiscovery()
        }
    }

    private val usbReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action
            if (action == "android.hardware.usb.action.USB_DEVICE_ATTACHED" ||
                action == "android.hardware.usb.action.USB_STATE" ||
                action == Intent.ACTION_BATTERY_CHANGED) {
                val connected = intent?.extras?.getBoolean("connected") ?: (intent?.extras?.getBoolean("configured") ?: true)
                _isUsbCablePlugged.value = connected
                checkUsbState()
                if (_isUsbTetheringActive.value) {
                    onUsbConnectedCallback?.invoke()
                }
            }
        }
    }

    private val wifiConnectReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.sharedash.app.WIFI_CONNECT") {
                val ssid = intent.getStringExtra("ssid") ?: ""
                val password = intent.getStringExtra("password") ?: ""
                if (ssid.isNotBlank()) {
                    android.util.Log.i("MainActivity", "Received WIFI_CONNECT broadcast: SSID=$ssid")
                    connectToWifiNetwork(ssid, password)
                }
            }
        }
    }

    private fun startDiscovery() {
        try {
            udpManager.startDiscovery(lifecycleScope)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "UDP start failed: ${e.message}")
        }
    }

    /**
     * Connect to a Wi-Fi network programmatically (triggered by PC over the control connection).
     * Uses WifiNetworkSuggestion API on Android 10+ since addNetwork() is deprecated.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun connectToWifiNetwork(ssid: String, password: String) {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? android.net.wifi.WifiManager
        if (wifiManager != null && !wifiManager.isWifiEnabled) {
            android.util.Log.w("MainActivity", "Phone Wi-Fi is OFF! Requesting Wi-Fi to be enabled for connection to $ssid...")
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Wi-Fi is OFF. Turning Wi-Fi on to connect to $ssid...", Toast.LENGTH_LONG).show()
                try {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        wifiManager.isWifiEnabled = true
                    } else {
                        try {
                            val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI)
                            panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(panelIntent)
                        } catch (_: Exception) {
                            val settingsIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(settingsIntent)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Could not toggle Wi-Fi: ${e.message}")
                }
            }
        }

        // Disconnect existing active Wi-Fi connection to eliminate MCC/SCC radio time-slicing and accelerate peer association
        try {
            @Suppress("DEPRECATION")
            wifiManager?.disconnect()
            android.util.Log.i("MainActivity", "Disconnected existing Wi-Fi station connection to free radio band for $ssid")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Could not disconnect previous Wi-Fi: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use WifiNetworkSuggestion
            if (wifiManager != null) {
                val suggestionBuilder = android.net.wifi.WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setIsAppInteractionRequired(false)

                if (password.isNotEmpty()) {
                    suggestionBuilder.setWpa2Passphrase(password)
                }

                val suggestion = suggestionBuilder.build()

                // Remove any previous suggestions from us first
                wifiManager.removeNetworkSuggestions(listOf(suggestion))

                val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
                if (status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    android.util.Log.i("MainActivity", "Wi-Fi suggestion added for SSID='$ssid'")
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.util.Log.e("MainActivity", "Wi-Fi suggestion failed with status: $status")
                }

                // Also try WifiNetworkSpecifier for immediate connection
                try {
                    val specifierBuilder = android.net.wifi.WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)

                    if (password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(password)
                    }

                    val specifier = specifierBuilder.build()
                    val request = android.net.NetworkRequest.Builder()
                        .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(specifier)
                        .build()
                    val connManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    connManager.requestNetwork(request, object : android.net.ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: android.net.Network) {
                            android.util.Log.i("MainActivity", "Connected to $ssid via WifiNetworkSpecifier")
                            fallbackWifi = network
                        }
                        override fun onLost(network: android.net.Network) {
                            if (fallbackWifi == network) fallbackWifi = null
                        }
                        override fun onUnavailable() {
                            android.util.Log.w("MainActivity", "WifiNetworkSpecifier: $ssid unavailable")
                        }
                    })
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "WifiNetworkSpecifier error: ${e.message}")
                }
            }
        } else {
            // Android 9 and below: Use deprecated addNetwork
            @Suppress("DEPRECATION")
            if (wifiManager != null) {
                val wifiConfig = android.net.wifi.WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (password.isNotEmpty()) {
                        preSharedKey = "\"$password\""
                    } else {
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                val netId = wifiManager.addNetwork(wifiConfig)
                if (netId != -1) {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(netId, true)
                    wifiManager.reconnect()
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Open USB tethering settings (triggered by PC over the control connection).
     * Android doesn't expose a public API to toggle tethering programmatically.
     */
    private fun openUsbTetheringSettings() {
        val targets = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
        )
        for (intent in targets) {
            try { startActivity(intent); return } catch (_: android.content.ActivityNotFoundException) {
            } catch (_: SecurityException) {}
        }
        Toast.makeText(this, "Open Settings and search for USB tethering.", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        checkUsbState()
        if (resumeWirelessSetup) {
            resumeWirelessSetup = false
            prepareWireless()
        }
        if (_isUsbTetheringActive.value) {
            onUsbConnectedCallback?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(wifiConnectReceiver) } catch (_: Exception) {}
        try { hotspotManager.stopHotspot() } catch (_: Exception) {}
        httpServer?.stop()
        udpManager.stopDiscovery()
        transportManager.closeAll()
    }
}
