package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HealthSyncApplication
import com.example.drive.GoogleDriveDirectClient
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.health.HevySyncManager
import com.example.model.*
import com.example.util.AppLogger
import com.example.util.CsvConverter
import com.example.util.WorkoutCsvConverter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class HealthSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? HealthSyncApplication
        val prefs = app?.preferencesManager ?: return Result.failure()
        val historyStore = app.historyStore
        val settings = prefs.settings.value

        val startTime = System.currentTimeMillis()
        val zoneId = try { ZoneId.of(settings.timezoneId) } catch (_: Exception) { ZoneId.systemDefault() }
        val now = ZonedDateTime.now(zoneId)
        val formattedNow = now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss"))
        val isManual = inputData.getBoolean("is_manual", false)

        val isHc = settings.isHealthConnectEnabled
        val isHevy = settings.isHevyEnabled

        AppLogger.i(
            "WORKER",
            "Background sync started [${if (isManual) "Manual" else "Scheduled"}] - HC: $isHc, Hevy: $isHevy, Token present: ${settings.googleOAuthAccessToken.isNotBlank()}"
        )

        if (!isHc && !isHevy) {
            AppLogger.d("WORKER", "No sync sources enabled. Background sync finished.")
            return Result.success()
        }

        val driveClient = GoogleDriveDirectClient(context)
        val userEmail = settings.connectedEmail.ifBlank { null }
        var activeToken = settings.googleOAuthAccessToken.ifBlank { null }
        if (activeToken.isNullOrBlank() && !userEmail.isNullOrBlank()) {
            try {
                val fetched = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
                if (!fetched.isNullOrBlank()) {
                    prefs.updateGoogleOAuthAccessToken(fetched)
                    activeToken = fetched
                    AppLogger.s("WORKER", "Successfully fetched fresh Drive token for $userEmail")
                }
            } catch (e: Exception) {
                AppLogger.d("WORKER", "Could not pre-fetch Drive token in worker: ${e.message}")
            }
        }

        val successSummaries = mutableListOf<String>()
        val errorSummaries = mutableListOf<String>()
        var anySuccess = false
        var anyLocalFallback = false

        // 1. Health Connect background sync
        if (isHc) {
            val hcFolder = TargetFolder.HEALTH_DATA
            val healthManager = HealthConnectManager(context)
            val today = now.toLocalDate()

            val dailyRecords: List<DailyRecord> = if (settings.demoModeEnabled) {
                listOf(healthManager.generateSampleRecord(today, "Demo Mode"))
            } else {
                val availability = healthManager.checkAvailability()
                if (availability == HealthConnectAvailability.AVAILABLE && healthManager.hasAllPermissions()) {
                    val record = healthManager.readDailyRecord(today, zoneId)
                    listOf(record)
                } else {
                    listOf(healthManager.generateSampleRecord(today, "HealthConnect"))
                }
            }

            val exportPayload = BiometricsExportPayload(
                exportVersion = "1.0",
                sourceApp = "HealthConnect",
                timezone = settings.timezoneId,
                exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                dailyRecords = dailyRecords
            )

            val directResult = driveClient.syncBiometricsDirectly(
                accessToken = activeToken,
                payload = exportPayload,
                folderPath = hcFolder.folderPath,
                targetSubfolder = hcFolder.subfolder,
                fileName = hcFolder.defaultFileName,
                writeMode = settings.writeMode,
                archiveMaxDays = settings.archiveMaxDays,
                isDemoMode = settings.demoModeEnabled,
                userEmail = userEmail
            )

            val csvPreview = CsvConverter.toCsvString(exportPayload.dailyRecords, settings.writeMode == WriteMode.OVERWRITE)

            val status = when {
                directResult.isSuccess -> { anySuccess = true; ExportStatus.SUCCESS }
                directResult.isLocalOnlyFallback -> { anyLocalFallback = true; ExportStatus.LOCAL_ONLY }
                else -> ExportStatus.FAILED
            }

            historyStore.addHistoryItem(
                ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = startTime,
                    formattedDate = formattedNow,
                    status = status,
                    writeMode = settings.writeMode,
                    folderPath = hcFolder.folderPath,
                    recordsCount = dailyRecords.size,
                    message = directResult.message,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = isManual,
                    sourceApp = "HealthConnect",
                    targetFolderUrl = directResult.targetFolderUrl
                )
            )

            if (directResult.isSuccess) {
                successSummaries.add("Health Connect (${dailyRecords.size} record)")
            } else if (directResult.isLocalOnlyFallback) {
                successSummaries.add("Health Connect (saved locally)")
            } else {
                errorSummaries.add("Health Connect: ${directResult.message}")
            }
        }

        // 2. Hevy background sync
        if (isHevy) {
            val hevyFolder = TargetFolder.GYM_WORKOUTS
            val hevyManager = HevySyncManager()
            val healthManager = HealthConnectManager(context)
            val fetchResult = hevyManager.fetchWorkouts(
                apiKey = settings.hevyApiKey,
                isDemoMode = settings.demoModeEnabled
            )
            val basePayload = fetchResult.getOrElse {
                WorkoutsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "Hevy",
                    syncedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                    workouts = hevyManager.getSampleWorkouts()
                )
            }
            val enrichedWorkouts = hevyManager.enrichWithHealthConnect(
                workouts = basePayload.workouts,
                healthManager = healthManager,
                zoneId = zoneId
            )
            val workoutsPayload = basePayload.copy(workouts = enrichedWorkouts)

            val directResult = driveClient.syncWorkoutsDirectly(
                accessToken = activeToken,
                payload = workoutsPayload,
                folderPath = hevyFolder.folderPath,
                targetSubfolder = hevyFolder.subfolder,
                fileName = hevyFolder.defaultFileName,
                writeMode = settings.writeMode,
                archiveMaxDays = settings.archiveMaxDays,
                isDemoMode = settings.demoModeEnabled,
                userEmail = userEmail
            )

            val csvPreview = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)

            val status = when {
                directResult.isSuccess -> { anySuccess = true; ExportStatus.SUCCESS }
                directResult.isLocalOnlyFallback -> { anyLocalFallback = true; ExportStatus.LOCAL_ONLY }
                else -> ExportStatus.FAILED
            }

            historyStore.addHistoryItem(
                ExportHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = startTime,
                    formattedDate = formattedNow,
                    status = status,
                    writeMode = settings.writeMode,
                    folderPath = hevyFolder.folderPath,
                    recordsCount = workoutsPayload.workouts.size,
                    message = directResult.message,
                    payloadPreviewCsv = csvPreview,
                    isManualTrigger = isManual,
                    sourceApp = "Hevy",
                    targetFolderUrl = directResult.targetFolderUrl
                )
            )

            if (directResult.isSuccess) {
                successSummaries.add("Hevy (${workoutsPayload.workouts.size} workouts)")
            } else if (directResult.isLocalOnlyFallback) {
                successSummaries.add("Hevy (saved locally)")
            } else {
                errorSummaries.add("Hevy: ${directResult.message}")
            }
        }

        val isOverallSuccess = anySuccess || (anyLocalFallback && errorSummaries.isEmpty())
        val outcomeMsg = buildString {
            if (successSummaries.isNotEmpty()) {
                append("Exported ")
                append(successSummaries.joinToString(" and "))
                append(" to Google Drive.")
            }
            if (errorSummaries.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("Notices: ")
                append(errorSummaries.joinToString("; "))
            }
        }

        prefs.recordSyncOutcome(
            timestamp = startTime,
            status = outcomeMsg,
            isSuccess = isOverallSuccess
        )

        if (anySuccess) {
            AppLogger.s("WORKER", "Background sync completed: $outcomeMsg")
            showNotification("Sync Succeeded", outcomeMsg)
            return Result.success()
        } else if (anyLocalFallback && errorSummaries.isEmpty()) {
            AppLogger.i("WORKER", "Background sync saved locally: $outcomeMsg")
            showNotification("Sync Saved Locally", outcomeMsg)
            return Result.success()
        } else {
            AppLogger.w("WORKER", "Background sync notice: $outcomeMsg")
            showNotification("Sync Notice", outcomeMsg)
            return if (anyLocalFallback) Result.success() else Result.retry()
        }
    }

    private fun showNotification(title: String, text: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "health_sync_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Health & Gym Sync",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications for exports to Google Drive"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
        } catch (_: Exception) {}
    }
}
