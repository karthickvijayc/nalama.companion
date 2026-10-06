package com.example.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.HealthSyncApplication
import com.example.util.AppLogger

class AppUpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? HealthSyncApplication
        val prefs = app?.preferencesManager

        val settings = prefs?.settings?.value
        val isEnabled = settings?.autoUpdateCheckEnabled ?: true

        if (!isEnabled) {
            AppLogger.d("UPDATE", "Background update check disabled in settings. Skipping.")
            return Result.success()
        }

        AppLogger.i("UPDATE", "Starting background update check against GitHub releases...")
        val updateManager = AppUpdateManager()
        val checkResult = updateManager.checkForLatestRelease(BuildConfig.VERSION_NAME)

        return if (checkResult.isSuccess) {
            val release = checkResult.getOrThrow()
            prefs?.recordUpdateCheck(
                timestamp = System.currentTimeMillis(),
                latestVersion = release.tagName,
                downloadUrl = release.downloadUrl
            )

            if (release.isNewer) {
                val lastNotified = settings?.lastNotifiedUpdateVersion ?: ""
                if (lastNotified != release.tagName) {
                    AppLogger.s("UPDATE", "New version detected (${release.tagName}). Showing notification to user.")
                    updateManager.showUpdateNotification(context, release)
                    prefs?.recordUpdateNotified(release.tagName)
                } else {
                    AppLogger.d("UPDATE", "User was already notified for ${release.tagName}. Skipping duplicate notification.")
                }
            } else {
                AppLogger.d("UPDATE", "App is up to date (current: ${BuildConfig.VERSION_NAME}, latest: ${release.tagName}).")
            }
            Result.success()
        } else {
            val error = checkResult.exceptionOrNull()?.message ?: "Unknown error"
            AppLogger.w("UPDATE", "Background update check failed: $error")
            // Succeeded without error so WorkManager waits for the next multi-day interval
            Result.success()
        }
    }
}
