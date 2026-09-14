package com.sharedash.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _deviceNameFlow = MutableStateFlow(
        prefs.getString(KEY_DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}") ?: "${Build.MANUFACTURER} ${Build.MODEL}"
    )
    val deviceNameFlow: StateFlow<String> = _deviceNameFlow.asStateFlow()

    private val _connectionModeFlow = MutableStateFlow(
        prefs.getString(KEY_CONNECTION_MODE, MODE_PHONE_TO_PC) ?: MODE_PHONE_TO_PC
    )
    val connectionModeFlow: StateFlow<String> = _connectionModeFlow.asStateFlow()

    private val _preferUsbFlow = MutableStateFlow(prefs.getBoolean(KEY_PREFER_USB, true))
    val preferUsbFlow: StateFlow<Boolean> = _preferUsbFlow.asStateFlow()

    private val _prefer5GHzFlow = MutableStateFlow(prefs.getBoolean(KEY_PREFER_5GHZ, true))
    val prefer5GHzFlow: StateFlow<Boolean> = _prefer5GHzFlow.asStateFlow()

    private val _serverPortFlow = MutableStateFlow(prefs.getInt(KEY_PORT, 54321))
    val serverPortFlow: StateFlow<Int> = _serverPortFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    )
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    var deviceName: String
        get() = _deviceNameFlow.value
        set(value) {
            val v = value.trim().ifEmpty { "${Build.MANUFACTURER} ${Build.MODEL}" }
            prefs.edit().putString(KEY_DEVICE_NAME, v).apply()
            _deviceNameFlow.value = v
        }

    var connectionMode: String
        get() = _connectionModeFlow.value
        set(value) {
            prefs.edit().putString(KEY_CONNECTION_MODE, value).apply()
            _connectionModeFlow.value = value
        }

    var preferUsb: Boolean
        get() = _preferUsbFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_PREFER_USB, value).apply()
            _preferUsbFlow.value = value
        }

    var prefer5GHz: Boolean
        get() = _prefer5GHzFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_PREFER_5GHZ, value).apply()
            _prefer5GHzFlow.value = value
        }

    var serverPort: Int
        get() = _serverPortFlow.value
        set(value) {
            prefs.edit().putInt(KEY_PORT, value).apply()
            _serverPortFlow.value = value
        }

    var themeMode: String
        get() = _themeModeFlow.value
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value).apply()
            _themeModeFlow.value = value
        }

    companion object {
        const val MODE_PHONE_TO_PC = "phone_to_pc"
        const val MODE_PHONE_TO_PHONE = "phone_to_phone"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        private const val PREFS_NAME = "sharedash_settings"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_CONNECTION_MODE = "connection_mode"
        private const val KEY_PREFER_USB = "prefer_usb"
        private const val KEY_PREFER_5GHZ = "prefer_5ghz"
        private const val KEY_PORT = "server_port"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
