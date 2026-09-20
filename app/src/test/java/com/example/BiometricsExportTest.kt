package com.example

import com.example.model.*
import com.example.util.CsvConverter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiometricsExportTest {

    private fun createSamplePayload(): BiometricsExportPayload {
        val record = DailyRecord(
            date = "2026-09-06",
            sources = listOf("com.sec.android.app.shealth", "com.google.android.apps.fitness"),
            activity = ActivityMetrics(
                steps = 8420,
                distanceMeters = 6120.0,
                totalCaloriesKcal = 2240.0,
                activeCaloriesKcal = 480.0,
                activeDurationMinutes = 48,
                vo2MaxMlKgMin = ValueAvg(avg = 42.5)
            ),
            sleep = SleepMetrics(
                totalSleepMinutes = 450,
                lightSleepMinutes = 240,
                deepSleepMinutes = 95,
                remSleepMinutes = 85,
                awakeMinutes = 30,
                sleepEfficiencyScore = 92
            ),
            vitals = VitalsMetrics(
                restingHeartRateBpm = ValueMinMaxAvg(min = 56.0, max = 68.0, avg = 61.0),
                heartRateVariabilityMs = ValueAvg(avg = 48.0),
                oxygenSaturationPct = ValueAvg(avg = 98.5),
                bloodPressureMmHg = BloodPressure(systolic = 118.0, diastolic = 76.0, pulse = 62.0)
            ),
            bodyMeasurements = BodyMeasurements(
                weightKg = 72.4,
                bodyFatPct = 18.2,
                leanBodyMassKg = 59.2
            )
        )

        return BiometricsExportPayload(
            exportVersion = "1.0",
            sourceApp = "HealthConnect",
            timezone = "Asia/Kolkata",
            exportedAt = "2026-09-06T06:30:00Z",
            dailyRecords = listOf(record)
        )
    }

    @Test
    fun testCsvSerialization() {
        val payload = createSamplePayload()
        val csvStr = CsvConverter.toCsvString(payload.dailyRecords, includeHeader = true)
        val lines = csvStr.trim().split("\n")

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("date,sources,steps,distance_meters"))
        assertTrue(lines[1].startsWith("2026-09-06,\"com.sec.android.app.shealth;com.google.android.apps.fitness\",8420"))
    }

    @Test
    fun testTargetFolderHierarchy() {
        assertEquals("nalama.family/imports/health_data", TargetFolder.HEALTH_DATA.folderPath)
        assertEquals("health_data", TargetFolder.HEALTH_DATA.subfolder)
        assertEquals("biometrics_daily", TargetFolder.HEALTH_DATA.defaultFileName)

        assertEquals("nalama.family/imports/gym_workouts", TargetFolder.GYM_WORKOUTS.folderPath)
        assertEquals("gym_workouts", TargetFolder.GYM_WORKOUTS.subfolder)
        assertEquals("hevy_workouts", TargetFolder.GYM_WORKOUTS.defaultFileName)
    }
}
