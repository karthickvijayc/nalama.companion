package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HealthSyncApplication
import com.example.data.AppSettings
import com.example.drive.CacheSummary
import com.example.drive.DriveDiagnosticTestResult
import com.example.drive.GoogleDriveDirectClient
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.health.HevySyncManager
import com.example.model.*
import com.example.sync.SyncScheduler
import com.example.util.AppLogger
import com.example.util.CsvConverter
import com.example.util.LogEntry
import com.example.util.WorkoutCsvConverter
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

data class SyncResult(
    val isSuccess: Boolean,
    val message: String,
    val durationMs: Long = 0,
    val isLocalOnlyFallback: Boolean = false,
    val targetFolderUrl: String? = null
)

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
    val lastExportResult: SyncResult? = null,
    val isTestingDrive: Boolean = false,
    val testDriveResult: SyncResult? = null,
    val previewCsv: String = "",
    val activeTab: Int = 0, // 0: Dashboard, 1: History, 2: Settings, 3: CSV Preview
    val bulkExportState: BulkExportState = BulkExportState(),
    val isDiagnosingDrive: Boolean = false,
    val diagnosticTestResult: DriveDiagnosticTestResult? = null,
    val showDiagnosticsDialog: Boolean = false,
    val showDriveAuthDialog: Boolean = false,
    val cacheSummary: CacheSummary = CacheSummary(0, 0, 0L, emptyList())
)

class HealthSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HealthSyncApplication
    private val prefs = app.preferencesManager
    private val historyStore = app.historyStore
    private val healthManager = HealthConnectManager(application)
    private val hevyManager = HevySyncManager()
    private val driveClient = GoogleDriveDirectClient(application)

    private val _uiState = MutableStateFlow(HealthSyncUiState())
    val uiState: StateFlow<HealthSyncUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<ExportHistoryItem>> = historyStore.history
    val diagnosticLogs: StateFlow<List<LogEntry>> = AppLogger.logs

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

            val csvPreview = record?.let {
                CsvConverter.toCsvString(listOf(it), settings.writeMode == WriteMode.OVERWRITE)
            } ?: "Permission not granted or setup not complete"

            _uiState.update {
                it.copy(
                    todayRecord = record,
                    previewCsv = csvPreview
                )
            }
        }
    }

    fun refreshHevyWorkouts() {
        viewModelScope.launch {
            val settings = prefs.settings.value
            val fetchResult = hevyManager.fetchWorkouts(
                apiKey = settings.hevyApiKey,
                isDemoMode = settings.demoModeEnabled
            )

            val payload = fetchResult.getOrElse {
                if (settings.demoModeEnabled || settings.hevyApiKey.isBlank()) {
                    WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        workouts = hevyManager.getSampleWorkouts()
                    )
                } else {
                    WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        workouts = emptyList()
                    )
                }
            }
            val workouts = payload.workouts

            val csvPreview = if (workouts.isNotEmpty()) {
                WorkoutCsvConverter.toCsvString(workouts, settings.writeMode == WriteMode.OVERWRITE)
            } else {
                "No workouts loaded. Configure Hevy API key in Settings."
            }

            _uiState.update {
                it.copy(
                    workoutsPayload = payload,
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

            AppLogger.i("UI", "Manual export triggered for ${settings.sourceApp.displayName}")

            if (settings.sourceApp == SyncSourceApp.HEVY) {
                val fetchResult = hevyManager.fetchWorkouts(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled
                )

                if (fetchResult.isFailure && !settings.demoModeEnabled) {
                    val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Failed to fetch workouts from Hevy API."
                    val errorResult = SyncResult(
                        isSuccess = false,
                        message = errorMsg
                    )
                    _uiState.update { it.copy(isExporting = false, lastExportResult = errorResult) }
                    return@launch
                }

                val payload = fetchResult.getOrElse {
                    WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                        workouts = hevyManager.getSampleWorkouts()
                    )
                }
                val workoutsPayload = payload.copy(syncedAt = now.format(DateTimeFormatter.ISO_INSTANT))

                val direct = driveClient.syncWorkoutsDirectly(
                    accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                    payload = workoutsPayload,
                    folderPath = folderPath,
                    targetSubfolder = subfolder,
                    fileName = defaultFileName,
                    writeMode = settings.writeMode,
                    archiveMaxDays = settings.archiveMaxDays,
                    isDemoMode = settings.demoModeEnabled
                )

                val result = SyncResult(
                    isSuccess = direct.isSuccess,
                    message = direct.message,
                    durationMs = direct.durationMs,
                    isLocalOnlyFallback = direct.isLocalOnlyFallback,
                    targetFolderUrl = direct.targetFolderUrl
                )

                val csvPreview = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)

                val status = when {
                    direct.isSuccess -> ExportStatus.SUCCESS
                    direct.isLocalOnlyFallback -> ExportStatus.LOCAL_ONLY
                    else -> ExportStatus.FAILED
                }

                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = formattedNow,
                    status = status,
                    writeMode = settings.writeMode,
                    folderPath = folderPath,
                    recordsCount = workoutsPayload.workouts.size,
                    message = result.message,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = true,
                    sourceApp = "Hevy",
                    targetFolderUrl = direct.targetFolderUrl
                )

                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = if (direct.isSuccess) "Exported ${workoutsPayload.workouts.size} Hevy workout(s) to Google Drive ($folderPath)" else result.message,
                    isSuccess = direct.isSuccess
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        workoutsPayload = workoutsPayload,
                        previewCsv = csvPreview
                    )
                }
            } else {
                val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
                val hasPerms = healthManager.hasAllPermissions()

                if (!settings.demoModeEnabled && (!isAvailable || !hasPerms)) {
                    val msg = if (!isAvailable) {
                        "Setup not complete: Health Connect is not available on this device."
                    } else {
                        "Permission not granted: Health Connect permissions have not been granted."
                    }
                    val errorResult = SyncResult(
                        isSuccess = false,
                        message = msg
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

                val direct = driveClient.syncBiometricsDirectly(
                    accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                    payload = payload,
                    folderPath = folderPath,
                    targetSubfolder = subfolder,
                    fileName = defaultFileName,
                    writeMode = settings.writeMode,
                    archiveMaxDays = settings.archiveMaxDays,
                    isDemoMode = settings.demoModeEnabled
                )

                val result = SyncResult(
                    isSuccess = direct.isSuccess,
                    message = direct.message,
                    durationMs = direct.durationMs,
                    isLocalOnlyFallback = direct.isLocalOnlyFallback,
                    targetFolderUrl = direct.targetFolderUrl
                )

                val csvPreview = CsvConverter.toCsvString(listOf(record), settings.writeMode == WriteMode.OVERWRITE)

                val status = when {
                    direct.isSuccess -> ExportStatus.SUCCESS
                    direct.isLocalOnlyFallback -> ExportStatus.LOCAL_ONLY
                    else -> ExportStatus.FAILED
                }

                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = formattedNow,
                    status = status,
                    writeMode = settings.writeMode,
                    folderPath = folderPath,
                    recordsCount = 1,
                    message = result.message,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = true,
                    sourceApp = "HealthConnect",
                    targetFolderUrl = direct.targetFolderUrl
                )

                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = if (direct.isSuccess) "Exported 1 health record to Google Drive ($folderPath)" else result.message,
                    isSuccess = direct.isSuccess
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = result,
                        todayRecord = record,
                        previewCsv = csvPreview
                    )
                }
            }
        }
    }

    fun testDriveConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingDrive = true, testDriveResult = null) }
            val settings = prefs.settings.value
            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }

            if (settings.googleOAuthAccessToken.isBlank() && !settings.demoModeEnabled) {
                val errorResult = SyncResult(
                    isSuccess = false,
                    message = "Google Drive authorization required. Please configure an access token in Settings > Google Account & Drive."
                )
                _uiState.update { it.copy(isTestingDrive = false, testDriveResult = errorResult) }
                return@launch
            }

            val direct = driveClient.testDirectDriveConnection(
                accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                folderPath = folderPath
            )
            val result = SyncResult(
                isSuccess = direct.isSuccess,
                message = direct.message,
                durationMs = direct.durationMs,
                targetFolderUrl = direct.targetFolderUrl
            )
            _uiState.update { it.copy(isTestingDrive = false, testDriveResult = result) }
        }
    }

    fun openDiagnosticsDialog() {
        _uiState.update {
            it.copy(
                showDiagnosticsDialog = true,
                cacheSummary = driveClient.cacheManager.getCacheSummary()
            )
        }
    }

    fun dismissDiagnosticsDialog() {
        _uiState.update { it.copy(showDiagnosticsDialog = false) }
    }

    fun openDriveAuthDialog() {
        _uiState.update { it.copy(showDriveAuthDialog = true) }
    }

    fun dismissDriveAuthDialog() {
        _uiState.update { it.copy(showDriveAuthDialog = false) }
    }

    fun runDriveDiagnostic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiagnosingDrive = true, diagnosticTestResult = null) }
            val settings = prefs.settings.value
            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val result = driveClient.runFullDriveDiagnostic(
                accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                folderPath = folderPath
            )
            _uiState.update {
                it.copy(
                    isDiagnosingDrive = false,
                    diagnosticTestResult = result,
                    cacheSummary = driveClient.cacheManager.getCacheSummary()
                )
            }
        }
    }

    fun clearSyncCache() {
        val clearedCount = driveClient.cacheManager.clearAllCache()
        AppLogger.i("CACHE", "Local sync cache manually reset. Cleared $clearedCount cached file entries.")
        _uiState.update { it.copy(cacheSummary = driveClient.cacheManager.getCacheSummary()) }
    }

    fun clearDiagnosticLogs() {
        AppLogger.clearLogs()
    }

    fun getDiagnosticReport(): String {
        val settings = prefs.settings.value
        return AppLogger.generateDiagnosticReport(
            connectedEmail = settings.connectedEmail,
            targetFolder = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath },
            hasToken = settings.googleOAuthAccessToken.isNotBlank(),
            isDemoMode = settings.demoModeEnabled,
            cacheSummary = driveClient.cacheManager.getCacheSummary()
        )
    }

    fun verifyAndSaveGoogleOAuthToken(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                onResult(false, "Token cannot be empty")
                return@launch
            }
            val result = driveClient.testDirectDriveConnection(trimmed, "nalama.family")
            if (result.isSuccess) {
                prefs.updateGoogleOAuthAccessToken(
                    token = trimmed,
                    email = result.userEmail ?: prefs.settings.value.connectedEmail.ifBlank { "Google Drive User" },
                    displayName = result.displayName ?: prefs.settings.value.connectedDisplayName.ifBlank { "Google User" }
                )
                AppLogger.s("AUTH", "Google OAuth token verified & saved for user: ${result.userEmail}")
                onResult(true, "Successfully connected to Google Drive account (${result.userEmail ?: "authorized"})!")
            } else {
                AppLogger.e("AUTH", "Failed to verify token: ${result.message}")
                onResult(false, "Verification failed: ${result.message}. Check that token has drive.file scope.")
            }
        }
    }

    fun clearGoogleOAuthToken() {
        prefs.updateGoogleOAuthAccessToken("")
        AppLogger.i("AUTH", "Google OAuth access token removed.")
    }

    fun updateGoogleOAuthAccessToken(token: String) {
        prefs.updateGoogleOAuthAccessToken(token)
    }

    fun dismissTestResult() {
        _uiState.update { it.copy(testDriveResult = null) }
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

    fun enterDemoModeFromLanding() {
        prefs.completeOnboardingAsDemo()
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }
    }

    fun disconnectGoogleAccount() {
        prefs.disconnectGoogleAccount()
        _uiState.update { it.copy(currentScreen = AppScreen.LANDING) }
    }

    fun updateSelectedLanguage(language: String) {
        prefs.updateSelectedLanguage(language)
    }

    fun startBulkExport(historyDays: Int = 180) {
        viewModelScope.launch {
            val settings = prefs.settings.value
            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zoneId)

            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val subfolder = settings.targetFolder.subfolder
            val defaultFileName = settings.targetFolder.defaultFileName

            AppLogger.i("BULK", "Bulk export initiated for ${settings.sourceApp.displayName}, historyDays: $historyDays")

            _uiState.update {
                it.copy(
                    bulkExportState = BulkExportState(
                        isRunning = true,
                        phase = "INITIALIZING",
                        statusMessage = "Preparing historical sync..."
                    )
                )
            }

            if (settings.sourceApp == SyncSourceApp.HEVY) {
                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            statusMessage = "Fetching all workout records from Hevy API...",
                            phase = "READING"
                        )
                    )
                }

                val hevyResult = hevyManager.fetchAllWorkoutsPaginated(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled,
                    pageSize = 10
                ) { page, totalPages, workoutsCount ->
                    val readProgress = if (totalPages > 0) (page.toFloat() / totalPages.toFloat()) * 0.5f else 0.25f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                current = page,
                                total = totalPages,
                                percentage = readProgress,
                                statusMessage = "Reading Hevy workouts (Page $page of $totalPages)..."
                            )
                        )
                    }
                }

                if (hevyResult.isFailure && !settings.demoModeEnabled) {
                    val errMsg = hevyResult.exceptionOrNull()?.message ?: "Failed to fetch workouts from Hevy API."
                    AppLogger.e("BULK", "Hevy bulk fetch failed: $errMsg")
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

                val chunks = allWorkouts.chunked(50)
                var uploadedCount = 0
                var lastDirectResult: com.example.drive.DirectDriveResult? = null

                for ((idx, chunk) in chunks.withIndex()) {
                    val chunkProgress = 0.5f + ((idx + 1).toFloat() / chunks.size.toFloat()) * 0.5f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                percentage = chunkProgress,
                                statusMessage = "Exporting chunk ${idx + 1} of ${chunks.size} (${chunk.size} workouts)...",
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

                    val direct = driveClient.syncWorkoutsDirectly(
                        accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                        payload = payload,
                        folderPath = folderPath,
                        targetSubfolder = subfolder,
                        fileName = defaultFileName,
                        writeMode = WriteMode.APPEND,
                        archiveMaxDays = settings.archiveMaxDays,
                        isDemoMode = settings.demoModeEnabled
                    )
                    lastDirectResult = direct

                    uploadedCount += chunk.size
                    kotlinx.coroutines.delay(200L)
                }

                val finalSuccess = lastDirectResult?.isSuccess == true
                val isLocalFallback = lastDirectResult?.isLocalOnlyFallback == true

                val historyStatus = when {
                    finalSuccess -> ExportStatus.SUCCESS
                    isLocalFallback -> ExportStatus.LOCAL_ONLY
                    else -> ExportStatus.FAILED
                }

                val historyMessage = when {
                    finalSuccess -> "Bulk export completed: $uploadedCount workouts exported to Google Drive."
                    isLocalFallback -> "Bulk export completed locally ($uploadedCount workouts). Google Drive upload skipped (unauthorized)."
                    else -> "Bulk export failed: ${lastDirectResult?.message}"
                }

                prefs.recordInitialBulkExportCompleted()
                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    status = historyStatus,
                    writeMode = WriteMode.APPEND,
                    folderPath = folderPath,
                    recordsCount = uploadedCount,
                    message = historyMessage,
                    payloadPreviewCsv = "",
                    isManualTrigger = true,
                    sourceApp = "Hevy",
                    targetFolderUrl = lastDirectResult?.targetFolderUrl
                )
                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = historyMessage,
                    isSuccess = finalSuccess
                )

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            isRunning = false,
                            percentage = 1f,
                            isCompleted = true,
                            recordsExported = uploadedCount,
                            statusMessage = historyMessage
                        ),
                        lastExportResult = SyncResult(
                            isSuccess = finalSuccess,
                            message = historyMessage,
                            isLocalOnlyFallback = isLocalFallback,
                            targetFolderUrl = lastDirectResult?.targetFolderUrl
                        )
                    )
                }

            } else {
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

                val chunks = records.chunked(60)
                var uploadedCount = 0
                var lastDirectResult: com.example.drive.DirectDriveResult? = null

                for ((idx, chunk) in chunks.withIndex()) {
                    val uploadProgress = 0.6f + ((idx + 1).toFloat() / chunks.size.toFloat()) * 0.4f
                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                percentage = uploadProgress,
                                statusMessage = "Exporting chunk ${idx + 1} of ${chunks.size} (${chunk.size} records)...",
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

                    val direct = driveClient.syncBiometricsDirectly(
                        accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                        payload = payload,
                        folderPath = folderPath,
                        targetSubfolder = subfolder,
                        fileName = defaultFileName,
                        writeMode = WriteMode.APPEND,
                        archiveMaxDays = settings.archiveMaxDays,
                        isDemoMode = settings.demoModeEnabled
                    )
                    lastDirectResult = direct

                    uploadedCount += chunk.size
                    kotlinx.coroutines.delay(200L)
                }

                val finalSuccess = lastDirectResult?.isSuccess == true
                val isLocalFallback = lastDirectResult?.isLocalOnlyFallback == true

                val historyStatus = when {
                    finalSuccess -> ExportStatus.SUCCESS
                    isLocalFallback -> ExportStatus.LOCAL_ONLY
                    else -> ExportStatus.FAILED
                }

                val historyMessage = when {
                    finalSuccess -> "Bulk export completed: $uploadedCount records exported to Google Drive."
                    isLocalFallback -> "Bulk export completed locally ($uploadedCount records). Google Drive upload skipped (unauthorized)."
                    else -> "Bulk export failed: ${lastDirectResult?.message}"
                }

                prefs.recordInitialBulkExportCompleted()
                val historyItem = ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    status = historyStatus,
                    writeMode = WriteMode.APPEND,
                    folderPath = folderPath,
                    recordsCount = uploadedCount,
                    message = historyMessage,
                    payloadPreviewCsv = "",
                    isManualTrigger = true,
                    sourceApp = "HealthConnect",
                    targetFolderUrl = lastDirectResult?.targetFolderUrl
                )
                historyStore.addHistoryItem(historyItem)
                prefs.recordSyncOutcome(
                    timestamp = System.currentTimeMillis(),
                    status = historyMessage,
                    isSuccess = finalSuccess
                )

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            isRunning = false,
                            percentage = 1f,
                            isCompleted = true,
                            recordsExported = uploadedCount,
                            statusMessage = historyMessage
                        ),
                        lastExportResult = SyncResult(
                            isSuccess = finalSuccess,
                            message = historyMessage,
                            isLocalOnlyFallback = isLocalFallback,
                            targetFolderUrl = lastDirectResult?.targetFolderUrl
                        )
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
