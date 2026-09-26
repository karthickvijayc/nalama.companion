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
            val cleanSources = record.sources
                .map { src ->
                    val clean = src.replace("\"", "").trim()
                    if (clean.startsWith("com.android.healthconnect")) "com.android.healthconnect" else clean
                }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(";")

            val hasSleep = record.sleep.totalSleepMinutes > 0

            listOf(
                record.date,
                cleanSources,
                record.activity.steps,
                record.activity.distanceMeters,
                record.activity.totalCaloriesKcal,
                record.activity.activeCaloriesKcal,
                record.activity.activeDurationMinutes,
                record.activity.vo2MaxMlKgMin?.avg ?: "",
                if (hasSleep) record.sleep.totalSleepMinutes else "",
                if (hasSleep) record.sleep.lightSleepMinutes else "",
                if (hasSleep) record.sleep.deepSleepMinutes else "",
                if (hasSleep) record.sleep.remSleepMinutes else "",
                if (hasSleep) record.sleep.awakeMinutes else "",
                if (hasSleep) record.sleep.sleepEfficiencyScore else "",
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
            val cleanSources = record.sources
                .map { src ->
                    val clean = src.replace("\"", "").trim()
                    if (clean.startsWith("com.android.healthconnect")) "com.android.healthconnect" else clean
                }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(";")

            val hasSleep = record.sleep.totalSleepMinutes > 0

            val row = listOf(
                escapeCsv(record.date),
                escapeCsv(cleanSources),
                record.activity.steps.toString(),
                record.activity.distanceMeters.toString(),
                record.activity.totalCaloriesKcal.toString(),
                record.activity.activeCaloriesKcal.toString(),
                record.activity.activeDurationMinutes.toString(),
                record.activity.vo2MaxMlKgMin?.avg?.toString() ?: "",
                if (hasSleep) record.sleep.totalSleepMinutes.toString() else "",
                if (hasSleep) record.sleep.lightSleepMinutes.toString() else "",
                if (hasSleep) record.sleep.deepSleepMinutes.toString() else "",
                if (hasSleep) record.sleep.remSleepMinutes.toString() else "",
                if (hasSleep) record.sleep.awakeMinutes.toString() else "",
                if (hasSleep) record.sleep.sleepEfficiencyScore.toString() else "",
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

        val trimmed = sb.toString().trimEnd()
        return if (trimmed.isNotEmpty()) "$trimmed\n" else ""
    }

    private fun escapeCsv(value: String): String {
        // Strip any rogue pre-existing quotation marks to prevent exponential quote explosion
        val clean = value.replace("\"", "").replace("\r\n", " ").replace("\n", " ").trim()
        // Standard RFC-4180 escaping: wrap in quotes ONLY if the value contains a comma or newline
        return if (clean.contains(",")) {
            "\"$clean\""
        } else {
            clean
        }
    }
}
