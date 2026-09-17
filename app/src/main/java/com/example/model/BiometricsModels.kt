package com.example.model

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Root export payload matching the exact specified schema:
 * {
 *   "exportVersion": "1.0",
 *   "sourceApp": "HealthConnect",
 *   "timezone": "Asia/Kolkata",
 *   "exportedAt": "2026-09-06T06:30:00Z",
 *   "dailyRecords": [ ... ]
 * }
 */
data class BiometricsExportPayload(
    val exportVersion: String = "1.0",
    val sourceApp: String = "HealthConnect",
    val timezone: String = "Asia/Kolkata",
    val exportedAt: String = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
    val dailyRecords: List<DailyRecord> = emptyList()
)

data class DailyRecord(
    val date: String, // "YYYY-MM-DD"
    val sources: List<String> = emptyList(),
    val activity: ActivityMetrics = ActivityMetrics(),
    val sleep: SleepMetrics = SleepMetrics(),
    val vitals: VitalsMetrics = VitalsMetrics(),
    val bodyMeasurements: BodyMeasurements = BodyMeasurements()
)

data class ActivityMetrics(
    val steps: Long = 0,
    val distanceMeters: Double = 0.0,
    val totalCaloriesKcal: Double = 0.0,
    val activeCaloriesKcal: Double = 0.0,
    val activeDurationMinutes: Long = 0,
    val vo2MaxMlKgMin: ValueAvg? = null
)

data class SleepMetrics(
    val totalSleepMinutes: Long = 0,
    val lightSleepMinutes: Long = 0,
    val deepSleepMinutes: Long = 0,
    val remSleepMinutes: Long = 0,
    val awakeMinutes: Long = 0,
    val sleepEfficiencyScore: Int = 0
)

data class VitalsMetrics(
    val restingHeartRateBpm: ValueMinMaxAvg? = null,
    val heartRateVariabilityMs: ValueAvg? = null,
    val oxygenSaturationPct: ValueAvg? = null,
    val bloodPressureMmHg: BloodPressure? = null
)

data class BodyMeasurements(
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val leanBodyMassKg: Double? = null
)

data class ValueAvg(
    val avg: Double
)

data class ValueMinMaxAvg(
    val min: Double,
    val max: Double,
    val avg: Double
)

data class BloodPressure(
    val systolic: Double,
    val diastolic: Double,
    val pulse: Double
)
