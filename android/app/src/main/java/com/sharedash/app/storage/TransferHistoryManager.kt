package com.sharedash.app.storage

import android.content.Context
import android.content.SharedPreferences
import com.sharedash.app.model.TransferDirection
import com.sharedash.app.model.TransferRecord
import com.sharedash.app.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TransferHistoryManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _historyFlow = MutableStateFlow<List<TransferRecord>>(emptyList())
    val historyFlow: StateFlow<List<TransferRecord>> = _historyFlow.asStateFlow()

    init {
        loadHistory()
    }

    @Synchronized
    private fun loadHistory() {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TransferRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TransferRecord(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        fileSize = obj.optLong("fileSize"),
                        timestamp = obj.optLong("timestamp"),
                        direction = TransferDirection.valueOf(obj.optString("direction", TransferDirection.RECEIVED.name)),
                        status = TransferStatus.valueOf(obj.optString("status", TransferStatus.COMPLETED.name)),
                        peerName = obj.optString("peerName", "ShareDash PC"),
                        transportUsed = obj.optString("transportUsed", "USB Fast-Path"),
                        speedMbps = obj.optDouble("speedMbps", 0.0),
                        filePath = obj.optString("filePath").takeIf { it.isNotBlank() }
                    )
                )
            }
            _historyFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("TransferHistoryManager", "Error parsing history: ${e.message}")
        }
    }

    @Synchronized
    private fun saveHistory(list: List<TransferRecord>) {
        try {
            val array = JSONArray()
            for (record in list) {
                val obj = JSONObject().apply {
                    put("id", record.id)
                    put("fileName", record.fileName)
                    put("fileSize", record.fileSize)
                    put("timestamp", record.timestamp)
                    put("direction", record.direction.name)
                    put("status", record.status.name)
                    put("peerName", record.peerName)
                    put("transportUsed", record.transportUsed)
                    put("speedMbps", record.speedMbps)
                    put("filePath", record.filePath ?: "")
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
            _historyFlow.value = list
        } catch (e: Exception) {
            android.util.Log.e("TransferHistoryManager", "Error saving history: ${e.message}")
        }
    }

    @Synchronized
    fun addRecord(record: TransferRecord) {
        val current = _historyFlow.value.toMutableList()
        // Prepend to show newest first
        current.add(0, record)
        // Keep max 200 items in history
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        saveHistory(current)
    }

    @Synchronized
    fun deleteRecord(id: String) {
        val current = _historyFlow.value.filter { it.id != id }
        saveHistory(current)
    }

    @Synchronized
    fun clearAll() {
        saveHistory(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "sharedash_transfer_history"
        private const val KEY_HISTORY = "history_records"

        @Volatile
        private var instance: TransferHistoryManager? = null

        fun getInstance(context: Context): TransferHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: TransferHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
