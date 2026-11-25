package com.sharedash.app.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random

sealed class HotspotState {
    object Idle : HotspotState()
    object Starting : HotspotState()
    data class Active(
        val ssid: String,
        val password: String,
        val ipAddress: String = "192.168.49.1",
        val band: String = "5 GHz (Quick Share Direct)",
        val qrData: String = "WIFI:T:WPA;S:$ssid;P:$password;;"
    ) : HotspotState()
    data class Error(val message: String) : HotspotState()
}

class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val p2pManager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var isP2pGroupActive = false

    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    init {
        try {
            p2pChannel = p2pManager?.initialize(context.applicationContext, context.mainLooper, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize WifiP2pManager: ${e.message}")
        }
    }

    /**
     * Check whether device is currently connected to an active Wi-Fi AP.
     */
    fun isWifiConnected(): Boolean {
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return connectivityManager.allNetworks.any { network ->
                    connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
            }
            @Suppress("DEPRECATION")
            val info = wifiManager?.connectionInfo
            return info != null && info.networkId != -1 && info.supplicantState == SupplicantState.COMPLETED
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Wi-Fi connection state: ${e.message}")
            return false
        }
    }

    /**
     * Retrieves the connected Wi-Fi SSID, stripping quotes.
     * Returns null if not connected, or if SSID is unknown/empty.
     */
    fun getConnectedWifiSsid(): String? {
        return try {
            @Suppress("DEPRECATION")
            val info = wifiManager?.connectionInfo
            val rawSsid = info?.ssid?.replace("\"", "")?.trim()
            if (!rawSsid.isNullOrEmpty() && rawSsid != "<unknown ssid>" && rawSsid != "0x") {
                rawSsid
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if connected to an external Wi-Fi network (i.e. not ShareDash Hotspot or Direct group).
     */
    fun isConnectedToExternalWifi(): Boolean {
        if (!isWifiConnected()) return false
        val ssid = getConnectedWifiSsid() ?: return true
        val lower = ssid.lowercase()
        // Exclude ShareDash's own Hotspot or Wi-Fi Direct groups
        if (lower.startsWith("sharedash") || lower.startsWith("direct-")) {
            return false
        }
        return true
    }

    fun hasHotspotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun start5GHzHotspot(onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)? = null) {
        if (_hotspotState.value == HotspotState.Starting) return
        if (!hasHotspotPermission()) {
            _hotspotState.value = HotspotState.Error("Wi-Fi / Location permission required for Hotspot")
            return
        }

        if (_hotspotState.value is HotspotState.Active) {
            val active = _hotspotState.value as HotspotState.Active
            onSuccess?.invoke(active.ssid, active.password, active.ipAddress)
            return
        }

        _hotspotState.value = HotspotState.Starting
        Log.i(TAG, "Initiating 5GHz Wi-Fi Direct Autonomous Group (DIRECT-SD)...")


        // 2. Force-off any existing or lingering Wi-Fi Direct group / Hotspot state
        forceStopPreviousDirectGroup {
            // 3. Start fresh 5GHz P2P Group
            start5GHzP2pGroup(onSuccess)
        }
    }

    @SuppressLint("MissingPermission")
    private fun forceStopPreviousDirectGroup(onCleaned: () -> Unit) {
        val mgr = p2pManager
        val channel = p2pChannel ?: try {
            mgr?.initialize(context.applicationContext, context.mainLooper, null)?.also { p2pChannel = it }
        } catch (_: Exception) { null }

        try {
            hotspotReservation?.close()
            hotspotReservation = null
        } catch (_: Exception) {}

        if (mgr != null && channel != null) {
            try {
                mgr.cancelConnect(channel, null)
                mgr.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Previous Wi-Fi Direct group removed successfully.")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onCleaned() }, 200)
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "No previous group or remove failed (code $reason). Proceeding.")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onCleaned() }, 200)
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Error during forceStopPreviousDirectGroup: ${e.message}")
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onCleaned() }, 200)
    }

    @SuppressLint("MissingPermission")
    private fun start5GHzP2pGroup(onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)? = null) {
        val mgr = p2pManager ?: run {
            _hotspotState.value = HotspotState.Error("Wi-Fi P2P Hardware not available")
            return
        }
        if (p2pChannel == null) {
            try {
                p2pChannel = mgr.initialize(context.applicationContext, context.mainLooper, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize WifiP2pManager: ${e.message}")
            }
        }
        val channel = p2pChannel ?: run {
            _hotspotState.value = HotspotState.Error("Wi-Fi P2P Channel initialization failed")
            return
        }

        if (com.sharedash.app.storage.SettingsManager.getInstance(context).prefer5GHz) {
            create5GHzP2pGroupActual(mgr, channel, false, onSuccess)
        } else {
            createAutoP2pGroup(mgr, channel, onSuccess)
        }
    }

    @SuppressLint("MissingPermission")
    private fun create5GHzP2pGroupActual(
        mgr: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        isRetry: Boolean = false,
        onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val suffix = (1000..9999).random()
                val ssidName = "DIRECT-SD-$suffix"
                val passphrase = generateRandomPassphrase(10)

                val configBuilder = WifiP2pConfig.Builder()
                    .setNetworkName(ssidName)
                    .setPassphrase(passphrase)
                    .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)

                val config = configBuilder.build()

                mgr.createGroup(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isP2pGroupActive = true
                        mgr.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            val actualSsid = group?.networkName?.takeIf { it.isNotBlank() } ?: ssidName
                            val actualPass = group?.passphrase?.takeIf { it.isNotBlank() } ?: passphrase
                            val gatewayIp = getHotspotGatewayIp()
                            Log.i(TAG, "5GHz Wi-Fi Direct Group CREATED! SSID='$actualSsid', Gateway=$gatewayIp")
                            _hotspotState.value = HotspotState.Active(
                                ssid = actualSsid,
                                password = actualPass,
                                ipAddress = gatewayIp,
                                band = "5 GHz Direct (Wi-Fi 6 P2P)"
                            )
                            onSuccess?.invoke(actualSsid, actualPass, gatewayIp)
                        }
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "5GHz Wi-Fi Direct createGroup failed (code $reason, isRetry=$isRetry).")
                        if (!isRetry) {
                            Log.i(TAG, "Forcing Wi-Fi Direct teardown and retrying 5GHz createGroup...")
                            mgr.cancelConnect(channel, null)
                            mgr.removeGroup(channel, null)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                create5GHzP2pGroupActual(mgr, channel, true, onSuccess)
                            }, 350)
                        } else {
                            Log.w(TAG, "5GHz retry failed, falling back to Auto-Band P2P...")
                            createAutoP2pGroup(mgr, channel, onSuccess)
                        }
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Exception creating 5GHz P2P group: ${e.message}. Retrying with Auto-Band P2P...")
                createAutoP2pGroup(mgr, channel, onSuccess)
                return
            }
        }
        createAutoP2pGroup(mgr, channel, onSuccess)
    }

    @SuppressLint("MissingPermission")
    private fun createAutoP2pGroup(
        mgr: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)?
    ) {
        try {
            val suffix = (1000..9999).random()
            val ssidName = "DIRECT-SD-$suffix"
            val passphrase = generateRandomPassphrase(10)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = WifiP2pConfig.Builder()
                    .setNetworkName(ssidName)
                    .setPassphrase(passphrase)
                    .build()

                mgr.createGroup(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isP2pGroupActive = true
                        mgr.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            val actualSsid = group?.networkName?.takeIf { it.isNotBlank() } ?: ssidName
                            val actualPass = group?.passphrase?.takeIf { it.isNotBlank() } ?: passphrase
                            val gatewayIp = getHotspotGatewayIp()
                            Log.i(TAG, "Wi-Fi Direct Group CREATED! SSID='$actualSsid', Gateway=$gatewayIp")
                            _hotspotState.value = HotspotState.Active(
                                ssid = actualSsid,
                                password = actualPass,
                                ipAddress = gatewayIp,
                                band = "Wi-Fi Direct P2P"
                            )
                            onSuccess?.invoke(actualSsid, actualPass, gatewayIp)
                        }
                    }

                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Wi-Fi Direct createGroup failed (code $reason)")
                        _hotspotState.value = HotspotState.Error("Wi-Fi Direct unavailable (code $reason)")
                    }
                })
            } else {
                mgr.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isP2pGroupActive = true
                        mgr.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            val actualSsid = group?.networkName ?: "DIRECT-SD"
                            val actualPass = group?.passphrase ?: ""
                            val gatewayIp = getHotspotGatewayIp()
                            _hotspotState.value = HotspotState.Active(
                                ssid = actualSsid,
                                password = actualPass,
                                ipAddress = gatewayIp,
                                band = "Wi-Fi Direct P2P"
                            )
                            onSuccess?.invoke(actualSsid, actualPass, gatewayIp)
                        }
                    }

                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Legacy Wi-Fi Direct createGroup failed (code $reason)")
                        _hotspotState.value = HotspotState.Error("Wi-Fi Direct failed (code $reason)")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Auto P2P: ${e.message}")
            _hotspotState.value = HotspotState.Error("P2P error: ${e.message}")
        }
    }

    /**
     * Dynamically detect the gateway IP of the active Wi-Fi AP interface (strictly wireless).
     */
    fun getHotspotGatewayIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return "192.168.43.1"
            val ifaceList = interfaces.toList()
            // 1. Look specifically for wireless AP / SoftAP / P2P interfaces (exclude USB/RNDIS)
            for (iface in ifaceList) {
                val name = iface.name.lowercase()
                val isUsb = name.contains("rndis") || name.contains("tether") || name.contains("usb") || name.contains("rmnet")
                val isWirelessAp = (name.contains("p2p") || name.contains("ap") || name.contains("wlan1") || name.contains("swlan") || name.contains("softap")) && !isUsb
                if (isWirelessAp) {
                    for (addr in iface.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("127.") && !host.startsWith("10.100.")) {
                                return host
                            }
                        }
                    }
                }
            }

            // 2. Check for standard wireless subnet IP patterns (192.168.43.x, 192.168.49.x, 172.20.10.x)
            for (iface in ifaceList) {
                val name = iface.name.lowercase()
                if (iface.isLoopback || !iface.isUp || name.contains("rndis") || name.contains("tether") || name.contains("usb")) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.43.") || host.startsWith("192.168.49.") || host.startsWith("172.20.10."))) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return if (isP2pGroupActive) "192.168.49.1" else "192.168.43.1"
    }

    private fun extractCredentials(reservation: WifiManager.LocalOnlyHotspotReservation?): Pair<String, String> {
        if (reservation == null) return Pair("ShareDash-5G-Hotspot", "")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val softApConfig = reservation.softApConfiguration
                if (softApConfig != null) {
                    val s = softApConfig.ssid ?: "ShareDash-5G"
                    val p = softApConfig.passphrase ?: ""
                    return Pair(s.replace("\"", ""), p.replace("\"", ""))
                }
            }
        } catch (_: Throwable) {}

        try {
            @Suppress("DEPRECATION")
            val wifiConfig = reservation.wifiConfiguration
            if (wifiConfig != null) {
                val s = wifiConfig.SSID ?: "ShareDash-5G"
                val p = wifiConfig.preSharedKey ?: ""
                return Pair(s.replace("\"", ""), p.replace("\"", ""))
            }
        } catch (_: Throwable) {}

        return Pair("ShareDash-5G-Hotspot", "")
    }

    private fun generateRandomPassphrase(length: Int = 10): String {
        val chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = Random()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    fun stopHotspot() {
        try {
            if (isP2pGroupActive && p2pManager != null && p2pChannel != null) {
                p2pManager.removeGroup(p2pChannel, null)
                isP2pGroupActive = false
            }
            hotspotReservation?.close()
            hotspotReservation = null
            _hotspotState.value = HotspotState.Idle
            Log.i(TAG, "Hotspot / Wi-Fi Direct closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing hotspot: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HotspotManager"
    }
}
