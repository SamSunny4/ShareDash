package com.sharedash.app.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
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
        val ipAddress: String = "192.168.43.1",
        val band: String = "5 GHz (Max Speed)",
        val qrData: String = "WIFI:T:WPA;S:$ssid;P:$password;;"
    ) : HotspotState()
    data class Error(val message: String) : HotspotState()
}

class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    fun hasHotspotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun start5GHzHotspot(onSuccess: ((ssid: String, password: String) -> Unit)? = null) {
        if (wifiManager == null) {
            _hotspotState.value = HotspotState.Error("Wi-Fi Hardware not available")
            return
        }

        if (!hasHotspotPermission()) {
            _hotspotState.value = HotspotState.Error("Wi-Fi / Location permission required for Hotspot")
            return
        }

        if (_hotspotState.value is HotspotState.Active) {
            return
        }

        _hotspotState.value = HotspotState.Starting
        Log.i(TAG, "Starting Local-Only Hotspot...")

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation
                        val (ssid, pass) = extractCredentials(reservation)

                        _hotspotState.value = HotspotState.Active(
                            ssid = ssid,
                            password = pass,
                            ipAddress = "192.168.43.1",
                            band = "5 GHz High-Speed Hotspot"
                        )
                        Log.i(TAG, "Hotspot ACTIVE: SSID='$ssid', Password='$pass', IP=192.168.43.1")
                        onSuccess?.invoke(ssid, pass)
                    }

                    override fun onStopped() {
                        super.onStopped()
                        hotspotReservation = null
                        _hotspotState.value = HotspotState.Idle
                        Log.i(TAG, "Hotspot stopped")
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "startLocalOnlyHotspot failed with reason code: $reason")
                        _hotspotState.value = HotspotState.Error("Hotspot unavailable (code $reason). Please use Mobile Hotspot.")
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local hotspot: ${e.message}")
            _hotspotState.value = HotspotState.Error("Hotspot error: ${e.message}")
        }
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

    fun stopHotspot() {
        try {
            hotspotReservation?.close()
            hotspotReservation = null
            _hotspotState.value = HotspotState.Idle
            Log.i(TAG, "Hotspot closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing hotspot: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HotspotManager"
    }
}
