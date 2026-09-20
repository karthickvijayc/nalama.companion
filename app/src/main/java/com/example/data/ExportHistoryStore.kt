package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ExportHistoryItem
import com.example.model.ExportStatus
import com.example.model.WriteMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ExportHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("health_export_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<ExportHistoryItem>>(emptyList())
    val history: StateFlow<List<ExportHistoryItem>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val jsonStr = prefs.getString(KEY_HISTORY_JSON, "[]") ?: "[]"
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ExportHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ExportHistoryItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        timestamp = obj.optLong("timestamp", 0L),
                        formattedDate = obj.optString("formattedDate", ""),
                        status = try { ExportStatus.valueOf(obj.optString("status", ExportStatus.SUCCESS.name)) } catch (_: Exception) { ExportStatus.SUCCESS },
                        writeMode = try { WriteMode.valueOf(obj.optString("writeMode", WriteMode.APPEND.name)) } catch (_: Exception) { WriteMode.APPEND },
                        folderPath = obj.optString("folderPath", ""),
                        recordsCount = obj.optInt("recordsCount", 0),
                        message = obj.optString("message", ""),
                        payloadPreviewCsv = obj.optString("payloadPreviewCsv", null),
                        isManualTrigger = obj.optBoolean("isManualTrigger", false),
                        sourceApp = obj.optString("sourceApp", "HealthConnect")
                    )
                )
            }
            _history.value = list
        } catch (_: Exception) {
            _history.value = emptyList()
        }
    }

    fun addHistoryItem(item: ExportHistoryItem) {
        val currentList = _history.value.toMutableList()
        currentList.add(0, item) // most recent first
        val trimmed = if (currentList.size > 50) currentList.subList(0, 50) else currentList
        _history.value = trimmed

        saveHistory(trimmed)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY_JSON).apply()
    }

    private fun saveHistory(list: List<ExportHistoryItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("formattedDate", item.formattedDate)
                put("status", item.status.name)
                put("writeMode", item.writeMode.name)
                put("folderPath", item.folderPath)
                put("recordsCount", item.recordsCount)
                put("message", item.message)
                item.payloadPreviewCsv?.let { put("payloadPreviewCsv", it) }
                put("isManualTrigger", item.isManualTrigger)
                put("sourceApp", item.sourceApp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY_JSON = "key_export_history_json"
    }
}

