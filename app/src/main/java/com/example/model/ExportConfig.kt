package com.example.model

/**
 * Write/Append options: Update Daily Record (default) or Replace Entire File.
 */
enum class WriteMode(val displayName: String, val description: String) {
    APPEND("Update Daily Record", "Updates today's record in-place; appends new days"),
    OVERWRITE("Replace Entire File", "Overwrites the entire CSV file")
}

/**
 * Target Google Drive folder path structure:
 * My Drive /
 *   └── nalama.family/
 *        └── imports/
 *             ├── health_data/  <- Biometrics, Sleep, & Steps (biometrics_daily.csv)
 *             └── gym_workouts/ <- Workouts (hevy_workouts.csv)
 */
enum class TargetFolder(
    val folderPath: String,
    val subfolder: String,
    val defaultFileName: String,
    val description: String
) {
    HEALTH_DATA(
        folderPath = "nalama.family/imports/health_data",
        subfolder = "health_data",
        defaultFileName = "biometrics_daily",
        description = "Biometrics, Sleep, & Steps (Health Connect)"
    ),
    GYM_WORKOUTS(
        folderPath = "nalama.family/imports/gym_workouts",
        subfolder = "gym_workouts",
        defaultFileName = "hevy_workouts",
        description = "Gym Workouts (Hevy)"
    )
}

data class ExportHistoryItem(
    val id: String,
    val timestamp: Long,
    val formattedDate: String,
    val status: ExportStatus,
    val writeMode: WriteMode = WriteMode.APPEND,
    val folderPath: String,
    val recordsCount: Int,
    val message: String,
    val payloadPreviewCsv: String? = null,
    val isManualTrigger: Boolean = false,
    val sourceApp: String = "HealthConnect"
)

enum class ExportStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS
}
