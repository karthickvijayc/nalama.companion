package com.example.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    const val PERIODIC_WORK_TAG = "health_connect_periodic_sync"
    const val ONE_TIME_WORK_TAG = "health_connect_immediate_sync"

    fun schedulePeriodicSync(context: Context, intervalMinutes: Int, autoSyncEnabled: Boolean) {
        try {
            val workManager = WorkManager.getInstance(context)

            if (!autoSyncEnabled) {
                workManager.cancelUniqueWork(PERIODIC_WORK_TAG)
                return
            }

            // WorkManager minimum periodic interval is 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                effectiveInterval.toLong(),
                TimeUnit.MINUTES,
                5L, // flex interval
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(PERIODIC_WORK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        } catch (_: Throwable) {
            // WorkManager not initialized or available
        }
    }

    fun triggerImmediateSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putBoolean("is_manual", true)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(ONE_TIME_WORK_TAG)
                .build()

            workManager.enqueue(oneTimeRequest)
        } catch (_: Throwable) {
            // WorkManager not initialized or available
        }
    }

    fun cancelAllSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(PERIODIC_WORK_TAG)
        } catch (_: Throwable) {
            // WorkManager not initialized or available
        }
    }
}
