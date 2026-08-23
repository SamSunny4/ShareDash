package com.sharedash.app.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
        Log.i(TAG, "🚀 Initiating Standard 5GHz SoftAP / Hotspot (Android LocalOnlyHotspot)...")

        // 1. Primary: Standard Android LocalOnlyHotspot (Standard 802.11 AP that Windows connects to reliably)
        startLocalOnlyHotspot(onSuccess)
    }

    @SuppressLint("MissingPermission")
    private fun startLocalOnlyHotspot(onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)? = null) {
        if (wifiManager == null) {
            _hotspotState.value = HotspotState.Error("Wi-Fi Hardware not available")
            return
        }

        // Disconnect client Wi-Fi to free radio channels
        try {
            wifiManager.disconnect()
        } catch (_: Exception) {}

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation
                        val (ssid, pass) = extractCredentials(reservation)
                        val gatewayIp = getHotspotGatewayIp()

                        _hotspotState.value = HotspotState.Active(
                            ssid = ssid,
                            password = pass,
                            ipAddress = gatewayIp,
                            band = "5 GHz High-Speed Hotspot"
                        )
                        Log.i(TAG, "Hotspot ACTIVE: SSID='$ssid', Password='$pass', IP=$gatewayIp")
                        onSuccess?.invoke(ssid, pass, gatewayIp)
                    }

                    override fun onStopped() {
                        super.onStopped()
                        hotspotReservation = null
                        _hotspotState.value = HotspotState.Idle
                        Log.i(TAG, "Hotspot stopped")
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "startLocalOnlyHotspot failed with reason code: $reason. Trying P2P fallback...")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && p2pManager != null && p2pChannel != null) {
                            start5GHzP2pGroup(onSuccess)
                        } else {
                            _hotspotState.value = HotspotState.Error("Hotspot unavailable (code $reason).")
                        }
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local hotspot: ${e.message}. Trying P2P fallback...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && p2pManager != null && p2pChannel != null) {
                start5GHzP2pGroup(onSuccess)
            } else {
                _hotspotState.value = HotspotState.Error("Hotspot error: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun start5GHzP2pGroup(onSuccess: ((ssid: String, password: String, gateway: String) -> Unit)? = null) {
        val channel = p2pChannel ?: run {
            startLocalOnlyHotspot(onSuccess)
            return
        }
        val mgr = p2pManager ?: run {
            startLocalOnlyHotspot(onSuccess)
            return
        }

        // Clean up any stale P2P groups first
        mgr.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                create5GHzP2pGroupActual(mgr, channel, onSuccess)
            }
            override fun onFailure(reason: Int) {
                create5GHzP2pGroupActual(mgr, channel, onSuccess)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun create5GHzP2pGroupActual(
        mgr: WifiP2pManager,
        channel: WifiP2pManager.Channel,
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
                        val gatewayIp = "192.168.49.1"
                        Log.i(TAG, "🎉 5GHz Wi-Fi Direct Group CREATED! SSID='$ssidName', Passphrase='$passphrase', Gateway=$gatewayIp")
                        _hotspotState.value = HotspotState.Active(
                            ssid = ssidName,
                            password = passphrase,
                            ipAddress = gatewayIp,
                            band = "5 GHz Direct (Wi-Fi 6 P2P)"
                        )
                        onSuccess?.invoke(ssidName, passphrase, gatewayIp)
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "5GHz Wi-Fi Direct createGroup failed (code $reason).")
                        _hotspotState.value = HotspotState.Error("5GHz Wi-Fi Direct failed.")
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Exception creating 5GHz P2P group: ${e.message}.")
            }
        }
    }

    /**
     * Dynamically detect the gateway IP of the active AP/tethering network interface.
     */
    fun getHotspotGatewayIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return "192.168.49.1"
            val ifaceList = interfaces.toList()
            // 1. Look specifically for AP / Tethering / Hotspot interfaces
            for (iface in ifaceList) {
                val name = iface.name.lowercase()
                if (name.contains("p2p") || name.contains("ap") || name.contains("wlan1") || name.contains("swlan") || name.contains("softap") || name.contains("rndis") || name.contains("tether")) {
                    for (addr in iface.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            }

            // 2. Check for standard subnet IP patterns
            for (iface in ifaceList) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.49.") || host.startsWith("192.168.43.") || host.startsWith("172.20.10."))) {
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
