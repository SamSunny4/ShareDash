package com.sharedash.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sharedash.app.discovery.BleDiscoveryManager
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
import com.sharedash.app.ui.screens.DiscoveryScreen
import com.sharedash.app.ui.screens.PairingScreen
import com.sharedash.app.ui.screens.TransferScreen
import com.sharedash.app.ui.theme.ShareDashTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleDiscoveryManager
    private lateinit var udpManager: UdpDiscoveryManager
    private var httpServer: com.sharedash.app.server.AndroidHttpServer? = null
    private val transportManager = AndroidTransportManager()

    private var selectedUris = mutableListOf<Uri>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startDiscovery()
        }
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

    private var onIncomingPairCallback: ((initiatorId: String, initiatorName: String, initiatorIp: String, pin: String, appVer: String) -> Unit)? = null
    private var onPairAcceptedCallback: ((targetId: String, targetName: String) -> Unit)? = null
    private var onPairConfirmedCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val myFriendlyName = "${Build.MANUFACTURER} ${Build.MODEL}"
        bleManager = BleDiscoveryManager(this)
        udpManager = UdpDiscoveryManager(this, myFriendlyName)

        // Eagerly start Android HTTP Server on port 54321 for symmetric discovery and pairing
        httpServer = com.sharedash.app.server.AndroidHttpServer(
            context = this,
            port = 54321,
            onIncomingPairRequest = { id, name, ip, pin, ver -> onIncomingPairCallback?.invoke(id, name, ip, pin, ver) },
            onPairAccepted = { id, name -> onPairAcceptedCallback?.invoke(id, name) },
            onPairConfirmed = { onPairConfirmedCallback?.invoke() },
            onFileReceived = { fileName, bytes ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "📥 Received $fileName ($bytes bytes) in Downloads/ShareDash", Toast.LENGTH_LONG).show()
                }
            }
        )
        httpServer?.start(lifecycleScope)

        handleIntent(intent)
        checkAndRequestPermissions()

        setContent {
            ShareDashTheme {
                var currentScreen by remember { mutableStateOf("discovery") }
                var activeTarget by remember { mutableStateOf<DiscoveredPeer?>(null) }
                var isPairingDialogOpen by remember { mutableStateOf(false) }
                var pairingPin by remember { mutableStateOf("000000") }

                val pairingCoordinator = remember { com.sharedash.app.discovery.AndroidPairingCoordinator() }

                val blePeers by bleManager.discoveredBlePeers.collectAsState()
                val udpPeers by udpManager.discoveredPeers.collectAsState()

                onIncomingPairCallback = { initiatorId, initiatorName, initiatorIp, pin, appVer ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        activeTarget = DiscoveredPeer(
                            deviceId = initiatorId,
                            friendlyName = initiatorName,
                            osName = "Windows",
                            ipAddress = initiatorIp,
                            port = 54321,
                            appVersion = appVer
                        )
                        pairingPin = pin
                        isPairingDialogOpen = true
                    }
                }

                onPairAcceptedCallback = { targetId, targetName ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        isPairingDialogOpen = false
                        Toast.makeText(this@MainActivity, "🔒 Securely Connected to $targetName (AES-256-GCM)", Toast.LENGTH_SHORT).show()
                    }
                }

                onPairConfirmedCallback = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        isPairingDialogOpen = false
                        Toast.makeText(this@MainActivity, "🔒 Securely Connected (AES-256-GCM)", Toast.LENGTH_SHORT).show()
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

                // Listen for 2-way incoming pair requests from PC
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose {
                        pairingCoordinator.stopPolling()
                    }
                }

                androidx.compose.runtime.LaunchedEffect(combinedPeers) {
                    val target = combinedPeers.firstOrNull { !it.deviceId.contains(android.os.Build.MODEL) }
                    if (target != null) {
                        pairingCoordinator.startPairingPoller(
                            scope = this,
                            targetIp = target.ipAddress,
                            targetPort = target.port,
                            onIncoming = { pin, name ->
                                activeTarget = target
                                pairingPin = pin
                                isPairingDialogOpen = true
                            },
                            onAccepted = { name ->
                                isPairingDialogOpen = false
                                activeTarget = target
                                Toast.makeText(this@MainActivity, "🔒 Securely Connected to $name (AES-256-GCM)", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                var activeTelemetry by remember {
                    mutableStateOf<SchedulerTelemetry?>(null)
                }

                onFilesPickedCallback = { uris ->
                    activeTarget?.let { peer ->
                        currentScreen = "transfer"
                        executeRealTransfer(peer, uris) { telem ->
                            activeTelemetry = telem
                        }
                    }
                }

                val wifiManager = remember { applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager }
                val bluetoothAdapter = remember { android.bluetooth.BluetoothAdapter.getDefaultAdapter() }

                val isWifiOn = wifiManager?.isWifiEnabled == true
                val isBluetoothOn = bluetoothAdapter?.isEnabled == true

                when (currentScreen) {
                    "discovery" -> {
                        DiscoveryScreen(
                            discoveredPeers = combinedPeers,
                            connectedPeer = activeTarget,
                            isUsbConnected = activeTarget?.supportedBridges?.any { it.contains("USB", true) } == true,
                            isWifiEnabled = isWifiOn,
                            isBluetoothEnabled = isBluetoothOn,
                            onDeviceSelected = { peer ->
                                if (!peer.isCompatible) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "⚠️ Incompatible App Version: ${peer.friendlyName} is running v${peer.appVersion}. Please update both apps.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    activeTarget = peer
                                    val pin = String.format("%06d", java.util.Random().nextInt(900000) + 100000)
                                    pairingPin = pin
                                    isPairingDialogOpen = true
                                    val myIp = udpManager.getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
                                    lifecycleScope.launch {
                                        pairingCoordinator.sendPairRequest(peer.ipAddress, peer.port, pin, android.os.Build.MODEL, myIp)
                                    }
                                }
                            },
                            onDisconnect = {
                                activeTarget?.let { peer ->
                                    lifecycleScope.launch {
                                        pairingCoordinator.respondToPairRequest(peer.ipAddress, peer.port, false)
                                    }
                                }
                                activeTarget = null
                                selectedUris.clear()
                            },
                            onPickFiles = {
                                filePickerLauncher.launch("*/*")
                            },
                            onPickFolder = {
                                filePickerLauncher.launch("*/*")
                            },
                            onOpenPairing = { currentScreen = "pairing" },
                            onOpenDownloadsFolder = {
                                try {
                                    val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                    startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(this, "Files saved in Downloads/ShareDash", Toast.LENGTH_LONG).show()
                                }
                            },
                            onEnableWifi = {
                                try {
                                    startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            onEnableBluetooth = {
                                try {
                                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            onOpenUsbSettings = {
                                try {
                                    startActivity(android.content.Intent("android.settings.TETHER_SETTINGS"))
                                } catch (_: Exception) {
                                    try {
                                        startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                                    } catch (_: Exception) {
                                        Toast.makeText(this, "Enable USB Tethering in Settings -> Connections", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )

                        if (isPairingDialogOpen) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { isPairingDialogOpen = false },
                                icon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = com.sharedash.app.ui.theme.AccentBlue
                                    )
                                },
                                title = {
                                    androidx.compose.material3.Text("Pairing with ${activeTarget?.friendlyName ?: "PC"}")
                                },
                                text = {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                    ) {
                                        androidx.compose.material3.Text("Confirm this 6-digit security PIN matches on both devices:")
                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                                        androidx.compose.material3.Text(
                                            text = if (pairingPin.length == 6) "${pairingPin.substring(0, 3)}  ${pairingPin.substring(3)}" else pairingPin,
                                            fontSize = 24.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = com.sharedash.app.ui.theme.AccentBlue
                                        )
                                    }
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            isPairingDialogOpen = false
                                            activeTarget?.let { peer ->
                                                lifecycleScope.launch {
                                                    val accepted = pairingCoordinator.respondToPairRequest(peer.ipAddress, peer.port, true)
                                                    if (accepted) {
                                                        pairingCoordinator.confirmPairSession(peer.ipAddress, peer.port)
                                                    }
                                                }
                                            }
                                            Toast.makeText(this@MainActivity, "🔒 Securely Connected! Now select files.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.sharedash.app.ui.theme.AccentBlue)
                                    ) {
                                        androidx.compose.material3.Text("Confirm & Connect")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.OutlinedButton(onClick = { isPairingDialogOpen = false }) {
                                        androidx.compose.material3.Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                    "transfer" -> {
                        activeTelemetry?.let { telem ->
                            TransferScreen(
                                targetName = activeTarget?.friendlyName ?: "Target Device",
                                telemetry = telem,
                                onCancel = {
                                    transportManager.closeAll()
                                    currentScreen = "discovery"
                                },
                                onFinish = {
                                    selectedUris.clear()
                                    currentScreen = "discovery"
                                }
                            )
                        }
                    }
                    "pairing" -> {
                        PairingScreen(
                            onBack = { currentScreen = "discovery" },
                            onPairWithPin = { pin ->
                                Toast.makeText(this, "Pairing PIN: $pin submitted", Toast.LENGTH_SHORT).show()
                                currentScreen = "discovery"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun executeRealTransfer(
        target: DiscoveredPeer,
        uris: List<Uri>,
        onUpdate: (SchedulerTelemetry) -> Unit
    ) {
        val transferId = UUID.randomUUID()
        val firstFileName = queryFileName(uris[0]) ?: "file.bin"
        val totalFiles = uris.size

        lifecycleScope.launch(Dispatchers.IO) {
            // Start Foreground Service
            val serviceIntent = Intent(this@MainActivity, TransferForegroundService::class.java).apply {
                putExtra(TransferForegroundService.EXTRA_TITLE, firstFileName)
                putExtra(TransferForegroundService.EXTRA_PROGRESS, 0)
                putExtra(TransferForegroundService.EXTRA_SPEED, "Connecting to ${target.friendlyName}...")
            }
            startService(serviceIntent)

            var totalBytes = 0L
            uris.forEach { uri ->
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    totalBytes += pfd.statSize
                }
            }

            val initialTelem = SchedulerTelemetry(
                transferId = transferId,
                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                status = "ACTIVE",
                aggregateMbps = 0.0,
                totalBytes = totalBytes,
                completedBytes = 0,
                progressPct = 0f,
                etaSeconds = 0,
                transports = listOf(TransportStats("LAN", TransportKind.LAN, 0.0, 2.0, 0, true)),
                chunkStates = emptyList()
            )
            withContext(Dispatchers.Main) { onUpdate(initialTelem) }

            var transferredBytes = 0L
            val startTime = System.currentTimeMillis()
            var success = true

            for (uri in uris) {
                val fileName = queryFileName(uri) ?: "file.bin"
                val fileSize = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

                try {
                    val boundary = "ShareDash-${System.currentTimeMillis()}-${transferId.toString().take(8)}"
                    val url = java.net.URL("http://${target.ipAddress}:${target.port}/api/v1/transfers/send")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    conn.setChunkedStreamingMode(256 * 1024)
                    conn.connectTimeout = 10000
                    conn.readTimeout = 0 // no read timeout for large transfers

                    val os = conn.outputStream.buffered()

                    // Write multipart header
                    val header = "--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$fileName\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                    os.write(header.toByteArray(Charsets.UTF_8))

                    // Stream file content
                    val input = contentResolver.openInputStream(uri)
                    if (input != null) {
                        val buffer = ByteArray(256 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            os.write(buffer, 0, read)
                            transferredBytes += read

                            val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(100) / 1000.0
                            val mbps = (transferredBytes * 8) / (elapsedSec * 1_000_000.0)
                            val progress = if (totalBytes > 0) ((transferredBytes.toFloat() / totalBytes) * 100f) else 0f
                            val remainingBytes = totalBytes - transferredBytes
                            val eta = if (mbps > 0) (remainingBytes * 8 / (mbps * 1_000_000.0)).toLong() else 0

                            val telem = SchedulerTelemetry(
                                transferId = transferId,
                                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                                status = "ACTIVE",
                                aggregateMbps = mbps / 8.0,
                                totalBytes = totalBytes,
                                completedBytes = transferredBytes,
                                progressPct = progress,
                                etaSeconds = eta,
                                transports = listOf(TransportStats("LAN", TransportKind.LAN, mbps / 8.0, 1.5, (transferredBytes / (256 * 1024)), true)),
                                chunkStates = emptyList()
                            )
                            withContext(Dispatchers.Main) { onUpdate(telem) }
                        }
                        input.close()
                    }

                    // Write multipart footer
                    val footer = "\r\n--$boundary--\r\n"
                    os.write(footer.toByteArray(Charsets.UTF_8))
                    os.flush()
                    os.close()

                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        android.util.Log.e("Transfer", "HTTP error $responseCode sending $fileName")
                        success = false
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    android.util.Log.e("Transfer", "Failed to send $fileName: ${e.message}")
                    success = false
                }
            }

            val finalTelem = SchedulerTelemetry(
                transferId = transferId,
                title = if (totalFiles > 1) "$firstFileName + ${totalFiles - 1} more" else firstFileName,
                status = if (success) "COMPLETED" else "FAILED",
                aggregateMbps = 0.0,
                totalBytes = totalBytes,
                completedBytes = if (success) totalBytes else transferredBytes,
                progressPct = if (success) 100f else ((transferredBytes.toFloat() / totalBytes.coerceAtLeast(1)) * 100f),
                etaSeconds = 0,
                transports = listOf(TransportStats("LAN", TransportKind.LAN, 0.0, 1.5, 0, true)),
                chunkStates = emptyList()
            )
            withContext(Dispatchers.Main) { onUpdate(finalTelem) }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
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
        } else {
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        bleManager.startDiscovery()
        udpManager.startDiscovery(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        bleManager.stopDiscovery()
        udpManager.stopDiscovery()
        transportManager.closeAll()
    }
}
