package com.example.model

/**
 * Supported source applications for sync.
 */
enum class SyncSourceApp(
    val id: String,
    val displayName: String,
    val category: String,
    val defaultTargetFolder: TargetFolder,
    val defaultFileName: String,
    val description: String
) {
    HEALTH_CONNECT(
        id = "health_connect",
        displayName = "Health Connect",
        category = "Biometrics & Sleep",
        defaultTargetFolder = TargetFolder.HEALTH_DATA,
        defaultFileName = "biometrics_daily",
        description = "Steps, distance, sleep stages, resting HR, HRV, BP, and vitals from Android Health Connect."
    ),
    HEVY(
        id = "hevy",
        displayName = "Hevy",
        category = "Gym Workouts",
        defaultTargetFolder = TargetFolder.GYM_WORKOUTS,
        defaultFileName = "hevy_workouts",
        description = "Log gym workouts, exercises, weights (kg), reps, sets, RPE, and volume from Hevy via API or local export."
    );

    companion object {
        fun fromId(id: String): SyncSourceApp =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HEALTH_CONNECT
    }
}
