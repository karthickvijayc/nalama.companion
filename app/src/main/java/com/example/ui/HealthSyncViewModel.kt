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
    val activeTab: Int = 0, // 0: Settings, 1: Dashboard, 2: History
    val bulkExportState: BulkExportState = BulkExportState(),
    val isDiagnosingDrive: Boolean = false,
    val diagnosticTestResult: DriveDiagnosticTestResult? = null,
    val showDiagnosticsDialog: Boolean = false,
    val cacheSummary: CacheSummary = CacheSummary(0, 0, 0L, emptyList()),
    val authError: String? = null
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

    private val _userConsentIntentEvent = MutableStateFlow<android.content.Intent?>(null)
    val userConsentIntentEvent: StateFlow<android.content.Intent?> = _userConsentIntentEvent.asStateFlow()

    fun consumeUserConsentIntent() {
        _userConsentIntentEvent.value = null
    }

    val history: StateFlow<List<ExportHistoryItem>> = historyStore.history
    val diagnosticLogs: StateFlow<List<LogEntry>> = AppLogger.logs

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    suspend fun resolveOrFetchDriveAccessToken(): String? {
        val settings = prefs.settings.value
        var currentToken = settings.googleOAuthAccessToken
        if (currentToken.isNotBlank()) return currentToken

        if (settings.connectedEmail.isNotBlank()) {
            try {
                val fetched = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(getApplication(), settings.connectedEmail)
                if (!fetched.isNullOrBlank()) {
                    prefs.updateGoogleOAuthAccessToken(fetched)
                    AppLogger.s("AUTH", "Google Drive authorized automatically for ${settings.connectedEmail}")
                    _uiState.update { it.copy(authError = null) }
                    return fetched
                }
            } catch (unregistered: com.example.auth.UnregisteredOnApiConsoleException) {
                AppLogger.e("AUTH", "UnregisteredOnApiConsole: ${unregistered.message}")
                _uiState.update { it.copy(authError = unregistered.message) }
            } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                AppLogger.w("AUTH", "User consent required for Google Drive on ${settings.connectedEmail}")
                _userConsentIntentEvent.value = recoverable.intent
            } catch (e: Exception) {
                AppLogger.d("AUTH", "Auto token fetch note: ${e.message}")
            }
        }
        return null
    }

    fun onGoogleAccountConnected(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val email = account.email ?: ""
        val name = account.displayName ?: ""
        prefs.connectGoogleAccount(email, name)
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }

        viewModelScope.launch {
            try {
                val token = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(getApplication(), email)
                if (!token.isNullOrBlank()) {
                    prefs.updateGoogleOAuthAccessToken(token, email, name)
                    AppLogger.s("AUTH", "Google Drive authorized for $email")
                    _uiState.update { it.copy(authError = null) }
                } else {
                    AppLogger.w("AUTH", "Signed in as $email. Drive permission consent pending.")
                }
            } catch (unregistered: com.example.auth.UnregisteredOnApiConsoleException) {
                AppLogger.e("AUTH", "UnregisteredOnApiConsole: ${unregistered.message}")
                _uiState.update { it.copy(authError = unregistered.message) }
            } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                AppLogger.i("AUTH", "Drive consent intent available for $email")
                _userConsentIntentEvent.value = recoverable.intent
            } catch (e: Exception) {
                AppLogger.e("AUTH", "Failed to retrieve initial Drive token: ${e.message}")
                _uiState.update { it.copy(authError = e.message) }
            }
        }
    }

    fun refreshGoogleDriveToken() {
        viewModelScope.launch {
            val email = prefs.settings.value.connectedEmail
            if (email.isBlank()) {
                AppLogger.w("AUTH", "Cannot refresh token: no connected Google account email.")
                return@launch
            }
            try {
                AppLogger.i("AUTH", "Refreshing Google Drive OAuth token for account: $email")
                val token = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(getApplication(), email)
                if (!token.isNullOrBlank()) {
                    prefs.updateGoogleOAuthAccessToken(token, email, prefs.settings.value.connectedDisplayName)
                    AppLogger.s("AUTH", "Google Drive token refreshed successfully.")
                    _uiState.update { it.copy(authError = null) }
                }
            } catch (unregistered: com.example.auth.UnregisteredOnApiConsoleException) {
                AppLogger.e("AUTH", "UnregisteredOnApiConsole: ${unregistered.message}")
                _uiState.update { it.copy(authError = unregistered.message) }
            } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                _userConsentIntentEvent.value = recoverable.intent
            } catch (e: Exception) {
                AppLogger.e("AUTH", "Error refreshing Drive token: ${e.message}")
                _uiState.update { it.copy(authError = e.message) }
            }
        }
    }

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
        if (settings.isHealthConnectEnabled) {
            refreshHealthData()
        }
        if (settings.isHevyEnabled) {
            refreshHevyWorkouts()
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
            val settings = prefs.settings.value
            val isHc = settings.isHealthConnectEnabled
            val isHevy = settings.isHevyEnabled

            if (!isHc && !isHevy) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportResult = SyncResult(
                            isSuccess = false,
                            message = "No sync sources enabled. Please enable Health Connect or Hevy in Settings."
                        )
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isExporting = true, lastExportResult = null) }

            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zoneId)
            val formattedNow = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"))

            AppLogger.i("UI", "Unified manual export triggered. HC: $isHc, Hevy: $isHevy")

            val activeToken = if (settings.demoModeEnabled) null else resolveOrFetchDriveAccessToken()
            if (!settings.demoModeEnabled && activeToken.isNullOrBlank()) {
                val errorMsg = "Google Drive authorization required. Please tap 'Authorize Drive' to link your Google account."
                AppLogger.w("DRIVE", errorMsg)
                val errorResult = SyncResult(
                    isSuccess = false,
                    message = errorMsg,
                    isLocalOnlyFallback = true
                )
                _uiState.update { it.copy(isExporting = false, lastExportResult = errorResult) }
                return@launch
            }

            val successSummaries = mutableListOf<String>()
            val errorSummaries = mutableListOf<String>()
            var latestTodayRecord = _uiState.value.todayRecord
            var latestWorkoutsPayload = _uiState.value.workoutsPayload
            var latestPreviewCsv = _uiState.value.previewCsv
            var anySuccess = false
            var anyLocalFallback = false

            // 1. Health Connect Export
            if (isHc) {
                val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
                val hasPerms = healthManager.hasAllPermissions()

                if (!settings.demoModeEnabled && (!isAvailable || !hasPerms)) {
                    val msg = if (!isAvailable) "Health Connect not available" else "Health Connect permissions needed"
                    errorSummaries.add(msg)
                } else {
                    val today = now.toLocalDate()
                    val record: DailyRecord = if (settings.demoModeEnabled) {
                        healthManager.generateSampleRecord(today, "Demo Mode")
                    } else {
                        healthManager.readDailyRecord(today, zoneId)
                    }
                    latestTodayRecord = record

                    val hcFolder = TargetFolder.HEALTH_DATA
                    val payload = BiometricsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "HealthConnect",
                        timezone = settings.timezoneId,
                        exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        dailyRecords = listOf(record)
                    )

                    val direct = driveClient.syncBiometricsDirectly(
                        accessToken = activeToken,
                        payload = payload,
                        folderPath = hcFolder.folderPath,
                        targetSubfolder = hcFolder.subfolder,
                        fileName = hcFolder.defaultFileName,
                        writeMode = settings.writeMode,
                        archiveMaxDays = settings.archiveMaxDays,
                        isDemoMode = settings.demoModeEnabled,
                        userEmail = settings.connectedEmail.ifBlank { null }
                    )

                    val hcCsv = CsvConverter.toCsvString(listOf(record), settings.writeMode == WriteMode.OVERWRITE)
                    latestPreviewCsv = hcCsv

                    val status = when {
                        direct.isSuccess -> { anySuccess = true; ExportStatus.SUCCESS }
                        direct.isLocalOnlyFallback -> { anyLocalFallback = true; ExportStatus.LOCAL_ONLY }
                        else -> ExportStatus.FAILED
                    }

                    historyStore.addHistoryItem(
                        ExportHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            formattedDate = formattedNow,
                            status = status,
                            writeMode = settings.writeMode,
                            folderPath = hcFolder.folderPath,
                            recordsCount = 1,
                            message = direct.message,
                            payloadPreviewCsv = hcCsv,
                            isManualTrigger = true,
                            sourceApp = "HealthConnect",
                            targetFolderUrl = direct.targetFolderUrl
                        )
                    )

                    if (direct.isSuccess) {
                        successSummaries.add("Health Connect (1 record)")
                    } else if (direct.isLocalOnlyFallback) {
                        successSummaries.add("Health Connect (saved locally)")
                    } else {
                        errorSummaries.add("Health Connect: ${direct.message}")
                    }
                }
            }

            // 2. Hevy Export
            if (isHevy) {
                val fetchResult = hevyManager.fetchWorkouts(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled
                )

                if (fetchResult.isFailure && !settings.demoModeEnabled) {
                    val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Failed to fetch Hevy workouts"
                    errorSummaries.add("Hevy: $errorMsg")
                } else {
                    val payload = fetchResult.getOrElse {
                        WorkoutsExportPayload(
                            exportVersion = "1.0",
                            sourceApp = "Hevy",
                            syncedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                            workouts = hevyManager.getSampleWorkouts()
                        )
                    }
                    val enrichedWorkouts = hevyManager.enrichWithHealthConnect(
                        workouts = payload.workouts,
                        healthManager = healthManager,
                        zoneId = zoneId
                    )
                    val workoutsPayload = payload.copy(
                        syncedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                        workouts = enrichedWorkouts
                    )
                    latestWorkoutsPayload = workoutsPayload

                    val hevyFolder = TargetFolder.GYM_WORKOUTS
                    val direct = driveClient.syncWorkoutsDirectly(
                        accessToken = activeToken,
                        payload = workoutsPayload,
                        folderPath = hevyFolder.folderPath,
                        targetSubfolder = hevyFolder.subfolder,
                        fileName = hevyFolder.defaultFileName,
                        writeMode = settings.writeMode,
                        archiveMaxDays = settings.archiveMaxDays,
                        isDemoMode = settings.demoModeEnabled,
                        userEmail = settings.connectedEmail.ifBlank { null }
                    )

                    val hevyCsv = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)
                    if (!isHc) {
                        latestPreviewCsv = hevyCsv
                    }

                    val status = when {
                        direct.isSuccess -> { anySuccess = true; ExportStatus.SUCCESS }
                        direct.isLocalOnlyFallback -> { anyLocalFallback = true; ExportStatus.LOCAL_ONLY }
                        else -> ExportStatus.FAILED
                    }

                    historyStore.addHistoryItem(
                        ExportHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            formattedDate = formattedNow,
                            status = status,
                            writeMode = settings.writeMode,
                            folderPath = hevyFolder.folderPath,
                            recordsCount = workoutsPayload.workouts.size,
                            message = direct.message,
                            payloadPreviewCsv = hevyCsv,
                            isManualTrigger = true,
                            sourceApp = "Hevy",
                            targetFolderUrl = direct.targetFolderUrl
                        )
                    )

                    if (direct.isSuccess) {
                        successSummaries.add("Hevy (${workoutsPayload.workouts.size} workouts)")
                    } else if (direct.isLocalOnlyFallback) {
                        successSummaries.add("Hevy (saved locally)")
                    } else {
                        errorSummaries.add("Hevy: ${direct.message}")
                    }
                }
            }

            val isOverallSuccess = anySuccess || (anyLocalFallback && errorSummaries.isEmpty())
            val outcomeMsg = buildString {
                if (successSummaries.isNotEmpty()) {
                    append("Successfully exported ")
                    append(successSummaries.joinToString(" and "))
                    append(" to Drive.")
                }
                if (errorSummaries.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append("Notices: ")
                    append(errorSummaries.joinToString("; "))
                }
            }

            prefs.recordSyncOutcome(
                timestamp = System.currentTimeMillis(),
                status = outcomeMsg,
                isSuccess = isOverallSuccess
            )

            val finalResult = SyncResult(
                isSuccess = isOverallSuccess,
                message = outcomeMsg,
                isLocalOnlyFallback = anyLocalFallback && !anySuccess
            )

            _uiState.update {
                it.copy(
                    isExporting = false,
                    lastExportResult = finalResult,
                    todayRecord = latestTodayRecord,
                    workoutsPayload = latestWorkoutsPayload,
                    previewCsv = latestPreviewCsv
                )
            }
        }
    }

    fun testDriveConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingDrive = true, testDriveResult = null) }
            val settings = prefs.settings.value
            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val activeToken = resolveOrFetchDriveAccessToken()

            val direct = driveClient.testDirectDriveConnection(
                accessToken = activeToken,
                folderPath = folderPath,
                userEmail = settings.connectedEmail.ifBlank { null }
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

    fun runDriveDiagnostic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiagnosingDrive = true, diagnosticTestResult = null) }
            val settings = prefs.settings.value
            val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
            val activeToken = resolveOrFetchDriveAccessToken()
            val result = driveClient.runFullDriveDiagnostic(
                accessToken = activeToken,
                folderPath = folderPath,
                userEmail = settings.connectedEmail.ifBlank { null }
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
        val pkgName = com.example.util.CertificateHelper.getPackageName(getApplication())
        val sha1 = com.example.util.CertificateHelper.getSigningSha1(getApplication())
        val sha256 = com.example.util.CertificateHelper.getSigningSha256(getApplication())
        val oauthInfo = """
            • Package Name: $pkgName
            • Signing SHA-1: $sha1
            • Signing SHA-256: $sha256
        """.trimIndent()
        return AppLogger.generateDiagnosticReport(
            connectedEmail = settings.connectedEmail,
            targetFolder = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath },
            hasToken = settings.googleOAuthAccessToken.isNotBlank(),
            isDemoMode = settings.demoModeEnabled,
            cacheSummary = driveClient.cacheManager.getCacheSummary(),
            oauthClientInfo = oauthInfo
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

    fun updateHealthConnectEnabled(enabled: Boolean) {
        prefs.updateHealthConnectEnabled(enabled)
        refreshActiveData()
    }

    fun updateHevyEnabled(enabled: Boolean) {
        prefs.updateHevyEnabled(enabled)
        refreshActiveData()
    }

    fun updateHevyApiKey(key: String) {
        prefs.updateHevyApiKey(key)
        if (prefs.settings.value.isHevyEnabled || prefs.settings.value.sourceApp == SyncSourceApp.HEVY) {
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
        if (settings.connectedEmail.isNotBlank() && settings.googleOAuthAccessToken.isBlank()) {
            viewModelScope.launch {
                try {
                    val token = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(getApplication(), settings.connectedEmail)
                    if (!token.isNullOrBlank()) {
                        prefs.updateGoogleOAuthAccessToken(token)
                        AppLogger.s("AUTH", "Restored Google Drive token on app start for ${settings.connectedEmail}")
                    }
                } catch (e: Exception) {
                    AppLogger.d("AUTH", "Silent token restore check: ${e.message}")
                }
            }
        }
        if (settings.hasCompletedOnboarding || settings.isGoogleConnected || settings.demoModeEnabled) {
            _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }
        } else {
            _uiState.update { it.copy(currentScreen = AppScreen.LANDING) }
        }
    }

    fun connectGoogleAccount(email: String, displayName: String = "") {
        prefs.connectGoogleAccount(email, displayName)
        _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }

        viewModelScope.launch {
            try {
                val token = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(getApplication(), email)
                if (!token.isNullOrBlank()) {
                    prefs.updateGoogleOAuthAccessToken(token, email, displayName)
                    AppLogger.s("AUTH", "Google Drive authorized automatically for $email")
                } else {
                    AppLogger.w("AUTH", "Signed in as $email. Drive permission consent pending.")
                }
            } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                AppLogger.i("AUTH", "Drive consent intent available for $email")
                _userConsentIntentEvent.value = recoverable.intent
            } catch (e: Exception) {
                AppLogger.e("AUTH", "Failed to retrieve initial Drive token: ${e.message}")
            }
        }
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
            val isHc = settings.isHealthConnectEnabled
            val isHevy = settings.isHevyEnabled

            if (!isHc && !isHevy) {
                _uiState.update {
                    it.copy(
                        bulkExportState = BulkExportState(
                            isRunning = false,
                            error = "No sync sources enabled. Please enable Health Connect or Hevy in Settings."
                        )
                    )
                }
                return@launch
            }

            val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zoneId)

            AppLogger.i("BULK", "Unified bulk export initiated. HC: $isHc, Hevy: $isHevy, historyDays: $historyDays")

            val activeToken = if (settings.demoModeEnabled) null else resolveOrFetchDriveAccessToken()
            if (!settings.demoModeEnabled && activeToken.isNullOrBlank()) {
                val errorMsg = "Google Drive authorization required. Please tap 'Authorize Google Drive' to link your Google account."
                AppLogger.w("BULK", errorMsg)
                _uiState.update {
                    it.copy(
                        bulkExportState = BulkExportState(
                            isRunning = false,
                            error = errorMsg
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    bulkExportState = BulkExportState(
                        isRunning = true,
                        phase = "INITIALIZING",
                        statusMessage = "Preparing historical sync for enabled sources..."
                    )
                )
            }

            val totalTasks = (if (isHc) 1 else 0) + (if (isHevy) 1 else 0)
            var currentTaskIndex = 0
            val successSummaries = mutableListOf<String>()
            val errorSummaries = mutableListOf<String>()
            var anySuccess = false
            var anyLocalFallback = false
            var totalExportedRecords = 0

            // 1. Health Connect Bulk Export
            if (isHc) {
                currentTaskIndex++
                val baseProgress = (currentTaskIndex - 1).toFloat() / totalTasks.toFloat()
                val taskWeight = 1.0f / totalTasks.toFloat()

                val isAvailable = healthManager.checkAvailability() == HealthConnectAvailability.AVAILABLE
                val hasPerms = healthManager.hasAllPermissions()

                if (!settings.demoModeEnabled && (!isAvailable || !hasPerms)) {
                    val msg = if (!isAvailable) "Health Connect not available" else "Health Connect permissions missing"
                    errorSummaries.add(msg)
                } else {
                    val today = now.toLocalDate()
                    val startDate = today.minusDays(historyDays.toLong())

                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                statusMessage = "Reading $historyDays days of Health Connect records...",
                                phase = "READING (Health Connect)"
                            )
                        )
                    }

                    val records = healthManager.readHistoricalRecords(
                        startDate = startDate,
                        endDate = today,
                        zoneId = zoneId,
                        isDemoMode = settings.demoModeEnabled
                    ) { current, total, date ->
                        val readProgress = baseProgress + ((current.toFloat() / total.toFloat()) * 0.5f * taskWeight)
                        _uiState.update {
                            it.copy(
                                bulkExportState = it.bulkExportState.copy(
                                    current = current,
                                    total = total,
                                    percentage = readProgress,
                                    statusMessage = "Reading Health Connect: $date ($current/$total days)..."
                                )
                            )
                        }
                    }

                    val chunks = records.chunked(60)
                    var uploadedCount = 0
                    var lastDirectResult: com.example.drive.DirectDriveResult? = null
                    val hcFolder = TargetFolder.HEALTH_DATA

                    for ((idx, chunk) in chunks.withIndex()) {
                        val uploadProgress = baseProgress + (0.5f * taskWeight) + (((idx + 1).toFloat() / chunks.size.toFloat()) * 0.5f * taskWeight)
                        _uiState.update {
                            it.copy(
                                bulkExportState = it.bulkExportState.copy(
                                    percentage = uploadProgress,
                                    statusMessage = "Uploading Health Connect chunk ${idx + 1}/${chunks.size} (${chunk.size} records)...",
                                    phase = "UPLOADING (Health Connect)"
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
                            accessToken = activeToken,
                            payload = payload,
                            folderPath = hcFolder.folderPath,
                            targetSubfolder = hcFolder.subfolder,
                            fileName = hcFolder.defaultFileName,
                            writeMode = WriteMode.APPEND,
                            archiveMaxDays = settings.archiveMaxDays,
                            isDemoMode = settings.demoModeEnabled,
                            userEmail = settings.connectedEmail.ifBlank { null }
                        )
                        lastDirectResult = direct
                        uploadedCount += chunk.size
                        kotlinx.coroutines.delay(100L)
                    }

                    val hcSuccess = lastDirectResult?.isSuccess == true
                    val hcFallback = lastDirectResult?.isLocalOnlyFallback == true
                    if (hcSuccess) anySuccess = true
                    if (hcFallback) anyLocalFallback = true
                    totalExportedRecords += uploadedCount

                    val historyStatus = when {
                        hcSuccess -> ExportStatus.SUCCESS
                        hcFallback -> ExportStatus.LOCAL_ONLY
                        else -> ExportStatus.FAILED
                    }

                    val historyMessage = when {
                        hcSuccess -> "Bulk export completed: $uploadedCount Health records exported to Drive."
                        hcFallback -> "Bulk export saved locally ($uploadedCount records). Drive upload skipped."
                        else -> "Health Connect bulk export failed: ${lastDirectResult?.message}"
                    }

                    historyStore.addHistoryItem(
                        ExportHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            status = historyStatus,
                            writeMode = WriteMode.APPEND,
                            folderPath = hcFolder.folderPath,
                            recordsCount = uploadedCount,
                            message = historyMessage,
                            payloadPreviewCsv = "",
                            isManualTrigger = true,
                            sourceApp = "HealthConnect",
                            targetFolderUrl = lastDirectResult?.targetFolderUrl
                        )
                    )

                    if (hcSuccess) {
                        successSummaries.add("Health Connect ($uploadedCount records)")
                    } else if (hcFallback) {
                        successSummaries.add("Health Connect ($uploadedCount records local)")
                    } else {
                        errorSummaries.add("Health Connect: ${lastDirectResult?.message}")
                    }
                }
            }

            // 2. Hevy Bulk Export
            if (isHevy) {
                currentTaskIndex++
                val baseProgress = (currentTaskIndex - 1).toFloat() / totalTasks.toFloat()
                val taskWeight = 1.0f / totalTasks.toFloat()

                _uiState.update {
                    it.copy(
                        bulkExportState = it.bulkExportState.copy(
                            statusMessage = "Fetching workouts from Hevy API...",
                            phase = "READING (Hevy)"
                        )
                    )
                }

                val hevyResult = hevyManager.fetchAllWorkoutsPaginated(
                    apiKey = settings.hevyApiKey,
                    isDemoMode = settings.demoModeEnabled,
                    pageSize = 10
                ) { page, totalPages, workoutsCount ->
                    val readProgress = baseProgress + (if (totalPages > 0) (page.toFloat() / totalPages.toFloat()) * 0.5f * taskWeight else 0.25f * taskWeight)
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
                    errorSummaries.add("Hevy: $errMsg")
                } else {
                    val allWorkouts = hevyResult.getOrNull() ?: emptyList()
                    val enrichedWorkouts = hevyManager.enrichWithHealthConnect(
                        workouts = allWorkouts,
                        healthManager = healthManager,
                        zoneId = zoneId
                    )
                    val hevyFolder = TargetFolder.GYM_WORKOUTS

                    _uiState.update {
                        it.copy(
                            bulkExportState = it.bulkExportState.copy(
                                percentage = baseProgress + (0.7f * taskWeight),
                                statusMessage = "Uploading ${enrichedWorkouts.size} Hevy workouts to Drive...",
                                phase = "UPLOADING (Hevy)"
                            )
                        )
                    }

                    val payload = WorkoutsExportPayload(
                        exportVersion = "1.0",
                        sourceApp = "Hevy",
                        syncedAt = now.format(DateTimeFormatter.ISO_INSTANT),
                        workouts = enrichedWorkouts
                    )

                    val direct = driveClient.syncWorkoutsDirectly(
                        accessToken = activeToken,
                        payload = payload,
                        folderPath = hevyFolder.folderPath,
                        targetSubfolder = hevyFolder.subfolder,
                        fileName = hevyFolder.defaultFileName,
                        writeMode = WriteMode.APPEND,
                        archiveMaxDays = settings.archiveMaxDays,
                        isDemoMode = settings.demoModeEnabled,
                        userEmail = settings.connectedEmail.ifBlank { null }
                    )

                    val hevySuccess = direct.isSuccess
                    val hevyFallback = direct.isLocalOnlyFallback
                    if (hevySuccess) anySuccess = true
                    if (hevyFallback) anyLocalFallback = true
                    totalExportedRecords += allWorkouts.size

                    val historyStatus = when {
                        hevySuccess -> ExportStatus.SUCCESS
                        hevyFallback -> ExportStatus.LOCAL_ONLY
                        else -> ExportStatus.FAILED
                    }

                    val historyMessage = when {
                        hevySuccess -> "Bulk export completed: ${allWorkouts.size} Hevy workouts exported to Drive."
                        hevyFallback -> "Bulk export completed locally (${allWorkouts.size} workouts). Drive upload skipped."
                        else -> "Hevy bulk export failed: ${direct.message}"
                    }

                    historyStore.addHistoryItem(
                        ExportHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            status = historyStatus,
                            writeMode = WriteMode.APPEND,
                            folderPath = hevyFolder.folderPath,
                            recordsCount = allWorkouts.size,
                            message = historyMessage,
                            payloadPreviewCsv = "",
                            isManualTrigger = true,
                            sourceApp = "Hevy",
                            targetFolderUrl = direct.targetFolderUrl
                        )
                    )

                    if (hevySuccess) {
                        successSummaries.add("Hevy (${allWorkouts.size} workouts)")
                    } else if (hevyFallback) {
                        successSummaries.add("Hevy (${allWorkouts.size} workouts local)")
                    } else {
                        errorSummaries.add("Hevy: ${direct.message}")
                    }
                }
            }

            prefs.recordInitialBulkExportCompleted()
            val isFinalSuccess = anySuccess || (anyLocalFallback && errorSummaries.isEmpty())
            val summaryMsg = buildString {
                if (successSummaries.isNotEmpty()) {
                    append("Bulk export complete: ")
                    append(successSummaries.joinToString(" and "))
                    append(" exported.")
                }
                if (errorSummaries.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append("Issues: ")
                    append(errorSummaries.joinToString("; "))
                }
            }

            prefs.recordSyncOutcome(
                timestamp = System.currentTimeMillis(),
                status = summaryMsg,
                isSuccess = isFinalSuccess
            )

            _uiState.update {
                it.copy(
                    bulkExportState = it.bulkExportState.copy(
                        isRunning = false,
                        percentage = 1f,
                        isCompleted = true,
                        recordsExported = totalExportedRecords,
                        statusMessage = summaryMsg,
                        error = if (!isFinalSuccess && errorSummaries.isNotEmpty()) errorSummaries.joinToString("; ") else null
                    ),
                    lastExportResult = SyncResult(
                        isSuccess = isFinalSuccess,
                        message = summaryMsg,
                        isLocalOnlyFallback = anyLocalFallback && !anySuccess
                    )
                )
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
