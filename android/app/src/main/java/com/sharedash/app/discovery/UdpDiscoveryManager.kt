package com.sharedash.app.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.sharedash.app.model.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

class UdpDiscoveryManager(
    private val context: Context,
    private val deviceName: String,
    private val serverPort: Int = 54321
) {
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val peerMap = mutableMapOf<String, DiscoveredPeer>()
    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startDiscovery(scope: CoroutineScope) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("ShareDashUdpLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        discoveryJob = scope.launch(Dispatchers.IO) {
            // 1. UDP Broadcast & Listener
            launch { listenForBeacons() }
            launch { broadcastLoop() }

            // 2. Localhost & Subnet HTTP Probe (guarantees USB ADB & router isolation bypass)
            launch { subnetHttpProbeLoop() }

            // 3. Stale Peer Check
            launch { staleCheckLoop() }
        }
    }

    private suspend fun broadcastLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true

            while (coroutineContext.isActive) {
                val json = JSONObject().apply {
                    put("device_id", "android-" + android.os.Build.MODEL.replace(" ", "-"))
                    put("friendly_name", deviceName)
                    put("os_name", "Android " + android.os.Build.VERSION.RELEASE)
                    put("server_port", serverPort)
                    put("app_version", CURRENT_APP_VERSION)
                    put("supported_transports", org.json.JSONArray(listOf("Wi-Fi Direct", "LAN")))
                    put("timestamp_ms", System.currentTimeMillis())
                }

                val bytes = json.toString().toByteArray()

                val broadcastTargets = mutableListOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("192.168.42.255"),
                    InetAddress.getByName("192.168.1.255"),
                    InetAddress.getByName("192.168.0.255"),
                    InetAddress.getByName("172.20.10.255")
                )

                // Add active interface broadcast addresses
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (!iface.isLoopback && iface.isUp) {
                            for (addr in iface.interfaceAddresses) {
                                addr.broadcast?.let { broadcastTargets.add(it) }
                            }
                        }
                    }
                } catch (_: Exception) {}

                for (target in broadcastTargets.distinct()) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, target, DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (_: Exception) {}
                }

                delay(2000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast error: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private suspend fun listenForBeacons() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(DISCOVERY_PORT)
            socket.broadcast = true
            val buffer = ByteArray(4096)

            while (coroutineContext.isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val text = String(packet.data, 0, packet.length)
                try {
                    val json = JSONObject(text)
                    val devId = json.optString("device_id", "")
                    val name = json.optString("friendly_name", "Nearby Device")
                    val os = json.optString("os_name", "Windows")
                    val port = json.optInt("server_port", 54321)
                    val appVer = json.optString("app_version", "0.1.0")
                    val isCompat = isVersionCompatible(appVer)
                    val ip = packet.address.hostAddress ?: "127.0.0.1"

                    val myModel = android.os.Build.MODEL
                    val myDeviceId = "android-" + myModel.replace(" ", "-")
                    val myIps = getLocalIpAddresses()

                    // Strict self-detection filter: ignore own beacons or own IPs
                    if (devId == myDeviceId || devId.contains(myModel, ignoreCase = true) || name == deviceName || ip in myIps || (ip == "127.0.0.1" && devId.startsWith("android"))) {
                        continue
                    }

                    val bridges = mutableListOf("Wi-Fi Direct", "LAN")
                    if (ip == "127.0.0.1" || ip.startsWith("192.168.42.")) {
                        bridges.add(0, "USB 3.2")
                    }

                    val peer = DiscoveredPeer(
                        deviceId = devId,
                        friendlyName = name,
                        osName = os,
                        ipAddress = ip,
                        port = port,
                        appVersion = appVer,
                        isCompatible = isCompat,
                        supportedBridges = bridges,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )

                    peerMap[devId] = peer
                    _discoveredPeers.value = peerMap.values.toList()
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed beacon JSON: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP listener error: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private suspend fun subnetHttpProbeLoop() = coroutineScope {
        val semaphore = Semaphore(32)
        while (isActive) {
            // Check localhost USB bridge first (127.0.0.1:54321 / 10.0.2.2 for emulator)
            probeIpAddress("127.0.0.1", 54321, "USB Cable")
            probeIpAddress("10.0.2.2", 54321, "USB / Host")

            // Probe default gateways and common Wi-Fi IP ranges
            val myIps = getLocalIpAddresses()
            for (myIp in myIps) {
                val parts = myIp.split(".")
                if (parts.size == 4) {
                    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                    // Probe common PC host addresses on this subnet
                    for (i in 1..254) {
                        val targetIp = "$prefix.$i"
                        if (targetIp != myIp) {
                            launch {
                                semaphore.withPermit {
                                    probeIpAddress(targetIp, 54321, "Wi-Fi LAN")
                                }
                            }
                        }
                    }
                }
            }

            delay(4000)
        }
    }

    private suspend fun staleCheckLoop() {
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val keysToRemove = peerMap.filterValues { now - it.lastSeenTimestamp > 15000 }.keys
            if (keysToRemove.isNotEmpty()) {
                keysToRemove.forEach { peerMap.remove(it) }
                _discoveredPeers.value = peerMap.values.toList()
            }
            delay(5000)
        }
    }

    private fun probeIpAddress(ip: String, port: Int, transportBadge: String) {
        try {
            val url = URL("http://$ip:$port/api/v1/info")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 400
            conn.readTimeout = 500
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()

                val json = JSONObject(resp)
                val devId = json.optString("device_id", ip)
                val name = json.optString("device_name", "ShareDash PC")
                val os = json.optString("os_name", "Windows")
                val appVer = json.optString("app_version", "0.1.0")
                val isCompat = isVersionCompatible(appVer)

                val myModel = android.os.Build.MODEL
                val myDeviceId = "android-" + myModel.replace(" ", "-")
                val myIps = getLocalIpAddresses()

                // Strict self-detection filter: ignore own HTTP server on phone
                if (devId == myDeviceId || devId.contains(myModel, ignoreCase = true) || name == deviceName || ip in myIps || (ip == "127.0.0.1" && devId.startsWith("android"))) {
                    return
                }

                val peer = DiscoveredPeer(
                    deviceId = devId,
                    friendlyName = name,
                    osName = os,
                    ipAddress = ip,
                    port = port,
                    appVersion = appVer,
                    isCompatible = isCompat,
                    supportedBridges = listOf(transportBadge, "Wi-Fi"),
                    lastSeenTimestamp = System.currentTimeMillis()
                )

                peerMap[devId] = peer
                _discoveredPeers.value = peerMap.values.toList()
                Log.i(TAG, "Discovered remote device via HTTP Probe: $name at $ip:$port")

                // Immediately announce phone to PC's UDP discovery port 54320
                try {
                    val announceJson = JSONObject().apply {
                        put("device_id", myDeviceId)
                        put("friendly_name", deviceName)
                        put("os_name", "Android " + android.os.Build.VERSION.RELEASE)
                        put("server_port", 54321)
                        put("app_version", CURRENT_APP_VERSION)
                        put("supported_transports", org.json.JSONArray(listOf("Wi-Fi Direct", "LAN")))
                        put("timestamp_ms", System.currentTimeMillis())
                    }
                    val bytes = announceJson.toString().toByteArray()
                    val targetSocket = DatagramSocket()
                    val targetPacket = DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), DISCOVERY_PORT)
                    targetSocket.send(targetPacket)
                    targetSocket.close()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun getLocalIpAddresses(): List<String> {
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

    fun stopDiscovery() {
        discoveryJob?.cancel()
        multicastLock?.release()
    }

    companion object {
        private const val TAG = "UdpDiscoveryManager"
        const val DISCOVERY_PORT = 54320
        const val CURRENT_APP_VERSION = "0.1.0"
        const val MIN_SUPPORTED_APP_VERSION = "0.1.0"

        fun isVersionCompatible(peerVersion: String): Boolean {
            if (peerVersion.isBlank()) return true
            val peerParts = peerVersion.split(".").mapNotNull { it.toIntOrNull() }
            val minParts = MIN_SUPPORTED_APP_VERSION.split(".").mapNotNull { it.toIntOrNull() }
            if (peerParts.size >= 2 && minParts.size >= 2) {
                if (peerParts[0] != minParts[0]) return false
                return peerParts[1] >= minParts[1]
            }
            return true
        }
    }
}
