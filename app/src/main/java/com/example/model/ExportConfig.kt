package com.example.model

/**
 * Format options: CSV (default), JSON, or Both.
 */
enum class ExportFormat(val displayName: String) {
    CSV("CSV (*.csv)"),
    JSON("JSON (*.json)"),
    BOTH("Both (CSV & JSON)")
}

/**
 * Write/Append options: In-place Upsert (default) or Full Overwrite.
 */
enum class WriteMode(val displayName: String, val description: String) {
    APPEND("In-place Update", "Updates today's record in-place; appends new days"),
    OVERWRITE("Full Overwrite", "Overwrites the entire file or sheet")
}

/**
 * Target Google Drive folder path structure as specified:
 * My Drive /
 *   └── nalama.family/
 *        └── imports/
 *             ├── health_data/  <- Place Biometrics, Sleep, & Steps here
 *             │    └── biometrics_daily.json / *.csv
 *             └── gym_workouts/ <- Place Hevy / Strong workouts here
 *                  └── hevy_workouts.json / *.csv
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
        description = "Hevy / Strong Workouts"
    )
}

data class ExportHistoryItem(
    val id: String,
    val timestamp: Long,
    val formattedDate: String,
    val status: ExportStatus,
    val format: ExportFormat,
    val writeMode: WriteMode,
    val folderPath: String,
    val recordsCount: Int,
    val httpStatusCode: Int?,
    val message: String,
    val payloadPreviewJson: String? = null,
    val payloadPreviewCsv: String? = null,
    val isManualTrigger: Boolean = false,
    val sourceApp: String = "HealthConnect"
)

enum class ExportStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS
}
