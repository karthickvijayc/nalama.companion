package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HealthSyncApplication
import com.example.data.AppSettings
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.health.HevySyncManager
import com.example.model.*
import com.example.network.GoogleAppsScriptWebhookClient
import com.example.network.WebhookResult
import com.example.sync.SyncScheduler
import com.example.util.CsvConverter
import com.example.util.JsonConverter
import com.example.util.WorkoutCsvConverter
import com.example.util.WorkoutJsonConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class HealthSyncUiState(
    val settings: AppSettings = AppSettings(),
    val healthAvailability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    val hasPermissions: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val todayRecord: DailyRecord? = null,
    val workoutsPayload: WorkoutsExportPayload? = null,
    val isExporting: Boolean = false,
    val lastExportResult: WebhookResult? = null,
    val isTestingWebhook: Boolean = false,
    val testWebhookResult: WebhookResult? = null,
    val previewJson: String = "",
    val previewCsv: String = "",
    val activeTab: Int = 0 // 0: Dashboard, 1: History, 2: Settings & Script, 3: Payload Preview
)

class HealthSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HealthSyncApplication
    private val prefs = app.preferencesManager
    private val historyStore = app.historyStore
    private val healthManager = HealthConnectManager(application)
    private val hevyManager = HevySyncManager()
    private val webhookClient = GoogleAppsScriptWebhookClient()

    private val _uiState = MutableStateFlow(HealthSyncUiState())
    val uiState: StateFlow<HealthSyncUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<ExportHistoryItem>> = historyStore.history

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshActiveData()
            }
        }
        checkHealthConnectStatus()
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun getHealthConnectSettingsIntent() = healthManager.getHealthConnectSettingsIntent()

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val availability = healthManager.checkAvailability()
            val hasPerms = healthManager.hasAllPermissions()
            val granted = healthManager.getGrantedPermissions()
            _uiState.update {
                it.copy(
                    healthAvailability = availability,
                    hasPermissions = hasPerms,
                    grantedPermissions = granted
                )
            }
            refreshActiveData()
        }
    }

    fun refreshActiveData() {
        val settings = prefs.settings.value
        when (settings.sourceApp) {
            SyncSourceApp.HEALTH_CONNECT -> refreshHealthData()
            SyncSourceApp.HEVY -> refreshHevyWorkouts()
        }
    }

    fun refreshHealthData() {
        viewModelScope.launch {
            val settings = prefs.settings.value
            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val today = LocalDate.now(zoneId)

            val record: DailyRecord = if (settings.demoModeEnabled) {
                healthManager.generateSampleRecord(today, "Demo Mode")
            } else if (healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE && healthManager.hasAllPermissions()) {
                healthManager.readDailyRecord(today, zoneId)
            } else {
                healthManager.generateSampleRecord(today, "Sample Health Connect Data")
            }

            val payload = BiometricsExportPayload(
                exportVersion = "1.0",
                sourceApp = "HealthConnect",
                timezone = settings.timezoneId,
                exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                dailyRecords = listOf(record)
            )

            val jsonPreview = JsonConverter.toJsonString(payload, 2)
            val csvPreview = CsvConverter.toCsvString(listOf(record), settings.writeMode == WriteMode.OVERWRITE)

            _uiState.update {
                it.copy(
                    todayRecord = record,
                    previewJson = jsonPreview,
                    previewCsv = csvPreview
                )
            }
        }
    }

    fun refreshHevyWorkouts() {
        viewModelScope.launch {
            val settings = prefs.settings.value
            val result = hevyManager.fetchWorkouts(
                apiKey = settings.hevyApiKey,
                isDemoMode = settings.demoModeEnabled
            )

            val workoutsPayload = result.getOrElse {
                WorkoutsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "Hevy",
                    syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                    workouts = hevyManager.getSampleWorkouts()
                )
            }

            val jsonPreview = WorkoutJsonConverter.toJsonString(workoutsPayload, 2)
            val csvPreview = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)

            _uiState.update {
                it.copy(
                    workoutsPayload = workoutsPayload,
                    previewJson = jsonPreview,
                    previewCsv = csvPreview
                )
            }
        }
    }

    fun exportNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, lastExportResult = null) }
            val settings = prefs.settings.value
            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zoneId)
            val formattedNow = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"))

            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val subfolder = settings.targetFolder.subfolder
            val defaultFileName = settings.targetFolder.defaultFileName

            if (settings.sourceApp == SyncSourceApp.HEVY) {
                // Hevy Workouts Export
                val fetchResult = hevyManager.fetchWorkouts(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled
                )
                val workoutsPayload = fetchResult.getOrElse {
                    WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        workouts = hevyManager.getSampleWorkouts()
                    )
                }

                val result = webhookClient.postWorkoutExport(
                    webhookUrl = settings.webhookUrl,
                    payload = workoutsPayload,
                    folderPath = folderPath,
                    targetSubfolder = subfolder,
                    fileName = defaultFileName,
                    format = settings.exportFormat,
                    writeMode = settings.writeMode
                )

                val jsonPreview = WorkoutJsonConverter.toJsonString(workoutsPayload, 2)
                val csvPreview = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)

                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = formattedNow,
                    status = if (result.isSuccess) ExportStatus.SUCCESS else ExportStatus.FAILED,
                    format = settings.exportFormat,
                    writeMode = settings.writeMode,
                    folderPath = folderPath,
                    recordsCount = workoutsPayload.workouts.size,
                    httpStatusCode = result.httpCode,
                    message = result.message,
                    payloadPreviewJson = jsonPreview,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = true,
                    sourceApp = "Hevy"
                )

                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = if (result.isSuccess) "Exported ${workoutsPayload.workouts.size} Hevy workout(s) to $folderPath" else result.message,
                    isSuccess = result.isSuccess
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        workoutsPayload = workoutsPayload,
                        previewJson = jsonPreview,
                        previewCsv = csvPreview
                    )
                }
            } else {
                // Health Connect Biometrics Export
                val today = now.toLocalDate()
                val record: DailyRecord = if (settings.demoModeEnabled) {
                    healthManager.generateSampleRecord(today, "Demo Mode")
                } else if (healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE && healthManager.hasAllPermissions()) {
                    healthManager.readDailyRecord(today, zoneId)
                } else {
                    healthManager.generateSampleRecord(today, "HealthConnect")
                }

                val payload = BiometricsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "HealthConnect",
                    timezone = settings.timezoneId,
                    exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                    dailyRecords = listOf(record)
                )

                val result = webhookClient.postExport(
                    webhookUrl = settings.webhookUrl,
                    payload = payload,
                    folderPath = folderPath,
                    targetSubfolder = subfolder,
                    fileName = defaultFileName,
                    format = settings.exportFormat,
                    writeMode = settings.writeMode
                )

                val jsonPreview = JsonConverter.toJsonString(payload, 2)
                val csvPreview = CsvConverter.toCsvString(listOf(record), settings.writeMode == WriteMode.OVERWRITE)

                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = formattedNow,
                    status = if (result.isSuccess) ExportStatus.SUCCESS else ExportStatus.FAILED,
                    format = settings.exportFormat,
                    writeMode = settings.writeMode,
                    folderPath = folderPath,
                    recordsCount = 1,
                    httpStatusCode = result.httpCode,
                    message = result.message,
                    payloadPreviewJson = jsonPreview,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = true,
                    sourceApp = "HealthConnect"
                )

                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = if (result.isSuccess) "Exported 1 health record to $folderPath" else result.message,
                    isSuccess = result.isSuccess
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        todayRecord = record,
                        previewJson = jsonPreview,
                        previewCsv = csvPreview
                    )
                }
            }
        }
    }

    fun testWebhook(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingWebhook = true, testWebhookResult = null) }
            val result = webhookClient.testWebhookConnectivity(url)
            _uiState.update { it.copy(isTestingWebhook = false, testWebhookResult = result) }
        }
    }

    fun dismissTestResult() {
        _uiState.update { it.copy(testWebhookResult = null) }
    }

    fun dismissExportResult() {
        _uiState.update { it.copy(lastExportResult = null) }
    }

    fun updateSourceApp(sourceApp: SyncSourceApp) {
        prefs.updateSourceApp(sourceApp)
        refreshActiveData()
    }

    fun updateHevyApiKey(key: String) {
        prefs.updateHevyApiKey(key)
        if (prefs.settings.value.sourceApp == SyncSourceApp.HEVY) {
            refreshHevyWorkouts()
        }
    }

    fun updateWebhookUrl(url: String) {
        prefs.updateWebhookUrl(url)
    }

    fun updateExportFormat(format: ExportFormat) {
        prefs.updateExportFormat(format)
        refreshActiveData()
    }

    fun updateWriteMode(mode: WriteMode) {
        prefs.updateWriteMode(mode)
        refreshActiveData()
    }

    fun updateTargetFolder(folder: TargetFolder) {
        prefs.updateTargetFolder(folder)
    }

    fun updateCustomFolderPath(path: String) {
        prefs.updateCustomFolderPath(path)
    }

    fun updateSyncInterval(minutes: Int) {
        prefs.updateSyncInterval(minutes)
        SyncScheduler.schedulePeriodicSync(
            context = getApplication(),
            intervalMinutes = minutes,
            autoSyncEnabled = prefs.settings.value.autoSyncEnabled
        )
    }

    fun updateAutoSync(enabled: Boolean) {
        prefs.updateAutoSync(enabled)
        SyncScheduler.schedulePeriodicSync(
            context = getApplication(),
            intervalMinutes = prefs.settings.value.syncIntervalMinutes,
            autoSyncEnabled = enabled
        )
    }

    fun updateTimezone(tz: String) {
        prefs.updateTimezone(tz)
        refreshActiveData()
    }

    fun toggleDemoMode(enabled: Boolean) {
        prefs.updateDemoMode(enabled)
        refreshActiveData()
    }

    fun clearHistory() {
        historyStore.clearHistory()
    }
}
