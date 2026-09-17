package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.HealthSyncApplication
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.health.HevySyncManager
import com.example.model.*
import com.example.network.GoogleAppsScriptWebhookClient
import com.example.util.CsvConverter
import com.example.util.JsonConverter
import com.example.util.WorkoutCsvConverter
import com.example.util.WorkoutJsonConverter
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
        val webhookClient = GoogleAppsScriptWebhookClient()

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
                timestamp = startTime,
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
                isManualTrigger = isManual,
                sourceApp = "Hevy"
            )

            historyStore.addHistoryItem(historyItem)
            prefs.recordSyncOutcome(
                timestamp = startTime,
                status = if (result.isSuccess) "Exported ${workoutsPayload.workouts.size} Hevy workout(s) to $folderPath" else result.message,
                isSuccess = result.isSuccess
            )

            if (result.isSuccess) {
                showNotification("Workout Sync Succeeded", "Exported ${workoutsPayload.workouts.size} workout(s) to Google Drive.")
                return Result.success()
            } else {
                showNotification("Workout Sync Notice", result.message)
                return Result.retry()
            }
        } else {
            val healthManager = HealthConnectManager(context)
            val today = now.toLocalDate()

            val dailyRecords: List<DailyRecord> = if (settings.demoModeEnabled) {
                listOf(healthManager.generateSampleRecord(today, "Demo Mode (Simulation)"))
            } else {
                val availability = healthManager.checkAvailability()
                if (availability == HealthConnectAvailability.AVAILABLE && healthManager.hasAllPermissions()) {
                    val record = healthManager.readDailyRecord(today, zoneId)
                    listOf(record)
                } else {
                    listOf(healthManager.generateSampleRecord(today, "HealthConnect (Default Metrics)"))
                }
            }

            val exportPayload = BiometricsExportPayload(
                exportVersion = "1.0",
                sourceApp = "HealthConnect",
                timezone = settings.timezoneId,
                exportedAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                dailyRecords = dailyRecords
            )

            val result = webhookClient.postExport(
                webhookUrl = settings.webhookUrl,
                payload = exportPayload,
                folderPath = folderPath,
                targetSubfolder = subfolder,
                fileName = defaultFileName,
                format = settings.exportFormat,
                writeMode = settings.writeMode
            )

            val jsonPreview = JsonConverter.toJsonString(exportPayload, 2)
            val csvPreview = CsvConverter.toCsvString(exportPayload.dailyRecords, settings.writeMode == WriteMode.OVERWRITE)

            val historyItem = ExportHistoryItem(
                id = UUID.randomUUID().toString(),
                timestamp = startTime,
                formattedDate = formattedNow,
                status = if (result.isSuccess) ExportStatus.SUCCESS else ExportStatus.FAILED,
                format = settings.exportFormat,
                writeMode = settings.writeMode,
                folderPath = folderPath,
                recordsCount = dailyRecords.size,
                httpStatusCode = result.httpCode,
                message = result.message,
                payloadPreviewJson = jsonPreview,
                payloadPreviewCsv = csvPreview,
                isManualTrigger = isManual,
                sourceApp = "HealthConnect"
            )

            historyStore.addHistoryItem(historyItem)
            prefs.recordSyncOutcome(
                timestamp = startTime,
                status = if (result.isSuccess) "Exported ${dailyRecords.size} health record(s) to $folderPath" else result.message,
                isSuccess = result.isSuccess
            )

            if (result.isSuccess) {
                showNotification("Health Sync Succeeded", "Exported ${dailyRecords.size} daily record(s) to Google Drive.")
                return Result.success()
            } else {
                showNotification("Health Sync Notice", result.message)
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
                    description = "Notifications for Health & Hevy exports to Google Sheets/Drive"
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
