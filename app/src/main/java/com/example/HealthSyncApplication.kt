package com.example

import android.app.Application
import com.example.data.ExportHistoryStore
import com.example.data.PreferencesManager
import com.example.sync.SyncScheduler

class HealthSyncApplication : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var historyStore: ExportHistoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        historyStore = ExportHistoryStore(this)

        try {
            val settings = preferencesManager.settings.value
            SyncScheduler.schedulePeriodicSync(
                context = this,
                intervalMinutes = settings.syncIntervalMinutes,
                autoSyncEnabled = settings.autoSyncEnabled
            )
        } catch (_: Throwable) {
            // WorkManager may be uninitialized in test or preview environments
        }
    }
}
