package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ExportFormat
import com.example.model.SyncSourceApp
import com.example.model.TargetFolder
import com.example.model.WriteMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

data class AppSettings(
    val webhookUrl: String = "",
    val sourceApp: SyncSourceApp = SyncSourceApp.HEALTH_CONNECT,
    val hevyApiKey: String = "",
    val targetFolder: TargetFolder = TargetFolder.HEALTH_DATA,
    val customFolderPath: String = "nalama.family/imports/health_data",
    val exportFormat: ExportFormat = ExportFormat.CSV, // Default to CSV as requested
    val writeMode: WriteMode = WriteMode.APPEND,       // Default to Append as requested
    val syncIntervalMinutes: Int = 15,                 // Default to 15 mins as requested
    val autoSyncEnabled: Boolean = true,
    val timezoneId: String = "Asia/Kolkata",            // Matching schema timezone
    val demoModeEnabled: Boolean = false,              // Allows immediate testing in emulator
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = "Never synced",
    val lastSyncSuccess: Boolean? = null
)

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val formatStr = prefs.getString(KEY_FORMAT, ExportFormat.CSV.name) ?: ExportFormat.CSV.name
        val writeModeStr = prefs.getString(KEY_WRITE_MODE, WriteMode.APPEND.name) ?: WriteMode.APPEND.name
        val targetFolderStr = prefs.getString(KEY_TARGET_FOLDER, TargetFolder.HEALTH_DATA.name) ?: TargetFolder.HEALTH_DATA.name
        val sourceAppStr = prefs.getString(KEY_SOURCE_APP, SyncSourceApp.HEALTH_CONNECT.name) ?: SyncSourceApp.HEALTH_CONNECT.name

        val loadedSourceApp = try {
            SyncSourceApp.valueOf(sourceAppStr)
        } catch (_: Exception) {
            SyncSourceApp.HEALTH_CONNECT
        }

        val defaultFolder = loadedSourceApp.defaultTargetFolder

        return AppSettings(
            webhookUrl = prefs.getString(KEY_WEBHOOK_URL, "") ?: "",
            sourceApp = loadedSourceApp,
            hevyApiKey = prefs.getString(KEY_HEVY_API_KEY, "") ?: "",
            targetFolder = try { TargetFolder.valueOf(targetFolderStr) } catch (_: Exception) { defaultFolder },
            customFolderPath = prefs.getString(KEY_CUSTOM_FOLDER_PATH, defaultFolder.folderPath) ?: defaultFolder.folderPath,
            exportFormat = try { ExportFormat.valueOf(formatStr) } catch (_: Exception) { ExportFormat.CSV },
            writeMode = try { WriteMode.valueOf(writeModeStr) } catch (_: Exception) { WriteMode.APPEND },
            syncIntervalMinutes = prefs.getInt(KEY_SYNC_INTERVAL, 15),
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, true),
            timezoneId = prefs.getString(KEY_TIMEZONE, "Asia/Kolkata") ?: "Asia/Kolkata",
            demoModeEnabled = prefs.getBoolean(KEY_DEMO_MODE, false),
            lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TIME, 0L),
            lastSyncStatus = prefs.getString(KEY_LAST_SYNC_STATUS, "Never synced") ?: "Never synced",
            lastSyncSuccess = if (prefs.contains(KEY_LAST_SYNC_SUCCESS)) prefs.getBoolean(KEY_LAST_SYNC_SUCCESS, false) else null
        )
    }

    fun updateSourceApp(sourceApp: SyncSourceApp) {
        val folder = sourceApp.defaultTargetFolder
        prefs.edit()
            .putString(KEY_SOURCE_APP, sourceApp.name)
            .putString(KEY_TARGET_FOLDER, folder.name)
            .putString(KEY_CUSTOM_FOLDER_PATH, folder.folderPath)
            .apply()
        _settings.value = _settings.value.copy(
            sourceApp = sourceApp,
            targetFolder = folder,
            customFolderPath = folder.folderPath
        )
    }

    fun updateHevyApiKey(key: String) {
        prefs.edit().putString(KEY_HEVY_API_KEY, key.trim()).apply()
        _settings.value = _settings.value.copy(hevyApiKey = key.trim())
    }

    fun updateWebhookUrl(url: String) {
        prefs.edit().putString(KEY_WEBHOOK_URL, url.trim()).apply()
        _settings.value = _settings.value.copy(webhookUrl = url.trim())
    }

    fun updateExportFormat(format: ExportFormat) {
        prefs.edit().putString(KEY_FORMAT, format.name).apply()
        _settings.value = _settings.value.copy(exportFormat = format)
    }

    fun updateWriteMode(mode: WriteMode) {
        prefs.edit().putString(KEY_WRITE_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(writeMode = mode)
    }

    fun updateTargetFolder(folder: TargetFolder) {
        prefs.edit().putString(KEY_TARGET_FOLDER, folder.name)
            .putString(KEY_CUSTOM_FOLDER_PATH, folder.folderPath)
            .apply()
        _settings.value = _settings.value.copy(targetFolder = folder, customFolderPath = folder.folderPath)
    }

    fun updateCustomFolderPath(path: String) {
        prefs.edit().putString(KEY_CUSTOM_FOLDER_PATH, path.trim()).apply()
        _settings.value = _settings.value.copy(customFolderPath = path.trim())
    }

    fun updateSyncInterval(minutes: Int) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL, minutes).apply()
        _settings.value = _settings.value.copy(syncIntervalMinutes = minutes)
    }

    fun updateAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _settings.value = _settings.value.copy(autoSyncEnabled = enabled)
    }

    fun updateTimezone(tz: String) {
        prefs.edit().putString(KEY_TIMEZONE, tz).apply()
        _settings.value = _settings.value.copy(timezoneId = tz)
    }

    fun updateDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _settings.value = _settings.value.copy(demoModeEnabled = enabled)
    }

    fun recordSyncOutcome(timestamp: Long, status: String, isSuccess: Boolean) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .putString(KEY_LAST_SYNC_STATUS, status)
            .putBoolean(KEY_LAST_SYNC_SUCCESS, isSuccess)
            .apply()
        _settings.value = _settings.value.copy(
            lastSyncTimestamp = timestamp,
            lastSyncStatus = status,
            lastSyncSuccess = isSuccess
        )
    }

    companion object {
        private const val KEY_WEBHOOK_URL = "key_webhook_url"
        private const val KEY_SOURCE_APP = "key_source_app"
        private const val KEY_HEVY_API_KEY = "key_hevy_api_key"
        private const val KEY_FORMAT = "key_format"
        private const val KEY_WRITE_MODE = "key_write_mode"
        private const val KEY_TARGET_FOLDER = "key_target_folder"
        private const val KEY_CUSTOM_FOLDER_PATH = "key_custom_folder_path"
        private const val KEY_SYNC_INTERVAL = "key_sync_interval"
        private const val KEY_AUTO_SYNC = "key_auto_sync"
        private const val KEY_TIMEZONE = "key_timezone"
        private const val KEY_DEMO_MODE = "key_demo_mode"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
        private const val KEY_LAST_SYNC_STATUS = "key_last_sync_status"
        private const val KEY_LAST_SYNC_SUCCESS = "key_last_sync_success"
    }
}
