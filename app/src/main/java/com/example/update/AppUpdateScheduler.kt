package com.example.update

import android.content.Context
import androidx.work.*
import com.example.util.AppLogger
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {

    const val PERIODIC_UPDATE_CHECK_TAG = "app_update_periodic_check"
    const val ONE_TIME_UPDATE_CHECK_TAG = "app_update_immediate_check"

    /**
     * Schedules periodic check once every 2 days with a 6-hour flex interval.
     */
    fun schedulePeriodicUpdateCheck(context: Context, enabled: Boolean = true) {
        try {
            val workManager = WorkManager.getInstance(context)

            if (!enabled) {
                workManager.cancelUniqueWork(PERIODIC_UPDATE_CHECK_TAG)
                AppLogger.d("UPDATE", "Periodic update check cancelled.")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Run once every 2 days (48 hours)
            val periodicRequest = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(
                2L, TimeUnit.DAYS,
                6L, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(PERIODIC_UPDATE_CHECK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_UPDATE_CHECK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            AppLogger.d("UPDATE", "Scheduled periodic background update check (every 2 days).")
        } catch (e: Throwable) {
            AppLogger.w("UPDATE", "Failed to schedule periodic update check: ${e.message}")
        }
    }

    /**
     * Triggers an immediate one-time background check.
     */
    fun triggerImmediateCheck(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
                .setConstraints(constraints)
                .addTag(ONE_TIME_UPDATE_CHECK_TAG)
                .build()

            workManager.enqueue(oneTimeRequest)
        } catch (_: Throwable) {}
    }
}
