package com.example.util

import com.example.model.DailyRecord

object CsvConverter {

    val CSV_HEADER = listOf(
        "date",
        "sources",
        "steps",
        "distance_meters",
        "total_calories_kcal",
        "active_calories_kcal",
        "active_duration_minutes",
        "vo2_max_avg",
        "total_sleep_minutes",
        "light_sleep_minutes",
        "deep_sleep_minutes",
        "rem_sleep_minutes",
        "awake_minutes",
        "sleep_efficiency_score",
        "resting_hr_min",
        "resting_hr_max",
        "resting_hr_avg",
        "hrv_ms_avg",
        "oxygen_saturation_pct_avg",
        "bp_systolic",
        "bp_diastolic",
        "bp_pulse",
        "weight_kg",
        "body_fat_pct",
        "lean_body_mass_kg"
    ).joinToString(",")

    fun getHeaderList(): List<String> = CSV_HEADER.split(",")

    /**
     * Extracts tabular cell values for each record, ready for direct spreadsheet rows.
     */
    fun toRowList(records: List<DailyRecord>): List<List<Any?>> {
        return records.map { record ->
            listOf(
                record.date,
                record.sources.joinToString(";"),
                record.activity.steps,
                record.activity.distanceMeters,
                record.activity.totalCaloriesKcal,
                record.activity.activeCaloriesKcal,
                record.activity.activeDurationMinutes,
                record.activity.vo2MaxMlKgMin?.avg ?: "",
                record.sleep.totalSleepMinutes,
                record.sleep.lightSleepMinutes,
                record.sleep.deepSleepMinutes,
                record.sleep.remSleepMinutes,
                record.sleep.awakeMinutes,
                record.sleep.sleepEfficiencyScore,
                record.vitals.restingHeartRateBpm?.min ?: "",
                record.vitals.restingHeartRateBpm?.max ?: "",
                record.vitals.restingHeartRateBpm?.avg ?: "",
                record.vitals.heartRateVariabilityMs?.avg ?: "",
                record.vitals.oxygenSaturationPct?.avg ?: "",
                record.vitals.bloodPressureMmHg?.systolic ?: "",
                record.vitals.bloodPressureMmHg?.diastolic ?: "",
                record.vitals.bloodPressureMmHg?.pulse ?: "",
                record.bodyMeasurements.weightKg ?: "",
                record.bodyMeasurements.bodyFatPct ?: "",
                record.bodyMeasurements.leanBodyMassKg ?: ""
            )
        }
    }

    /**
     * Converts records to CSV string.
     * Always includes headers to ensure Drive and Apps Script handlers have proper schema.
     */
    fun toCsvString(records: List<DailyRecord>, includeHeader: Boolean = true): String {
        val sb = StringBuilder()
        if (includeHeader) {
            sb.append(CSV_HEADER).append("\n")
        }

        for (record in records) {
            val row = listOf(
                escapeCsv(record.date),
                escapeCsv(record.sources.joinToString(";")),
                record.activity.steps.toString(),
                record.activity.distanceMeters.toString(),
                record.activity.totalCaloriesKcal.toString(),
                record.activity.activeCaloriesKcal.toString(),
                record.activity.activeDurationMinutes.toString(),
                record.activity.vo2MaxMlKgMin?.avg?.toString() ?: "",
                record.sleep.totalSleepMinutes.toString(),
                record.sleep.lightSleepMinutes.toString(),
                record.sleep.deepSleepMinutes.toString(),
                record.sleep.remSleepMinutes.toString(),
                record.sleep.awakeMinutes.toString(),
                record.sleep.sleepEfficiencyScore.toString(),
                record.vitals.restingHeartRateBpm?.min?.toString() ?: "",
                record.vitals.restingHeartRateBpm?.max?.toString() ?: "",
                record.vitals.restingHeartRateBpm?.avg?.toString() ?: "",
                record.vitals.heartRateVariabilityMs?.avg?.toString() ?: "",
                record.vitals.oxygenSaturationPct?.avg?.toString() ?: "",
                record.vitals.bloodPressureMmHg?.systolic?.toString() ?: "",
                record.vitals.bloodPressureMmHg?.diastolic?.toString() ?: "",
                record.vitals.bloodPressureMmHg?.pulse?.toString() ?: "",
                record.bodyMeasurements.weightKg?.toString() ?: "",
                record.bodyMeasurements.bodyFatPct?.toString() ?: "",
                record.bodyMeasurements.leanBodyMassKg?.toString() ?: ""
            )
            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains(";")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
