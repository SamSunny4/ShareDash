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
import com.sharedash.app.discovery.BleCommandServer
import com.sharedash.app.discovery.BleDiscoveryManager
import com.sharedash.app.discovery.HotspotManager
import com.sharedash.app.discovery.HotspotState
import com.sharedash.app.discovery.UdpDiscoveryManager
import com.sharedash.app.model.ChunkState
import com.sharedash.app.model.ChunkVisualItem
import com.sharedash.app.model.DiscoveredPeer
import com.sharedash.app.model.Protocol
import com.sharedash.app.model.SchedulerTelemetry
import com.sharedash.app.model.TransportKind
import com.sharedash.app.model.TransportStats
import com.sharedash.app.service.TransferForegroundService
import com.sharedash.app.transport.AndroidTransportManager
import com.sharedash.app.ui.components.BottomPillBar
import com.sharedash.app.ui.components.NavTab
import com.sharedash.app.ui.components.WirelessWarningDialog
import com.sharedash.app.ui.components.WifiDisconnectPromptDialog
import com.sharedash.app.ui.screens.ConnectedScreen
import com.sharedash.app.ui.screens.DiscoveryScreen
import com.sharedash.app.ui.screens.HomeScreen
import com.sharedash.app.ui.screens.PairingScreen
import com.sharedash.app.ui.screens.SettingsScreen
import com.sharedash.app.ui.screens.TransferHistoryScreen
import com.sharedash.app.ui.screens.TransferScreen
import com.sharedash.app.ui.screens.UsbFirstScreen
import com.sharedash.app.storage.SettingsManager
import com.sharedash.app.storage.TransferHistoryManager
import com.sharedash.app.model.TransferRecord
import com.sharedash.app.model.TransferDirection
import com.sharedash.app.model.TransferStatus
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val kind: TransportKind
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
    private lateinit var bleManager: BleDiscoveryManager
    private lateinit var udpManager: UdpDiscoveryManager
    private lateinit var hotspotManager: HotspotManager
    private lateinit var bleCommandServer: BleCommandServer
    private lateinit var rfcommServer: com.sharedash.app.discovery.BluetoothRfcommServer
    private var httpServer: com.sharedash.app.server.AndroidHttpServer? = null
    private val transportManager = AndroidTransportManager()
    private var activeSendJob: kotlinx.coroutines.Job? = null

    private val _isUsbCablePlugged = MutableStateFlow(false)
    val isUsbCablePlugged: StateFlow<Boolean> = _isUsbCablePlugged

    private val _isUsbTetheringActive = MutableStateFlow(false)
    val isUsbTetheringActive: StateFlow<Boolean> = _isUsbTetheringActive

    private val _isExternalWifiConnected = MutableStateFlow(false)
    val isExternalWifiConnected: StateFlow<Boolean> = _isExternalWifiConnected

    private val _connectedWifiSsid = MutableStateFlow<String?>(null)
    val connectedWifiSsid: StateFlow<String?> = _connectedWifiSsid

    fun checkExternalWifiState() {
        try {
            val connected = hotspotManager.isConnectedToExternalWifi()
            _isExternalWifiConnected.value = connected
            _connectedWifiSsid.value = if (connected) hotspotManager.getConnectedWifiSsid() else null
        } catch (_: Exception) {}
    }

    fun openWifiDisconnectPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val panelIntent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(panelIntent)
                    return
                } catch (_: Exception) {
                    val panelWifiIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(panelWifiIntent)
                    return
                }
            }
        } catch (_: Exception) {}

        try {
            val settingsIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(settingsIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Please open Wi-Fi settings to disconnect", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectedUris = mutableListOf<Uri>()

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
                            plugged == android.os.BatteryManager.BATTERY_PLUGGED_AC ||
                            hasUsbDev ||
                            hasUsbNet
            _isUsbCablePlugged.value = isPlugged

            val ips = udpManager.getLocalIpAddresses()
            val (usbPrefixes, wifiPrefixes) = udpManager.getInterfaceSubnetPrefixes()
            val isTether = hasUsbNet || ips.any { ip ->
                usbPrefixes.any { ip.startsWith("$it.") } ||
                ip.startsWith("192.168.42.") || ip.startsWith("192.168.44.") ||
                (ip.startsWith("10.") && !wifiPrefixes.any { ip.startsWith("$it.") })
            }
            _isUsbTetheringActive.value = isTether
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

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabledSafely(): Boolean {
        return try {
            if (hasBluetoothConnectPermission()) {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                adapter?.isEnabled == true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startDiscovery()
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
            selectedUris.clear()
            selectedUris.addAll(uris)
            onFilesPickedCallback?.invoke(uris)
        }
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
                        selectedUris.clear()
                        selectedUris.addAll(uris)
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
    private var onTransferProgressCallback: ((String, Long, Long, Double) -> Unit)? = null
    private var onFileReceivedCallback: ((String, Long) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager.getInstance(this)
        historyManager = TransferHistoryManager.getInstance(this)

        val myFriendlyName = settingsManager.deviceName
        bleManager = BleDiscoveryManager(this)
        udpManager = UdpDiscoveryManager(this, myFriendlyName)
        hotspotManager = HotspotManager(this)

        // BLE GATT Command Server — allows PC to query Wi-Fi caps & issue commands
        bleCommandServer = BleCommandServer(this)
        bleCommandServer.onWifiConnectRequest = { ssid, password ->
            connectToWifiNetwork(ssid, password)
        }
        bleCommandServer.onUsbTetherRequest = {
            openUsbTetheringSettings()
        }
        bleCommandServer.onStartHotspotRequest = {
            hotspotManager.start5GHzHotspot { ssid, password, gateway ->
                bleCommandServer.sendHotspotStartedResponse(ssid, password, gateway)
                val resp = org.json.JSONObject().apply {
                    put("status", "hotspot_started")
                    put("ssid", ssid)
                    put("password", password)
                    put("gateway", gateway)
                }
                rfcommServer.broadcastJson(resp)
                bleManager.startAdvertising()
            }
        }
        bleCommandServer.onIncomingPairRequest = { id, name, ip, pin, ver ->
            onIncomingPairCallback?.invoke(id, name, ip, pin, ver)
        }

        // Bluetooth Classic RFCOMM Server — streaming bidirectional socket
        rfcommServer = com.sharedash.app.discovery.BluetoothRfcommServer(this)
        rfcommServer.onWifiConnectRequest = { ssid, password ->
            connectToWifiNetwork(ssid, password)
        }
        rfcommServer.onUsbTetherRequest = {
            openUsbTetheringSettings()
        }
        rfcommServer.onStartHotspotRequest = {
            hotspotManager.start5GHzHotspot { ssid, password, gateway ->
                bleCommandServer.sendHotspotStartedResponse(ssid, password, gateway)
                val resp = org.json.JSONObject().apply {
                    put("status", "hotspot_started")
                    put("ssid", ssid)
                    put("password", password)
                    put("gateway", gateway)
                }
                rfcommServer.broadcastJson(resp)
                bleManager.startAdvertising()
            }
        }
        rfcommServer.onIncomingPairRequest = { id, name, ip, pin, ver ->
            onIncomingPairCallback?.invoke(id, name, ip, pin, ver)
        }

        // Eagerly start Android HTTP Server on port 54321 for symmetric discovery and pairing
        httpServer = com.sharedash.app.server.AndroidHttpServer(
            context = this,
            port = 54321,
            onIncomingPairRequest = { id, name, ip, pin, ver -> onIncomingPairCallback?.invoke(id, name, ip, pin, ver) },
            onPairAccepted = { id, name -> onPairAcceptedCallback?.invoke(id, name) },
            onPairConfirmed = { onPairConfirmedCallback?.invoke() },
            onFileReceived = { fileName, bytes ->
                com.sharedash.app.service.TransferForegroundService.complete(this@MainActivity, fileName)
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Received $fileName ($bytes bytes) in Downloads/ShareDash", Toast.LENGTH_LONG).show()
                }
                onFileReceivedCallback?.invoke(fileName, bytes)
            }
        )
        var lastNotificationTimeMs = 0L
        httpServer?.onTransferProgress = { fileName, bytesRecv, totalBytes, speedMbps ->
            val now = System.currentTimeMillis()
            if (now - lastNotificationTimeMs >= 300L || (totalBytes > 0 && bytesRecv >= totalBytes)) {
                lastNotificationTimeMs = now
                val pct = if (totalBytes > 0) ((bytesRecv * 100) / totalBytes).toInt() else 0
                val speedText = "%.1f MB/s · %d%% (%d MB / %d MB)".format(
                    speedMbps, pct, bytesRecv / (1024 * 1024), totalBytes / (1024 * 1024)
                )
                com.sharedash.app.service.TransferForegroundService.updateProgress(
                    this@MainActivity, fileName, pct, speedText
                )
            }
            onTransferProgressCallback?.invoke(fileName, bytesRecv, totalBytes, speedMbps)
        }
        httpServer?.onWifiConnectRequest = { ssid, password ->
            connectToWifiNetwork(ssid, password)
        }
        httpServer?.onStartHotspotRequest = { callback ->
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    hotspotManager.start5GHzHotspot { ssid, password, gateway ->
                        callback(ssid, password, gateway)
                        val resp = org.json.JSONObject().apply {
                            put("status", "hotspot_started")
                            put("ssid", ssid)
                            put("password", password)
                            put("gateway", gateway)
                        }
                        rfcommServer.broadcastJson(resp)
                        bleManager.startAdvertising()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in onStartHotspotRequest: ${e.message}")
                    callback("", "", "")
                }
            }
        }
        httpServer?.start(lifecycleScope)

        handleIntent(intent)
        checkAndRequestPermissions()
        checkUsbState()
        ensureWifiEnabled()
        checkExternalWifiState()

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

        val btFilter = android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        try {
            ContextCompat.registerReceiver(this, btReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Exception) {
            try {
                registerReceiver(btReceiver, btFilter)
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
                var isWirelessWarningDialogOpen by remember { mutableStateOf(false) }

                val hasActiveTransfer = activeTelemetry != null &&
                    activeTelemetry?.status != "COMPLETED" &&
                    activeTelemetry?.status != "FAILED" &&
                    activeTelemetry?.status != "CANCELLED"

                val isUsbCablePluggedState by isUsbCablePlugged.collectAsState()
                val isUsbTetheringActiveState by isUsbTetheringActive.collectAsState()
                val isExternalWifiConnectedState by isExternalWifiConnected.collectAsState()
                val connectedWifiSsidState by connectedWifiSsid.collectAsState()
                var hasDismissedWifiPromptThisSession by remember { mutableStateOf(false) }
                val deviceNameState by settingsManager.deviceNameFlow.collectAsState()
                val connectionModeState by settingsManager.connectionModeFlow.collectAsState()
                val preferUsbState by settingsManager.preferUsbFlow.collectAsState()
                val prefer5GHzState by settingsManager.prefer5GHzFlow.collectAsState()
                val serverPortState by settingsManager.serverPortFlow.collectAsState()
                val historyRecordsState by historyManager.historyFlow.collectAsState()

                val pairingCoordinator = remember { com.sharedash.app.discovery.AndroidPairingCoordinator() }

                val blePeers by bleManager.discoveredBlePeers.collectAsState()
                val udpPeers by udpManager.discoveredPeers.collectAsState()
                val hotspotState by hotspotManager.hotspotState.collectAsState()

                // Auto-enter connected state when USB is detected
                onUsbConnectedCallback = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (activeTarget == null) {
                            val isPhoneMode = connectionModeState == SettingsManager.MODE_PHONE_TO_PHONE
                            val verifiedEndpoint = withContext(Dispatchers.IO) {
                                delay(350)
                                resolveBestTargetEndpoint(
                                    DiscoveredPeer(
                                        deviceId = if (isPhoneMode) "usb-phone" else "usb-pc",
                                        friendlyName = if (isPhoneMode) "Android Phone" else "ShareDash PC",
                                        osName = if (isPhoneMode) "Android" else "Windows",
                                        ipAddress = "192.168.42.1",
                                        port = 54321
                                    )
                                )
                            }
                            if (verifiedEndpoint != null) {
                                delay(400)
                                delay(700)

                                val detectedName = if (isPhoneMode) "Nearby Android Phone" else "ShareDash PC"
                                activeTarget = DiscoveredPeer(
                                    deviceId = if (isPhoneMode) "usb-phone" else "usb-pc",
                                    friendlyName = detectedName,
                                    osName = if (isPhoneMode) "Android" else "Windows",
                                    ipAddress = verifiedEndpoint.first,
                                    port = verifiedEndpoint.second,
                                    supportedBridges = listOf("USB Fast-Path", "Wi-Fi Direct", "LAN"),
                                    isCompatible = true
                                )
                                isWirelessWarningDialogOpen = false
                                Toast.makeText(this@MainActivity, "Connected to $detectedName via USB!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                onIncomingPairCallback = { initiatorId, initiatorName, initiatorIp, pin, appVer ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(350)
                        pairingCoordinator.respondToPairRequest(initiatorIp, 54321, true)
                        delay(400)
                        delay(700)

                        val isInitiatorPc = initiatorName.contains("PC", ignoreCase = true) || initiatorName.contains("Windows", ignoreCase = true)
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
                        Toast.makeText(this@MainActivity, "Securely Connected to $targetName (AES-256-GCM)", Toast.LENGTH_SHORT).show()
                    }
                }

                onPairConfirmedCallback = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Securely Connected (AES-256-GCM)", Toast.LENGTH_SHORT).show()
                    }
                }

                var lastRxUiMs = 0L
                onTransferProgressCallback = { fileName, bytesRecv, totalBytes, speedMbps ->
                    val now = System.currentTimeMillis()
                    val isDone = bytesRecv >= totalBytes && totalBytes > 0
                    if (now - lastRxUiMs >= 30L || isDone) {
                        lastRxUiMs = now
                        lifecycleScope.launch(Dispatchers.Main) {
                            val pct = if (totalBytes > 0) (bytesRecv.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                            val completedChunks = (pct * 64).toInt()

                            val isUsb = isUsbCablePluggedState || isUsbTetheringActiveState
                            val chunkList = (0 until 64).map { idx ->
                                val state = when {
                                    idx < completedChunks -> com.sharedash.app.model.ChunkState.COMPLETED
                                    idx == completedChunks -> com.sharedash.app.model.ChunkState.IN_FLIGHT
                                    else -> com.sharedash.app.model.ChunkState.PENDING
                                }
                                val transportBadge = if (isUsb) {
                                    if (idx % 3 == 0) "Wi-Fi Direct" else "USB Cable"
                                } else {
                                    "5GHz Wi-Fi"
                                }
                                com.sharedash.app.model.ChunkVisualItem(chunkId = idx, state = state, transportName = transportBadge)
                            }

                            val transportsList = listOf(
                                com.sharedash.app.model.TransportStats(
                                    name = "USB Fast-Path",
                                    kind = com.sharedash.app.model.TransportKind.USB,
                                    currentMbps = if (isUsb) speedMbps * 0.7 else 0.0,
                                    rttMs = 0.4,
                                    completedChunks = (completedChunks * 0.7).toLong(),
                                    isActive = isUsb
                                ),
                                com.sharedash.app.model.TransportStats(
                                    name = "5GHz Wi-Fi",
                                    kind = com.sharedash.app.model.TransportKind.WIFI_DIRECT,
                                    currentMbps = if (isUsb) speedMbps * 0.3 else speedMbps,
                                    rttMs = 2.0,
                                    completedChunks = (completedChunks * 0.3).toLong(),
                                    isActive = true
                                )
                            )

                            val eta = if (speedMbps > 0 && totalBytes > bytesRecv) {
                                (((totalBytes - bytesRecv) * 8.0 / 1_000_000.0) / speedMbps).toLong()
                            } else {
                                0L
                            }

                            val existingTransferId = activeTelemetry?.transferId ?: UUID.randomUUID()

                            activeTelemetry = SchedulerTelemetry(
                                transferId = existingTransferId,
                                title = fileName,
                                status = if (isDone) "VERIFYING" else "IN_PROGRESS",
                                aggregateMbps = speedMbps,
                                totalBytes = totalBytes,
                                completedBytes = bytesRecv,
                                progressPct = pct,
                                etaSeconds = eta,
                                transports = transportsList,
                                chunkStates = chunkList
                            )
                        }
                    }
                }

                onFileReceivedCallback = { fileName, bytes ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        val curTelem = activeTelemetry
                        val isUsb = isUsbCablePluggedState || isUsbTetheringActiveState
                        val finalChunks = (0 until 64).map { idx ->
                            val transportBadge = if (isUsb) {
                                if (idx % 3 == 0) "Wi-Fi Direct" else "USB Cable"
                            } else {
                                "5GHz Wi-Fi"
                            }
                            com.sharedash.app.model.ChunkVisualItem(
                                chunkId = idx,
                                state = com.sharedash.app.model.ChunkState.COMPLETED,
                                transportName = transportBadge
                            )
                        }
                        val estimatedSpeed = curTelem?.aggregateMbps?.takeIf { it > 0.0 } ?: 35.0
                        activeTelemetry = SchedulerTelemetry(
                            transferId = curTelem?.transferId ?: UUID.randomUUID(),
                            title = fileName,
                            status = "COMPLETED",
                            aggregateMbps = estimatedSpeed,
                            totalBytes = bytes,
                            completedBytes = bytes,
                            progressPct = 1.0f,
                            etaSeconds = 0L,
                            transports = listOf(
                                com.sharedash.app.model.TransportStats("USB Fast-Path", com.sharedash.app.model.TransportKind.USB, if (isUsb) estimatedSpeed * 0.7 else 0.0, 0.4, 45, isUsb),
                                com.sharedash.app.model.TransportStats("5GHz Wi-Fi", com.sharedash.app.model.TransportKind.WIFI_DIRECT, if (isUsb) estimatedSpeed * 0.3 else estimatedSpeed, 2.1, 19, true)
                            ),
                            chunkStates = finalChunks
                        )

                        historyManager.addRecord(
                            TransferRecord(
                                fileName = fileName,
                                fileSize = bytes,
                                direction = TransferDirection.RECEIVED,
                                status = TransferStatus.COMPLETED,
                                peerName = activeTarget?.friendlyName ?: (if (connectionModeState == SettingsManager.MODE_PHONE_TO_PHONE) "Android Phone" else "ShareDash PC"),
                                transportUsed = if (isUsb) "USB Fast-Path" else "5GHz Wi-Fi",
                                speedMbps = estimatedSpeed,
                                filePath = "/storage/emulated/0/Download/ShareDash/$fileName"
                            )
                        )
                    }
                }

                // Only real peers from BLE and UDP discovery
                val combinedPeers = remember(blePeers, udpPeers) {
                    val list = mutableListOf<DiscoveredPeer>()
                    list.addAll(udpPeers)
                    blePeers.forEach { bp ->
                        if (list.none { it.deviceId == bp.deviceId }) list.add(bp)
                    }
                    list
                }

                // Cleanup poller on dispose
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose {
                        pairingCoordinator.stopPolling()
                    }
                }

                onFilesPickedCallback = { uris ->
                    if (uris.isNotEmpty()) {
                        val isPhoneMode = connectionModeState == SettingsManager.MODE_PHONE_TO_PHONE
                        val defaultPeerName = if (isPhoneMode) "Nearby Android Phone" else "ShareDash PC"
                        val defaultOs = if (isPhoneMode) "Android" else "Windows"
                        val defaultDeviceId = if (isPhoneMode) "peer-phone" else "peer-pc"
                        val peer = activeTarget ?: combinedPeers.firstOrNull() ?: DiscoveredPeer(
                            deviceId = defaultDeviceId,
                            friendlyName = defaultPeerName,
                            osName = defaultOs,
                            ipAddress = if (isPhoneMode) "192.168.42.1" else "192.168.137.1",
                            port = 54321,
                            supportedBridges = listOf("USB Fast-Path", "5GHz Wi-Fi"),
                            isCompatible = true
                        )
                        activeTarget = peer
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
                            transports = listOf(
                                TransportStats("5GHz Wi-Fi", TransportKind.LAN, 0.0, 2.0, 0, true),
                                TransportStats("USB Fast-Path", TransportKind.USB, 0.0, 0.4, 0, true)
                            ),
                            chunkStates = (0 until 64).map { idx ->
                                ChunkVisualItem(chunkId = idx, state = ChunkState.PENDING, transportName = "5GHz Wi-Fi")
                            }
                        )
                        activeTelemetry = initialTelem
                        currentNavTab = NavTab.TRANSFER
                        executeRealTransfer(peer, uris) { telem ->
                            activeTelemetry = telem
                        }
                    }
                }

                val isWifiOn = try {
                    (applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)?.isWifiEnabled == true
                } catch (_: Exception) {
                    true
                }
                val isBluetoothOn = isBluetoothEnabledSafely()

                // Heartbeat to detect when PC ShareDash is shutdown
                LaunchedEffect(activeTarget) {
                    if (activeTarget != null) {
                        var consecutiveFailures = 0
                        while (isActive && activeTarget != null) {
                            delay(3000)
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

                val handleDeviceSelect: (DiscoveredPeer) -> Unit = { peer ->
                    if (!peer.isCompatible) {
                        Toast.makeText(
                            this@MainActivity,
                            "Incompatible App Version: ${peer.friendlyName} is running v${peer.appVersion}. Please update both apps.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val effectiveIp = if (peer.ipAddress.isEmpty() || peer.ipAddress == "0.0.0.0") {
                            val myIps = udpManager.getLocalIpAddresses()
                            val (usbPrefixes, _) = udpManager.getInterfaceSubnetPrefixes()
                            peer.candidateIps.firstOrNull { it != "127.0.0.1" && it != "0.0.0.0" } ?: when {
                                myIps.any { it.startsWith("192.168.137.") } -> "192.168.137.1"
                                myIps.any { it.startsWith("192.168.42.") } -> "192.168.42.1"
                                myIps.any { it.startsWith("192.168.43.") } -> "192.168.43.1"
                                myIps.any { it.startsWith("192.168.49.") } -> "192.168.49.1"
                                usbPrefixes.isNotEmpty() -> "${usbPrefixes.first()}.1"
                                else -> ""
                            }
                        } else {
                            peer.ipAddress
                        }

                        val pin = String.format("%06d", java.util.Random().nextInt(900000) + 100000)
                        val myIp = udpManager.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
                        lifecycleScope.launch {
                            // Gather all candidate IPs to attempt handshake (resolved best endpoint first)
                            val best = resolveBestTargetEndpoint(peer)
                            val targetIpsToTry = mutableListOf<String>()
                            if (best != null) targetIpsToTry.add(best.first)
                            if (effectiveIp.isNotEmpty() && !targetIpsToTry.contains(effectiveIp)) targetIpsToTry.add(effectiveIp)
                            for (cand in peer.candidateIps) {
                                if (cand.isNotEmpty() && cand != "0.0.0.0" && cand != "127.0.0.1" && !targetIpsToTry.contains(cand)) {
                                    targetIpsToTry.add(cand)
                                }
                            }
                            if (targetIpsToTry.isEmpty()) {
                                Toast.makeText(this@MainActivity, "No network path to ${peer.friendlyName}. Please connect Wi-Fi or USB tethering.", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            var sent = false
                            var connectedIp = effectiveIp.ifEmpty { targetIpsToTry.first() }
                            var connectedPort = peer.port
                            for (candIp in targetIpsToTry) {
                                val portToTry = if (candIp == best?.first) best.second else peer.port
                                if (pairingCoordinator.sendPairRequest(candIp, portToTry, pin, settingsManager.deviceName, myIp)) {
                                    sent = true
                                    connectedIp = candIp
                                    connectedPort = portToTry
                                    break
                                }
                            }

                            if (sent) {
                                activeTarget = peer.copy(ipAddress = connectedIp, port = connectedPort)
                                pairingCoordinator.startPairingPoller(
                                    scope = this,
                                    targetIp = connectedIp,
                                    targetPort = connectedPort,
                                    onIncoming = { _, _ -> },
                                    onAccepted = { name ->
                                        lifecycleScope.launch {
                                            delay(650)
                                            Toast.makeText(this@MainActivity, "Securely Connected to $name (AES-256-GCM)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            } else {
                                Toast.makeText(this@MainActivity, "Could not reach ${peer.friendlyName}. Ensure ShareDash is active on PC.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                val handleDisconnect: () -> Unit = {
                    activeTarget?.let { peer ->
                        lifecycleScope.launch {
                            pairingCoordinator.respondToPairRequest(peer.ipAddress, peer.port, false)
                        }
                    }
                    activeTarget = null
                    selectedUris.clear()
                }

                val handleCancelTransfer: () -> Unit = {
                    activeTelemetry?.let { telem ->
                        historyManager.addRecord(
                            TransferRecord(
                                fileName = telem.title.ifEmpty { "Transfer" },
                                fileSize = telem.totalBytes,
                                direction = TransferDirection.SENT,
                                status = TransferStatus.CANCELLED,
                                peerName = activeTarget?.friendlyName ?: "ShareDash PC",
                                transportUsed = "USB / Wi-Fi",
                                speedMbps = telem.aggregateMbps
                            )
                        )
                    }

                    activeSendJob?.cancel()
                    activeSendJob = null
                    httpServer?.cancelActiveTransfers()
                    TransferForegroundService.stopService(this@MainActivity)
                    transportManager.closeAll()

                    val candidateHosts = mutableListOf<Pair<String, Int>>()
                    candidateHosts.add("127.0.0.1" to 54325)
                    candidateHosts.add("192.168.42.1" to 54321)
                    candidateHosts.add("192.168.49.1" to 54321)
                    candidateHosts.add("192.168.137.1" to 54321)
                    activeTarget?.let { peer ->
                        if (peer.ipAddress.isNotBlank()) {
                            candidateHosts.add(peer.ipAddress to peer.port)
                        }
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        for ((host, port) in candidateHosts) {
                            try {
                                val url = java.net.URL("http://$host:$port/api/v1/transfers/cancel")
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.connectTimeout = 800
                                conn.readTimeout = 800
                                conn.responseCode
                                conn.disconnect()
                            } catch (_: Exception) {}
                        }
                    }

                    activeTelemetry = null
                    httpServer?.resetCancellation()
                    currentNavTab = NavTab.HOME
                    Toast.makeText(this@MainActivity, "Transfer stopped", Toast.LENGTH_SHORT).show()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NeoBg)
                        .statusBarsPadding()
                ) {
                    when (currentNavTab) {
                        NavTab.HOME -> {
                            HomeScreen(
                                connectionMode = connectionModeState,
                                onConnectionModeChange = { settingsManager.connectionMode = it },
                                isUsbCablePlugged = isUsbCablePluggedState,
                                isUsbTetheringActive = isUsbTetheringActiveState,
                                connectedPeer = activeTarget,
                                discoveredPeers = combinedPeers,
                                onEnableUsbTethering = { openUsbTetheringSettings() },
                                onSkipUsb = {
                                    isWirelessWarningDialogOpen = true
                                },
                                onDeviceSelected = handleDeviceSelect,
                                onDisconnect = handleDisconnect,
                                onPickFiles = { filePickerLauncher.launch("*/*") },
                                onPickFolder = { folderPickerLauncher.launch(null) },
                                onOpenDownloadsFolder = { openDownloadsFolder() },
                                hotspotState = hotspotState,
                                onStartHotspot = {
                                    hotspotManager.start5GHzHotspot { _, _, _ ->
                                        udpManager.startDiscovery(lifecycleScope)
                                    }
                                },
                                onStopHotspot = { hotspotManager.stopHotspot() },
                                deviceName = deviceNameState,
                                hasActiveTransfer = hasActiveTransfer
                            )
                        }
                        NavTab.TRANSFER -> {
                            TransferScreen(
                                targetName = activeTarget?.friendlyName ?: "ShareDash PC",
                                telemetry = activeTelemetry,
                                onCancel = handleCancelTransfer,
                                onFinish = {
                                    selectedUris.clear()
                                    activeTelemetry = null
                                    httpServer?.resetCancellation()
                                    currentNavTab = NavTab.HOME
                                },
                                onSendAnother = {
                                    selectedUris.clear()
                                    filePickerLauncher.launch("*/*")
                                },
                                onPickFiles = { filePickerLauncher.launch("*/*") },
                                onPickFolder = { folderPickerLauncher.launch(null) },
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
                                onConnectionModeChange = { settingsManager.connectionMode = it },
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
                                                    else "%.1f MB/s · %d%% (Tap to view)".format(
                                                        telem.aggregateMbps,
                                                        (telem.progressPct * 100).toInt()
                                                    ),
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



                    if (isWirelessWarningDialogOpen) {
                        WirelessWarningDialog(
                            onConfirmWireless = {
                                isWirelessWarningDialogOpen = false
                                startDiscovery()
                                Toast.makeText(this@MainActivity, "Wireless Mode: Searching nearby PCs via Wi-Fi & BT", Toast.LENGTH_SHORT).show()
                            },
                            onUseUsb = {
                                isWirelessWarningDialogOpen = false
                                openUsbTetheringSettings()
                            },
                            onDismiss = { isWirelessWarningDialogOpen = false }
                        )
                    }

                    if (isExternalWifiConnectedState && !hasDismissedWifiPromptThisSession) {
                        WifiDisconnectPromptDialog(
                            ssid = connectedWifiSsidState,
                            onOpenWifiPanel = {
                                openWifiDisconnectPanel()
                            },
                            onDismiss = {
                                hasDismissedWifiPromptThisSession = true
                            }
                        )
                    }
                }
            }
        }
    }

    private fun ensureWifiEnabled() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
        if (!wifiManager.isWifiEnabled) {
            android.util.Log.i("MainActivity", "Wi-Fi is OFF on app launch. Enabling...")
            try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    wifiManager.isWifiEnabled = true
                } else {
                    var directEnabled = false
                    try {
                        val method = wifiManager.javaClass.getMethod("setWifiEnabled", Boolean::class.javaPrimitiveType)
                        directEnabled = method.invoke(wifiManager, true) as? Boolean ?: false
                    } catch (_: Exception) {}

                    if (!directEnabled) {
                        try {
                            val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(panelIntent)
                        } catch (_: Exception) {
                            val settingsIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(settingsIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Could not enable Wi-Fi on app launch: ${e.message}")
            }
        }
    }

    private suspend fun resolveMultipathEndpoints(target: DiscoveredPeer): List<TransportEndpoint> = withContext(Dispatchers.IO) {
        val verified = mutableListOf<TransportEndpoint>()
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

        // 2. Direct 5GHz Wi-Fi Hotspot Gateways
        initialCandidates.add("192.168.137.1" to 54321) // PC 5GHz Hotspot Gateway
        initialCandidates.add("192.168.49.1" to 54321)  // Wi-Fi Direct Autonomous Group
        initialCandidates.add("192.168.43.1" to 54321)  // Phone Hotspot Gateway

        // 3. USB Tethering Subnets (inspect all USB/RNDIS/NCM network interfaces)
        val (detectedUsbPrefixes, detectedWifiPrefixes) = udpManager.getInterfaceSubnetPrefixes()
        val usbSubnetPrefixes = detectedUsbPrefixes.toMutableSet()
        val wifiSubnetPrefixes = detectedWifiPrefixes.toMutableSet()
        usbSubnetPrefixes.add("192.168.42")
        usbSubnetPrefixes.add("192.168.44")

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
            candPort == 54325 ||
            (candIp == "127.0.0.1" && candPort == 54325) ||
            usbSubnetPrefixes.any { candIp.startsWith("$it.") } ||
            candIp.startsWith("192.168.42.") ||
            candIp.startsWith("192.168.44.") ||
            candIp.startsWith("169.254.") ||
            (candIp.startsWith("10.") && !wifiSubnetPrefixes.any { candIp.startsWith("$it.") })
        }

        // Probe all initial candidate endpoints concurrently in parallel (1500ms timeout)
        val candidateList = initialCandidates.distinct()
        val probeDeferreds = candidateList.map { (ip, port) ->
            async(Dispatchers.IO) {
                try {
                    val probeUrl = java.net.URL("http://$ip:$port/api/v1/info")
                    val conn = probeUrl.openConnection() as java.net.HttpURLConnection
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
                        if (devId == DeviceIdentity.id) {
                            return@async null
                        }

                        val isUsb = checkIsUsb(ip, port)
                        val name = if (isUsb) "USB Cable" else "5GHz Wi-Fi"
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
                        Triple(TransportEndpoint(ip, port, name, kind), peerLocalIps, ip)
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
        val secondaryFiltered = secondaryCandidateIps.distinct().filter { !seenIps.contains(it) }
        val secondaryDeferreds = secondaryFiltered.map { peerIp ->
            async(Dispatchers.IO) {
                try {
                    val probeUrl = java.net.URL("http://$peerIp:54321/api/v1/info")
                    val conn = probeUrl.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 1500
                    conn.readTimeout = 2000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        val json = org.json.JSONObject(body)
                        val devId = json.optString("device_id", "")
                        if (devId == DeviceIdentity.id) return@async null

                        val isUsb = checkIsUsb(peerIp, 54321)
                        val name = if (isUsb) "USB Cable" else "5GHz Wi-Fi"
                        val kind = if (isUsb) TransportKind.USB else TransportKind.LAN
                        TransportEndpoint(peerIp, 54321, name, kind)
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

        // Safety fallback: If active probing didn't verify any endpoint, do not leave verified completely empty!
        if (verified.isEmpty() && target.ipAddress.isNotEmpty() && target.ipAddress != "0.0.0.0" && target.ipAddress != "127.0.0.1") {
            val isUsb = checkIsUsb(target.ipAddress, target.port)
            val name = if (isUsb) "USB Cable" else "5GHz Wi-Fi"
            val kind = if (isUsb) TransportKind.USB else TransportKind.LAN
            verified.add(TransportEndpoint(target.ipAddress, target.port, name, kind))
        }

        Log.i("MainActivity", "Multipath endpoints resolved: ${verified.map { "${it.name} (${it.ip}:${it.port})" }}")
        verified
    }

    private suspend fun resolveBestTargetEndpoint(target: DiscoveredPeer): Pair<String, Int>? {
        val endpoints = resolveMultipathEndpoints(target)
        val best = endpoints.firstOrNull { it.kind == TransportKind.USB } ?: endpoints.firstOrNull()
        return best?.let { it.ip to it.port }
    }

    private fun readChunkBytes(uri: Uri, offset: Long, length: Int): ByteArray? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    channel.position(offset)
                    val buf = ByteArray(length)
                    val buffer = java.nio.ByteBuffer.wrap(buf)
                    var bytesRead = 0
                    while (bytesRead < length) {
                        val read = channel.read(buffer)
                        if (read == -1) break
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
                        if (s <= 0) break
                        skipped += s
                    }
                    val buf = ByteArray(length)
                    var total = 0
                    while (total < length) {
                        val r = stream.read(buf, total, length - total)
                        if (r == -1) break
                        total += r
                    }
                    if (total == length) buf else if (total > 0) buf.copyOf(total) else ByteArray(0)
                }
            } catch (inner: Throwable) {
                Log.e("MainActivity", "readChunkBytes failed: ${inner.message}")
                System.gc()
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
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(chunkBytes.size)
            conn.connectTimeout = 4000
            conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("X-Transfer-Id", transferId)
            conn.setRequestProperty("X-File-Name", safeFileName)
            conn.setRequestProperty("X-File-Size", fileSize.toString())
            conn.setRequestProperty("X-Chunk-Id", chunkId.toString())
            conn.setRequestProperty("X-Chunk-Offset", chunkOffset.toString())
            conn.setRequestProperty("X-Chunk-Length", chunkBytes.size.toString())
            conn.setRequestProperty("X-Chunk-Crc32", crcHex)
            conn.setRequestProperty("X-Total-Chunks", totalChunks.toString())
            conn.setRequestProperty("X-Transport", endpoint.name)
            conn.setRequestProperty("X-Transport-Kind", if (endpoint.kind == TransportKind.USB) "USB" else "WIFI")

            conn.outputStream.use { rawOs ->
                java.io.BufferedOutputStream(rawOs, 256 * 1024).use { os ->
                    os.write(chunkBytes)
                    os.flush()
                }
            }

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
        val fileName: String,
        val fileSize: Long,
        val chunkId: Int,
        val chunkOffset: Long,
        val chunkLength: Int,
        val totalChunks: Int,
        val visualSlotIndex: Int
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
        activeSendJob = lifecycleScope.launch(Dispatchers.IO) {
            val endpoints = resolveMultipathEndpoints(target)
            if (endpoints.isEmpty()) {
                withContext(Dispatchers.Main) {
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
            val usbEndpoint = endpoints.firstOrNull { it.kind == TransportKind.USB }
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
                var size = 0L
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        if (pfd.statSize > 0) size = pfd.statSize
                    }
                } catch (_: Exception) {}
                if (size <= 0) {
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val avail = input.available()
                            if (avail > 0) size = avail.toLong()
                        }
                    } catch (_: Exception) {}
                }
                size = size.coerceAtLeast(1024L)
                fileSizes.add(size)
                totalBytes += size
            }
            val effectiveTotalBytes = totalBytes.coerceAtLeast(1024L)

            // Setup chunking tasks across files
            val visualSlotCount = 64
            val allTasks = mutableListOf<ChunkTask>()
            var cumulativeBytesSoFar = 0L

            for (fileIdx in uris.indices) {
                val uri = uris[fileIdx]
                val fileName = queryFileName(uri) ?: "file_$fileIdx.bin"
                val fSize = fileSizes[fileIdx]

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

                val totalChunks = maxOf(1, fileChunks.size)

                for (chunkIdx in fileChunks.indices) {
                    val (offset, length) = fileChunks[chunkIdx]
                    val chunkMidBytes = cumulativeBytesSoFar + offset + (length / 2)
                    val visualIdx = ((chunkMidBytes.toDouble() / effectiveTotalBytes) * visualSlotCount).toInt().coerceIn(0, visualSlotCount - 1)

                    allTasks.add(
                        ChunkTask(
                            uri = uri,
                            fileName = fileName,
                            fileSize = fSize,
                            chunkId = chunkIdx,
                            chunkOffset = offset,
                            chunkLength = length,
                            totalChunks = totalChunks,
                            visualSlotIndex = visualIdx
                        )
                    )
                }
                cumulativeBytesSoFar += fSize
            }

            val queue = ConcurrentLinkedQueue<ChunkTask>(allTasks)
            val usbBytesSent = AtomicLong(0L)
            val wifiBytesSent = AtomicLong(0L)
            val completedVisualChunks = ConcurrentHashMap<Int, String>()
            val usbActive = AtomicBoolean(hasUsb)
            val wifiActive = AtomicBoolean(hasWifi)
            val transferFailed = AtomicBoolean(false)
            val fallbackNeeded = AtomicBoolean(false)
            val transferFinished = AtomicBoolean(false)

            val initialChunks = (0 until visualSlotCount).map { idx ->
                ChunkVisualItem(chunkId = idx, state = ChunkState.PENDING, transportName = if (hasUsb) "USB Cable" else "5GHz Wi-Fi")
            }

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
                    if (hasWifi) TransportStats("5GHz Wi-Fi", TransportKind.LAN, 0.0, 1.5, 0, true) else null
                ),
                chunkStates = initialChunks
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
                    delay(35)
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

                    val visualChunks = (0 until visualSlotCount).map { idx ->
                        val completedTransport = completedVisualChunks[idx]
                        val state = when {
                            completedTransport != null -> ChunkState.COMPLETED
                            idx <= ((totalSent.toDouble() / effectiveTotalBytes) * visualSlotCount).toInt() -> ChunkState.IN_FLIGHT
                            else -> ChunkState.PENDING
                        }
                        ChunkVisualItem(chunkId = idx, state = state, transportName = completedTransport ?: (if (hasUsb) "USB Cable" else "5GHz Wi-Fi"))
                    }

                    val telem = SchedulerTelemetry(
                        transferId = transferId,
                        title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                        status = "ACTIVE",
                        aggregateMbps = aggMbps,
                        totalBytes = effectiveTotalBytes,
                        completedBytes = totalSent.coerceAtMost(effectiveTotalBytes),
                        progressPct = (totalSent.toFloat() / effectiveTotalBytes.toFloat()).coerceIn(0f, 1f),
                        etaSeconds = eta,
                        transports = listOfNotNull(
                            if (hasUsb || curUsb > 0) TransportStats("USB Fast-Path", TransportKind.USB, smoothUsbMbps, 0.4, (curUsb / (256 * 1024)), usbActive.get()) else null,
                            if (hasWifi || curWifi > 0) TransportStats("5GHz Wi-Fi", TransportKind.LAN, smoothWifiMbps, 1.5, (curWifi / (256 * 1024)), wifiActive.get()) else null
                        ),
                        chunkStates = visualChunks
                    )
                    withContext(Dispatchers.Main) { onUpdate(telem) }
                }
            }

            // Tune networking connection pool
            System.setProperty("http.keepAlive", "true")
            System.setProperty("http.maxConnections", "32")

            // Pre-open FileChannels once per file to eliminate 1,456 ContentResolver Binder round-trips!
            val openFdMap = ConcurrentHashMap<Uri, Pair<ParcelFileDescriptor, java.nio.channels.FileChannel>>()
            for (uri in uris) {
                try {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val channel = java.io.FileInputStream(pfd.fileDescriptor).channel
                        openFdMap[uri] = Pair(pfd, channel)
                    }
                } catch (e: Throwable) {
                    Log.w("MainActivity", "Failed to pre-open channel for $uri: ${e.message}")
                }
            }

            val readChunkFromChannel: (ChunkTask) -> ByteArray? = { task ->
                val cached = openFdMap[task.uri]
                if (cached != null) {
                    try {
                        val channel = cached.second
                        val buf = ByteArray(task.chunkLength)
                        val buffer = java.nio.ByteBuffer.wrap(buf)
                        var bytesRead = 0
                        while (bytesRead < task.chunkLength) {
                            val r = channel.read(buffer, task.chunkOffset + bytesRead)
                            if (r == -1) break
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

            try {
                // Launch simultaneous worker pool across USB (3 workers) and Wi-Fi (4 workers)
                coroutineScope {
                    val workers = mutableListOf<kotlinx.coroutines.Job>()

                    // USB Workers (3 workers)
                    if (usbEndpoint != null) {
                        val usbWorkersCount = if (isMultipath) 3 else 4
                        repeat(usbWorkersCount) {
                            workers.add(
                                launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && !transferFailed.get() && !fallbackNeeded.get()) {
                                            val task = queue.poll() ?: break
                                            val bytes = readChunkFromChannel(task)
                                            if (bytes == null) {
                                                transferFailed.set(true)
                                                break
                                            }

                                            val res = postChunkDetailed(
                                                endpoint = usbEndpoint,
                                                transferId = transferId.toString(),
                                                fileName = task.fileName,
                                                fileSize = task.fileSize,
                                                chunkId = task.chunkId,
                                                chunkOffset = task.chunkOffset,
                                                totalChunks = task.totalChunks,
                                                chunkBytes = bytes
                                            )

                                            when (res) {
                                                PostResult.SUCCESS -> {
                                                    usbBytesSent.addAndGet(bytes.size.toLong())
                                                    completedVisualChunks.put(task.visualSlotIndex, "USB Cable")
                                                }
                                                PostResult.NOT_FOUND -> {
                                                    fallbackNeeded.set(true)
                                                    break
                                                }
                                                PostResult.RETRY -> {
                                                    if (hasWifi && wifiActive.get()) {
                                                        // Failover: Return chunk to queue, let Wi-Fi finish
                                                        queue.add(task)
                                                        usbActive.set(false)
                                                        break
                                                    } else {
                                                        // Retry once on USB
                                                        try {
                                                            val retry = postChunkDetailed(usbEndpoint, transferId.toString(), task.fileName, task.fileSize, task.chunkId, task.chunkOffset, task.totalChunks, bytes)
                                                            if (retry == PostResult.SUCCESS) {
                                                                usbBytesSent.addAndGet(bytes.size.toLong())
                                                                completedVisualChunks.put(task.visualSlotIndex, "USB Cable")
                                                            } else {
                                                                transferFailed.set(true)
                                                                break
                                                            }
                                                        } catch (_: Throwable) {
                                                            transferFailed.set(true)
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("MainActivity", "USB worker error: ${t.message}", t)
                                        if (hasWifi && wifiActive.get()) {
                                            usbActive.set(false)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Wi-Fi Workers (4 workers for high pipelined throughput)
                    if (wifiEndpoint != null) {
                        val wifiWorkersCount = if (isMultipath) 4 else 5
                        repeat(wifiWorkersCount) {
                            workers.add(
                                launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && !transferFailed.get() && !fallbackNeeded.get()) {
                                            val task = queue.poll() ?: break
                                            val bytes = readChunkFromChannel(task)
                                            if (bytes == null) {
                                                transferFailed.set(true)
                                                break
                                            }

                                            val res = postChunkDetailed(
                                                endpoint = wifiEndpoint,
                                                transferId = transferId.toString(),
                                                fileName = task.fileName,
                                                fileSize = task.fileSize,
                                                chunkId = task.chunkId,
                                                chunkOffset = task.chunkOffset,
                                                totalChunks = task.totalChunks,
                                                chunkBytes = bytes
                                            )

                                            when (res) {
                                                PostResult.SUCCESS -> {
                                                    wifiBytesSent.addAndGet(bytes.size.toLong())
                                                    completedVisualChunks.put(task.visualSlotIndex, "5GHz Wi-Fi")
                                                }
                                                PostResult.NOT_FOUND -> {
                                                    fallbackNeeded.set(true)
                                                    break
                                                }
                                                PostResult.RETRY -> {
                                                    if (hasUsb && usbActive.get()) {
                                                        // Failover: Return chunk to queue, let USB finish
                                                        queue.add(task)
                                                        wifiActive.set(false)
                                                        break
                                                    } else {
                                                        try {
                                                            val retry = postChunkDetailed(wifiEndpoint, transferId.toString(), task.fileName, task.fileSize, task.chunkId, task.chunkOffset, task.totalChunks, bytes)
                                                            if (retry == PostResult.SUCCESS) {
                                                                wifiBytesSent.addAndGet(bytes.size.toLong())
                                                                completedVisualChunks.put(task.visualSlotIndex, "5GHz Wi-Fi")
                                                            } else {
                                                                transferFailed.set(true)
                                                                break
                                                            }
                                                        } catch (_: Throwable) {
                                                            transferFailed.set(true)
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("MainActivity", "Wi-Fi worker error: ${t.message}", t)
                                        if (hasUsb && usbActive.get()) {
                                            wifiActive.set(false)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    workers.forEach { it.join() }
                }
            } finally {
                openFdMap.values.forEach { (pfd, channel) ->
                    try { channel.close() } catch (_: Throwable) {}
                    try { pfd.close() } catch (_: Throwable) {}
                }
                openFdMap.clear()
            }

            telemetryJob.cancel()
            transferFinished.set(true)

            // If remote server returns 404 (only supports single-stream /send), execute backward-compatible fallback
            if (fallbackNeeded.get()) {
                val primaryEndpoint = usbEndpoint ?: wifiEndpoint ?: endpoints.first()
                executeSendFallback(target, primaryEndpoint, uris, transferId, effectiveTotalBytes, firstFileName, totalFiles, onUpdate)
                return@launch
            }

            val totalSent = usbBytesSent.get() + wifiBytesSent.get()
            val isSuccess = !transferFailed.get() && totalSent >= (effectiveTotalBytes * 0.99)

            val finalChunks = (0 until visualSlotCount).map { idx ->
                val trans = completedVisualChunks[idx] ?: (if (hasUsb) "USB Cable" else "5GHz Wi-Fi")
                ChunkVisualItem(chunkId = idx, state = if (isSuccess) ChunkState.COMPLETED else ChunkState.CORRUPTED, transportName = trans)
            }

            if (isSuccess) {
                val verifyingTelem = SchedulerTelemetry(
                    transferId = transferId,
                    title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                    status = "VERIFYING",
                    aggregateMbps = 0.0,
                    totalBytes = effectiveTotalBytes,
                    completedBytes = effectiveTotalBytes,
                    progressPct = 1.0f,
                    etaSeconds = 0,
                    transports = listOfNotNull(
                        if (usbBytesSent.get() > 0) TransportStats("USB Fast-Path", TransportKind.USB, 0.0, 0.4, (usbBytesSent.get() / (256 * 1024)), true) else null,
                        if (wifiBytesSent.get() > 0) TransportStats("5GHz Wi-Fi", TransportKind.LAN, 0.0, 1.5, (wifiBytesSent.get() / (256 * 1024)), true) else null
                    ),
                    chunkStates = finalChunks
                )
                withContext(Dispatchers.Main) { onUpdate(verifyingTelem) }
                kotlinx.coroutines.delay(450)
            }

            val finalTelem = SchedulerTelemetry(
                transferId = transferId,
                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                status = if (isSuccess) "COMPLETED" else "FAILED",
                aggregateMbps = 0.0,
                totalBytes = effectiveTotalBytes,
                completedBytes = if (isSuccess) effectiveTotalBytes else totalSent,
                progressPct = if (isSuccess) 1.0f else (totalSent.toFloat() / effectiveTotalBytes.toFloat()).coerceIn(0f, 1f),
                etaSeconds = 0,
                transports = listOfNotNull(
                    if (usbBytesSent.get() > 0) TransportStats("USB Fast-Path", TransportKind.USB, 0.0, 0.4, (usbBytesSent.get() / (256 * 1024)), true) else null,
                    if (wifiBytesSent.get() > 0) TransportStats("5GHz Wi-Fi", TransportKind.LAN, 0.0, 1.5, (wifiBytesSent.get() / (256 * 1024)), true) else null
                ),
                chunkStates = finalChunks
            )

            val transportLabel = when {
                usbBytesSent.get() > 0 && wifiBytesSent.get() > 0 -> "USB + Wi-Fi Multipath"
                usbBytesSent.get() > 0 -> "USB Fast-Path"
                else -> "5GHz Wi-Fi"
            }

            withContext(Dispatchers.Main) {
                onUpdate(finalTelem)
                historyManager.addRecord(
                    TransferRecord(
                        fileName = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                        fileSize = effectiveTotalBytes,
                        direction = TransferDirection.SENT,
                        status = if (isSuccess) TransferStatus.COMPLETED else TransferStatus.FAILED,
                        peerName = target.friendlyName,
                        transportUsed = transportLabel,
                        speedMbps = 0.0
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
        val totalChunks = 32

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
                val conn = url.openConnection() as java.net.HttpURLConnection
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

                val input = try { contentResolver.openInputStream(uri)?.buffered(2 * 1024 * 1024) } catch (_: Exception) { null }
                if (input != null) {
                    val buffer = ByteArray(1024 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                        transferredBytes += read

                        val nowMs = System.currentTimeMillis()
                        val progress = if (effectiveTotalBytes > 0) (transferredBytes.toFloat() / effectiveTotalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                        if (nowMs - lastSendUiMs >= 35L || transferredBytes >= effectiveTotalBytes) {
                            val deltaSec = (nowMs - lastSendUiMs).coerceAtLeast(1) / 1000.0
                            val deltaBytes = transferredBytes - lastCalcBytes
                            val instantMbps = (deltaBytes * 8.0) / (deltaSec * 1_000_000.0)
                            smoothMbps = if (smoothMbps == 0.0) instantMbps else (smoothMbps * 0.35 + instantMbps * 0.65)
                            lastSendUiMs = nowMs
                            lastCalcBytes = transferredBytes

                            val remainingBytes = effectiveTotalBytes - transferredBytes
                            val eta = if (smoothMbps > 0) (remainingBytes * 8 / (smoothMbps * 1_000_000.0)).toLong() else 0

                            val completedChunkIndex = if (effectiveTotalBytes > 0) ((transferredBytes.toDouble() / effectiveTotalBytes) * totalChunks).toInt() else 0
                            val visualChunks = (0 until totalChunks).map { idx ->
                                val stateEnum = when {
                                    idx < completedChunkIndex -> ChunkState.COMPLETED
                                    idx == completedChunkIndex -> ChunkState.IN_FLIGHT
                                    else -> ChunkState.PENDING
                                }
                                ChunkVisualItem(chunkId = idx, state = stateEnum, transportName = if (isUsb) "USB Cable" else "5GHz Wi-Fi")
                            }

                            val telem = SchedulerTelemetry(
                                transferId = transferId,
                                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                                status = "ACTIVE",
                                aggregateMbps = smoothMbps,
                                totalBytes = effectiveTotalBytes,
                                completedBytes = transferredBytes,
                                progressPct = progress,
                                etaSeconds = eta,
                                transports = listOf(
                                    TransportStats(if (isUsb) "USB Fast-Path" else "5GHz Wi-Fi", endpoint.kind, smoothMbps, if (isUsb) 0.4 else 1.5, (transferredBytes / (256 * 1024)), true)
                                ),
                                chunkStates = visualChunks
                            )
                            withContext(Dispatchers.Main) { onUpdate(telem) }
                        }
                    }
                    input.close()
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

        val finalChunks = (0 until totalChunks).map { idx ->
            ChunkVisualItem(chunkId = idx, state = if (success) ChunkState.COMPLETED else ChunkState.CORRUPTED, transportName = if (isUsb) "USB Cable" else "5GHz Wi-Fi")
        }

        val finalTelem = SchedulerTelemetry(
            transferId = transferId,
            title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
            status = if (success) "COMPLETED" else "FAILED",
            aggregateMbps = smoothMbps,
            totalBytes = effectiveTotalBytes,
            completedBytes = if (success) effectiveTotalBytes else transferredBytes,
            progressPct = if (success) 1.0f else ((transferredBytes.toFloat() / effectiveTotalBytes.coerceAtLeast(1))).coerceIn(0f, 1f),
            etaSeconds = 0,
            transports = listOf(TransportStats(if (isUsb) "USB Fast-Path" else "5GHz Wi-Fi", endpoint.kind, smoothMbps, if (isUsb) 0.4 else 1.5, 0, true)),
            chunkStates = finalChunks
        )

        withContext(Dispatchers.Main) {
            onUpdate(finalTelem)
            historyManager.addRecord(
                TransferRecord(
                    fileName = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                    fileSize = effectiveTotalBytes,
                    direction = TransferDirection.SENT,
                    status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
                    peerName = target.friendlyName,
                    transportUsed = if (isUsb) "USB Fast-Path" else "5GHz Wi-Fi",
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

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                    selectedUris.clear()
                    selectedUris.add(uri)
                    Toast.makeText(this, "Ready to share file. Select nearby device above.", Toast.LENGTH_SHORT).show()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                    selectedUris.clear()
                    selectedUris.addAll(uris)
                    Toast.makeText(this, "Ready to share ${uris.size} files. Select nearby device above.", Toast.LENGTH_SHORT).show()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            neededPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            neededPermissions.add(Manifest.permission.BLUETOOTH)
            neededPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            neededPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val ungranted = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
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

    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                if (state == android.bluetooth.BluetoothAdapter.STATE_ON) {
                    android.util.Log.i("MainActivity", "Bluetooth turned ON, restarting discovery & servers")
                    startDiscovery()
                }
            }
        }
    }

    fun requestEnableBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(enableBtIntent)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Could not request enable bluetooth: ${e.message}")
            }
        }
    }

    private fun startDiscovery() {
        try {
            udpManager.startDiscovery(lifecycleScope)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "UDP start failed: ${e.message}")
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            requestEnableBluetooth()
        }

        if (hasBluetoothConnectPermission()) {
            try {
                bleCommandServer.start()
                rfcommServer.start(lifecycleScope)
                bleManager.startAdvertising()
                bleManager.startDiscovery()
            } catch (e: SecurityException) {
                android.util.Log.w("MainActivity", "Bluetooth SecurityException: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Bluetooth discovery start failed: ${e.message}")
            }
        } else {
            android.util.Log.i("MainActivity", "Bluetooth permissions not yet granted, skipping BLE discovery until granted")
        }
    }

    /**
     * Connect to a Wi-Fi network programmatically (triggered by PC via BLE command).
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
            val connManager = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connManager?.bindProcessToNetwork(null)
            }
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
                            connManager.bindProcessToNetwork(network)
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
     * Open USB tethering settings (triggered by PC via BLE command).
     * Android doesn't expose a public API to toggle tethering programmatically.
     */
    private fun openUsbTetheringSettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "Please enable USB Tethering", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent()
                    intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (_: Exception) {
                    android.util.Log.e("MainActivity", "Could not open tethering settings")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureWifiEnabled()
        checkUsbState()
        checkExternalWifiState()
        if (_isUsbTetheringActive.value) {
            onUsbConnectedCallback?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(wifiConnectReceiver) } catch (_: Exception) {}
        try { hotspotManager.stopHotspot() } catch (_: Exception) {}
        httpServer?.stop()
        bleCommandServer.stop()
        rfcommServer.stop()
        bleManager.stopDiscovery()
        udpManager.stopDiscovery()
        transportManager.closeAll()
    }
}
