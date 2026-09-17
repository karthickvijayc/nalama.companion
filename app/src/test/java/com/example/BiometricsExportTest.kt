package com.example

import com.example.model.*
import com.example.util.CsvConverter
import com.example.util.JsonConverter
import org.json.JSONObject
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
    fun testJsonSerializationMatchesSchema() {
        val payload = createSamplePayload()
        val jsonStr = JsonConverter.toJsonString(payload)
        val json = JSONObject(jsonStr)

        assertEquals("1.0", json.getString("exportVersion"))
        assertEquals("HealthConnect", json.getString("sourceApp"))
        assertEquals("Asia/Kolkata", json.getString("timezone"))
        assertEquals("2026-09-06T06:30:00Z", json.getString("exportedAt"))

        val records = json.getJSONArray("dailyRecords")
        assertEquals(1, records.length())

        val rec = records.getJSONObject(0)
        assertEquals("2026-09-06", rec.getString("date"))

        val sources = rec.getJSONArray("sources")
        assertEquals(2, sources.length())
        assertEquals("com.sec.android.app.shealth", sources.getString(0))
        assertEquals("com.google.android.apps.fitness", sources.getString(1))

        // Activity
        val act = rec.getJSONObject("activity")
        assertEquals(8420L, act.getLong("steps"))
        assertEquals(6120.0, act.getDouble("distanceMeters"), 0.001)
        assertEquals(2240.0, act.getDouble("totalCaloriesKcal"), 0.001)
        assertEquals(480.0, act.getDouble("activeCaloriesKcal"), 0.001)
        assertEquals(48L, act.getLong("activeDurationMinutes"))
        assertEquals(42.5, act.getJSONObject("vo2MaxMlKgMin").getDouble("avg"), 0.001)

        // Sleep
        val sleep = rec.getJSONObject("sleep")
        assertEquals(450L, sleep.getLong("totalSleepMinutes"))
        assertEquals(240L, sleep.getLong("lightSleepMinutes"))
        assertEquals(95L, sleep.getLong("deepSleepMinutes"))
        assertEquals(85L, sleep.getLong("remSleepMinutes"))
        assertEquals(30L, sleep.getLong("awakeMinutes"))
        assertEquals(92, sleep.getInt("sleepEfficiencyScore"))

        // Vitals
        val vitals = rec.getJSONObject("vitals")
        val rhr = vitals.getJSONObject("restingHeartRateBpm")
        assertEquals(56.0, rhr.getDouble("min"), 0.001)
        assertEquals(68.0, rhr.getDouble("max"), 0.001)
        assertEquals(61.0, rhr.getDouble("avg"), 0.001)
        assertEquals(48.0, vitals.getJSONObject("heartRateVariabilityMs").getDouble("avg"), 0.001)
        assertEquals(98.5, vitals.getJSONObject("oxygenSaturationPct").getDouble("avg"), 0.001)
        val bp = vitals.getJSONObject("bloodPressureMmHg")
        assertEquals(118.0, bp.getDouble("systolic"), 0.001)
        assertEquals(76.0, bp.getDouble("diastolic"), 0.001)
        assertEquals(62.0, bp.getDouble("pulse"), 0.001)

        // Body Measurements
        val body = rec.getJSONObject("bodyMeasurements")
        assertEquals(72.4, body.getDouble("weightKg"), 0.001)
        assertEquals(18.2, body.getDouble("bodyFatPct"), 0.001)
        assertEquals(59.2, body.getDouble("leanBodyMassKg"), 0.001)
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
