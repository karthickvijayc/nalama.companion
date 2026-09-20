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

enum class AppScreen {
    SPLASH,
    LANDING,
    MAIN
}

data class BulkExportState(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val percentage: Float = 0f,
    val statusMessage: String = "",
    val phase: String = "",
    val recordsExported: Int = 0,
    val error: String? = null,
    val isCompleted: Boolean = false
)

data class HealthSyncUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
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
    val activeTab: Int = 0, // 0: Dashboard, 1: History, 2: Settings & Script, 3: Payload Preview
    val bulkExportState: BulkExportState = BulkExportState()
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

            val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
            val hasPerms = healthManager.hasAllPermissions()

            val record: DailyRecord? = when {
                settings.demoModeEnabled -> healthManager.generateSampleRecord(today, "Demo Mode")
                isAvailable && hasPerms -> healthManager.readDailyRecord(today, zoneId)
                else -> null
            }

            val jsonPreview = record?.let {
                val payload = BiometricsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "HealthConnect",
                    timezone = settings.timezoneId,
                    exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                    dailyRecords = listOf(it)
                )
                JsonConverter.toJsonString(payload, 2)
            } ?: "{\n  \"status\": \"Permission not granted or setup not complete\"\n}"

            val csvPreview = record?.let {
                CsvConverter.toCsvString(listOf(it), settings.writeMode == WriteMode.OVERWRITE)
            } ?: "Permission not granted or setup not complete"

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
            if (settings.hevyApiKey.isBlank() && !settings.demoModeEnabled) {
                _uiState.update {
                    it.copy(
                        workoutsPayload = null,
                        previewJson = "{\n  \"status\": \"Setup not complete: Hevy API key required\"\n}",
                        previewCsv = "Setup not complete: Hevy API key required"
                    )
                }
                return@launch
            }

            val result = hevyManager.fetchWorkouts(
                apiKey = settings.hevyApiKey,
                isDemoMode = settings.demoModeEnabled
            )

            val workoutsPayload = result.getOrNull() ?: WorkoutsExportPayload(
                exportVersion = "1.0",
                sourceApp = "Hevy",
                syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                workouts = emptyList()
            )

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
                if (settings.hevyApiKey.isBlank() && !settings.demoModeEnabled) {
                    val errorResult = WebhookResult(
                        isSuccess = false,
                        httpCode = 400,
                        message = "Setup not complete: Please configure your Hevy API key in Settings (Hevy Pro required, hevy.com/settings?developer).",
                        durationMs = 0
                    )
                    _uiState.update { it.copy(isExporting = false, lastExportResult = errorResult) }
                    return@launch
                }

                val fetchResult = hevyManager.fetchWorkouts(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled
                )
                val workoutsPayload = fetchResult.getOrElse {
                    WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        workouts = emptyList()
                    )
                }

                val result = webhookClient.postWorkoutExport(
                    webhookUrl = settings.webhookUrl,
                    payload = workoutsPayload,
                    folderPath = folderPath,
                    targetSubfolder = subfolder,
                    fileName = defaultFileName,
                    format = settings.exportFormat,
                    writeMode = settings.writeMode,
                    archiveMaxDays = settings.archiveMaxDays
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
                val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
                val hasPerms = healthManager.hasAllPermissions()

                if (!settings.demoModeEnabled && (!isAvailable || !hasPerms)) {
                    val msg = if (!isAvailable) {
                        "Setup not complete: Health Connect is not available on this device."
                    } else {
                        "Permission not granted: Health Connect permissions have not been granted."
                    }
                    val errorResult = WebhookResult(
                        isSuccess = false,
                        httpCode = 400,
                        message = msg,
                        durationMs = 0
                    )
                    _uiState.update { it.copy(isExporting = false, lastExportResult = errorResult) }
                    return@launch
                }

                val today = now.toLocalDate()
                val record: DailyRecord = if (settings.demoModeEnabled) {
                    healthManager.generateSampleRecord(today, "Demo Mode")
                } else {
                    healthManager.readDailyRecord(today, zoneId)
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
                    writeMode = settings.writeMode,
                    archiveMaxDays = settings.archiveMaxDays
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

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onSplashFinished() {
        val settings = prefs.settings.value
        if (settings.hasCompletedOnboarding || settings.isGoogleConnected || settings.demoModeEnabled) {
            _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }
        } else {
            _uiState.update { it.copy(currentScreen = AppScreen.LANDING) }
        }
    }

    fun connectGoogleAccount(email: String, displayName: String = "") {
        prefs.connectGoogleAccount(email, displayName)
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }
    }

    fun disconnectGoogleAccount() {
        prefs.disconnectGoogleAccount()
        _uiState.update { it.copy(currentScreen = AppScreen.LANDING) }
    }

    fun updateSelectedLanguage(language: String) {
        prefs.updateSelectedLanguage(language)
    }

    fun enterDemoModeFromLanding() {
        prefs.completeOnboardingAsDemo()
        refreshActiveData()
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }
    }

    /**
     * Executes bulk export of all historical data.
     * Manages API throttling limits, pagination for Hevy, date range chunking for Health Connect,
     * and batches requests to Google Apps Script Webhook.
     * Also marks initial bulk export as completed upon successful run.
     */
    fun startBulkExport(historyDays: Int = 365) {
        val settings = _uiState.value.settings
        if (settings.webhookUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    lastExportResult = WebhookResult(
                        isSuccess = false,
                        httpCode = null,
                        message = "Please configure your Google Apps Script Webhook URL before running a bulk export.",
                        durationMs = 0
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bulkExportState = BulkExportState(
                        isRunning = true,
                        current = 0,
                        total = historyDays,
                        percentage = 0.05f,
                        statusMessage = "Preparing historical export...",
                        phase = "INITIALIZING"
                    )
                )
            }

            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (e: Exception) { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zoneId)
            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val subfolder = settings.targetFolder.subfolder
            val defaultFileName = settings.targetFolder.defaultFileName

            if (settings.sourceApp == SyncSourceApp.HEVY) {
                // Hevy Bulk History Export
                if (settings.hevyApiKey.isBlank() && !settings.demoModeEnabled) {
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                isRunning = false,
                                error = "Setup not complete: Please configure your Hevy API key in Settings (Hevy Pro required, hevy.com/settings?developer)."
                            )
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            statusMessage = "Fetching workouts from Hevy API with pagination...",
                            phase = "FETCHING"
                        )
                    )
                }

                val hevyResult = hevyManager.fetchAllWorkoutsPaginated(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled,
                    pageSize = 10
                ) { page, totalPages, count ->
                    val progress = (page.toFloat() / totalPages.coerceAtLeast(1).toFloat()) * 0.5f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                current = page,
                                total = totalPages,
                                percentage = progress,
                                statusMessage = "Fetched page $page of $totalPages ($count workouts)..."
                            )
                        )
                    }
                }

                if (hevyResult.isFailure) {
                    val errMsg = hevyResult.exceptionOrNull()?.localizedMessage ?: "Failed to fetch Hevy workouts"
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                isRunning = false,
                                error = errMsg
                            )
                        )
                    }
                    return@launch
                }

                val allWorkouts = hevyResult.getOrNull() ?: emptyList()
                if (allWorkouts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                isRunning = false,
                                statusMessage = "No workouts found to export.",
                                isCompleted = true
                            )
                        )
                    }
                    return@launch
                }

                // Batch workouts in chunks of 50 to avoid Apps Script HTTP payload/timeout limits
                val chunks = allWorkouts.chunked(50)
                var uploadedCount = 0
                var lastResult: WebhookResult? = null

                for ((idx, chunk) in chunks.withIndex()) {
                    val chunkProgress = 0.5f + ((idx + 1).toFloat() / chunks.size.toFloat()) * 0.5f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                percentage = chunkProgress,
                                statusMessage = "Uploading chunk ${idx + 1} of ${chunks.size} (${chunk.size} workouts)...",
                                phase = "UPLOADING"
                            )
                        )
                    }

                    val payload = WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                        workouts = chunk
                    )

                    lastResult = webhookClient.postWorkoutExport(
                        webhookUrl = settings.webhookUrl,
                        payload = payload,
                        folderPath = folderPath,
                        targetSubfolder = subfolder,
                        fileName = defaultFileName,
                        format = settings.exportFormat,
                        writeMode = WriteMode.APPEND,
                        archiveMaxDays = settings.archiveMaxDays,
                        isBulkExport = true
                    )

                    uploadedCount += chunk.size
                    kotlinx.coroutines.delay(400L)
                }

                val finalSuccess = lastResult?.isSuccess == true
                if (finalSuccess) {
                    prefs.recordInitialBulkExportCompleted()
                    val historyItem = ExportHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        status = ExportStatus.SUCCESS,
                        format = settings.exportFormat,
                        writeMode = WriteMode.APPEND,
                        folderPath = folderPath,
                        recordsCount = uploadedCount,
                        httpStatusCode = lastResult?.httpCode,
                        message = "Bulk export completed: $uploadedCount workouts exported (180-day active window + yearly archives).",
                        payloadPreviewJson = "",
                        payloadPreviewCsv = "",
                        isManualTrigger = true,
                        sourceApp = "Hevy"
                    )
                    historyStore.addHistoryItem(historyItem)
                    prefs.recordSyncOutcome(
                        timestamp = System.currentTimeMillis(),
                        status = "Bulk exported $uploadedCount workouts to $folderPath",
                        isSuccess = true
                    )
                }

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            isRunning = false,
                            percentage = 1f,
                            isCompleted = true,
                            recordsExported = uploadedCount,
                            statusMessage = if (finalSuccess) "Bulk export complete! $uploadedCount workouts processed." else "Bulk export finished: ${lastResult?.message}"
                        ),
                        lastExportResult = lastResult
                    )
                }

            } else {
                // Health Connect Bulk History Export
                val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
                val hasPerms = healthManager.hasAllPermissions()

                if (!settings.demoModeEnabled && (!isAvailable || !hasPerms)) {
                    val msg = if (!isAvailable) "Setup not complete: Health Connect is not available on this device."
                              else "Permission not granted: Please grant Health Connect permissions before bulk export."
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                isRunning = false,
                                error = msg
                            )
                        )
                    }
                    return@launch
                }

                val today = now.toLocalDate()
                val startDate = today.minusDays(historyDays.toLong())

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            statusMessage = "Reading $historyDays days of health records...",
                            phase = "READING"
                        )
                    )
                }

                val records = healthManager.readHistoricalRecords(
                    startDate = startDate,
                    endDate = today,
                    zoneId = zoneId,
                    isDemoMode = settings.demoModeEnabled
                ) { current, total, date ->
                    val readProgress = (current.toFloat() / total.toFloat()) * 0.6f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                current = current,
                                total = total,
                                percentage = readProgress,
                                statusMessage = "Reading Health records: $date ($current/$total days)..."
                            )
                        )
                    }
                }

                // Batch records into chunks of 60 days to prevent Apps Script timeouts
                val chunks = records.chunked(60)
                var uploadedCount = 0
                var lastResult: WebhookResult? = null

                for ((idx, chunk) in chunks.withIndex()) {
                    val uploadProgress = 0.6f + ((idx + 1).toFloat() / chunks.size.toFloat()) * 0.4f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                percentage = uploadProgress,
                                statusMessage = "Uploading chunk ${idx + 1} of ${chunks.size} (${chunk.size} records)...",
                                phase = "UPLOADING"
                            )
                        )
                    }

                    val payload = BiometricsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "HealthConnect",
                        timezone = settings.timezoneId,
                        exportedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                        dailyRecords = chunk
                    )

                    lastResult = webhookClient.postExport(
                        webhookUrl = settings.webhookUrl,
                        payload = payload,
                        folderPath = folderPath,
                        targetSubfolder = subfolder,
                        fileName = defaultFileName,
                        format = settings.exportFormat,
                        writeMode = WriteMode.APPEND,
                        archiveMaxDays = settings.archiveMaxDays,
                        isBulkExport = true
                    )

                    uploadedCount += chunk.size
                    kotlinx.coroutines.delay(400L)
                }

                val finalSuccess = lastResult?.isSuccess == true
                if (finalSuccess) {
                    prefs.recordInitialBulkExportCompleted()
                    val historyItem = ExportHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        status = ExportStatus.SUCCESS,
                        format = settings.exportFormat,
                        writeMode = WriteMode.APPEND,
                        folderPath = folderPath,
                        recordsCount = uploadedCount,
                        httpStatusCode = lastResult?.httpCode,
                        message = "Bulk export completed: $uploadedCount daily records exported (180-day active window + yearly archives).",
                        payloadPreviewJson = "",
                        payloadPreviewCsv = "",
                        isManualTrigger = true,
                        sourceApp = "HealthConnect"
                    )
                    historyStore.addHistoryItem(historyItem)
                    prefs.recordSyncOutcome(
                        timestamp = System.currentTimeMillis(),
                        status = "Bulk exported $uploadedCount records to $folderPath",
                        isSuccess = true
                    )
                }

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            isRunning = false,
                            percentage = 1f,
                            isCompleted = true,
                            recordsExported = uploadedCount,
                            statusMessage = if (finalSuccess) "Bulk export complete! $uploadedCount records processed across active and archive files." else "Export note: ${lastResult?.message}"
                        ),
                        lastExportResult = lastResult
                    )
                }
            }
        }
    }

    fun dismissBulkExportDialog() {
        _uiState.update {
            it.copy(bulkExportState = BulkExportState())
        }
    }

    fun dismissInitialBulkExportPrompt() {
        prefs.recordInitialBulkExportCompleted()
    }
}
