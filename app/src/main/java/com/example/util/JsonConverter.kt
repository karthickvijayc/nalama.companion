package com.example.util

import com.example.model.BiometricsExportPayload
import com.example.model.DailyRecord
import org.json.JSONArray
import org.json.JSONObject

object JsonConverter {

    /**
     * Converts a [BiometricsExportPayload] into a formatted JSON string matching the specified schema.
     */
    fun toJsonString(payload: BiometricsExportPayload, indentSpaces: Int = 2): String {
        val root = JSONObject()
        root.put("exportVersion", payload.exportVersion)
        root.put("sourceApp", payload.sourceApp)
        root.put("timezone", payload.timezone)
        root.put("exportedAt", payload.exportedAt)

        val recordsArray = JSONArray()
        for (record in payload.dailyRecords) {
            recordsArray.put(recordToJson(record))
        }
        root.put("dailyRecords", recordsArray)

        return if (indentSpaces > 0) root.toString(indentSpaces) else root.toString()
    }

    private fun recordToJson(record: DailyRecord): JSONObject {
        val recordObj = JSONObject()
        recordObj.put("date", record.date)

        val sourcesArray = JSONArray()
        record.sources.forEach { sourcesArray.put(it) }
        recordObj.put("sources", sourcesArray)

        // Activity
        val actObj = JSONObject()
        actObj.put("steps", record.activity.steps)
        actObj.put("distanceMeters", record.activity.distanceMeters)
        actObj.put("totalCaloriesKcal", record.activity.totalCaloriesKcal)
        actObj.put("activeCaloriesKcal", record.activity.activeCaloriesKcal)
        actObj.put("activeDurationMinutes", record.activity.activeDurationMinutes)
        record.activity.vo2MaxMlKgMin?.let {
            val vo2 = JSONObject()
            vo2.put("avg", it.avg)
            actObj.put("vo2MaxMlKgMin", vo2)
        }
        recordObj.put("activity", actObj)

        // Sleep
        val sleepObj = JSONObject()
        sleepObj.put("totalSleepMinutes", record.sleep.totalSleepMinutes)
        sleepObj.put("lightSleepMinutes", record.sleep.lightSleepMinutes)
        sleepObj.put("deepSleepMinutes", record.sleep.deepSleepMinutes)
        sleepObj.put("remSleepMinutes", record.sleep.remSleepMinutes)
        sleepObj.put("awakeMinutes", record.sleep.awakeMinutes)
        sleepObj.put("sleepEfficiencyScore", record.sleep.sleepEfficiencyScore)
        recordObj.put("sleep", sleepObj)

        // Vitals
        val vitalsObj = JSONObject()
        record.vitals.restingHeartRateBpm?.let {
            val rhr = JSONObject()
            rhr.put("min", it.min)
            rhr.put("max", it.max)
            rhr.put("avg", it.avg)
            vitalsObj.put("restingHeartRateBpm", rhr)
        }
        record.vitals.heartRateVariabilityMs?.let {
            val hrv = JSONObject()
            hrv.put("avg", it.avg)
            vitalsObj.put("heartRateVariabilityMs", hrv)
        }
        record.vitals.oxygenSaturationPct?.let {
            val spo2 = JSONObject()
            spo2.put("avg", it.avg)
            vitalsObj.put("oxygenSaturationPct", spo2)
        }
        record.vitals.bloodPressureMmHg?.let {
            val bp = JSONObject()
            bp.put("systolic", it.systolic)
            bp.put("diastolic", it.diastolic)
            bp.put("pulse", it.pulse)
            vitalsObj.put("bloodPressureMmHg", bp)
        }
        recordObj.put("vitals", vitalsObj)

        // Body Measurements
        val bodyObj = JSONObject()
        record.bodyMeasurements.weightKg?.let { bodyObj.put("weightKg", it) }
        record.bodyMeasurements.bodyFatPct?.let { bodyObj.put("bodyFatPct", it) }
        record.bodyMeasurements.leanBodyMassKg?.let { bodyObj.put("leanBodyMassKg", it) }
        recordObj.put("bodyMeasurements", bodyObj)

        return recordObj
    }
}
