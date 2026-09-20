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

        val folderPath = settings.customFolderPath.ifBlank { settings.targetFolder.folderPath }
        val subfolder = settings.targetFolder.subfolder
        val defaultFileName = settings.targetFolder.defaultFileName
        val driveClient = GoogleDriveDirectClient(context)

        if (settings.sourceApp == SyncSourceApp.HEVY) {
            val hevyManager = HevySyncManager()
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

            val directResult = driveClient.syncWorkoutsDirectly(
                accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                payload = workoutsPayload,
                folderPath = folderPath,
                targetSubfolder = subfolder,
                fileName = defaultFileName,
                writeMode = settings.writeMode,
                archiveMaxDays = settings.archiveMaxDays
            )

            val csvPreview = WorkoutCsvConverter.toCsvString(workoutsPayload.workouts, settings.writeMode == WriteMode.OVERWRITE)

            val historyItem = ExportHistoryItem(
                id = UUID.randomUUID().toString(),
                timestamp = startTime,
                formattedDate = formattedNow,
                status = if (directResult.isSuccess) ExportStatus.SUCCESS else ExportStatus.FAILED,
                writeMode = settings.writeMode,
                folderPath = folderPath,
                recordsCount = workoutsPayload.workouts.size,
                message = directResult.message,
                payloadPreviewCsv = csvPreview,
                isManualTrigger = isManual,
                sourceApp = "Hevy"
            )

            historyStore.addHistoryItem(historyItem)
            prefs.recordSyncOutcome(
                timestamp = startTime,
                status = if (directResult.isSuccess) "Exported ${workoutsPayload.workouts.size} Hevy workout(s) to $folderPath" else directResult.message,
                isSuccess = directResult.isSuccess
            )

            if (directResult.isSuccess) {
                showNotification("Workout Sync Succeeded", "Synced ${workoutsPayload.workouts.size} workout(s) to Google Drive.")
                return Result.success()
            } else {
                showNotification("Workout Sync Notice", directResult.message)
                return Result.retry()
            }
        } else {
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
                accessToken = settings.googleOAuthAccessToken.ifBlank { null },
                payload = exportPayload,
                folderPath = folderPath,
                targetSubfolder = subfolder,
                fileName = defaultFileName,
                writeMode = settings.writeMode,
                archiveMaxDays = settings.archiveMaxDays
            )

            val csvPreview = CsvConverter.toCsvString(exportPayload.dailyRecords, settings.writeMode == WriteMode.OVERWRITE)

            val historyItem = ExportHistoryItem(
                id = UUID.randomUUID().toString(),
                timestamp = startTime,
                formattedDate = formattedNow,
                status = if (directResult.isSuccess) ExportStatus.SUCCESS else ExportStatus.FAILED,
                writeMode = settings.writeMode,
                folderPath = folderPath,
                recordsCount = dailyRecords.size,
                message = directResult.message,
                payloadPreviewCsv = csvPreview,
                isManualTrigger = isManual,
                sourceApp = "HealthConnect"
            )

            historyStore.addHistoryItem(historyItem)
            prefs.recordSyncOutcome(
                timestamp = startTime,
                status = if (directResult.isSuccess) "Exported ${dailyRecords.size} health record(s) to $folderPath" else directResult.message,
                isSuccess = directResult.isSuccess
            )

            if (directResult.isSuccess) {
                showNotification("Health Sync Succeeded", "Synced ${dailyRecords.size} record(s) to Google Drive.")
                return Result.success()
            } else {
                showNotification("Health Sync Notice", directResult.message)
                return Result.retry()
            }
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

