package com.example.drive

import android.content.Context
import com.example.model.ActivityMetrics
import com.example.model.BloodPressure
import com.example.model.BodyMeasurements
import com.example.model.DailyRecord
import com.example.model.SleepMetrics
import com.example.model.ValueAvg
import com.example.model.ValueMinMaxAvg
import com.example.model.VitalsMetrics
import com.example.model.WorkoutItem
import com.example.util.CsvConverter
import com.example.util.WorkoutCsvConverter
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

data class CachedFileInfo(
    val fileName: String,
    val fileId: String,
    val lastRemoteMd5: String,
    val lastLocalMd5: String,
    val lastSyncTimestamp: Long,
    val spreadsheetId: String? = null,
    val rowCount: Int = 0
)

data class BiometricsMergeResult(
    val activeCsv: String,
    val archiveCsvByYear: Map<Int, String>,
    val updatedCount: Int,
    val newCount: Int,
    val archivedCount: Int,
    val totalActiveRecords: Int
)

data class WorkoutsMergeResult(
    val activeCsv: String,
    val archiveCsvByYear: Map<Int, String>,
    val updatedCount: Int,
    val newCount: Int,
    val archivedCount: Int,
    val totalActiveRecords: Int
)

data class CacheSummary(
    val fileCount: Int,
    val folderCount: Int,
    val totalSizeBytes: Long,
    val fileNames: List<String>
)

class DriveSyncCacheManager(private val context: Context) {

    private val cacheDir: File by lazy {
        File(context.filesDir, "drive_sync_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val metaFile: File by lazy {
        File(cacheDir, "metadata.json")
    }

    private val fileMetadataMap = mutableMapOf<String, CachedFileInfo>()
    private val folderIdMap = mutableMapOf<String, String>()

    init {
        loadMetadata()
    }

    @Synchronized
    private fun loadMetadata() {
        try {
            if (metaFile.exists()) {
                val jsonStr = metaFile.readText()
                val root = JSONObject(jsonStr)

                val filesObj = root.optJSONObject("files") ?: JSONObject()
                filesObj.keys().forEach { key ->
                    val fObj = filesObj.getJSONObject(key)
                    fileMetadataMap[key] = CachedFileInfo(
                        fileName = fObj.optString("fileName", key),
                        fileId = fObj.optString("fileId", ""),
                        lastRemoteMd5 = fObj.optString("lastRemoteMd5", ""),
                        lastLocalMd5 = fObj.optString("lastLocalMd5", ""),
                        lastSyncTimestamp = fObj.optLong("lastSyncTimestamp", 0L),
                        spreadsheetId = if (fObj.has("spreadsheetId")) fObj.optString("spreadsheetId") else null,
                        rowCount = fObj.optInt("rowCount", 0)
                    )
                }

                val foldersObj = root.optJSONObject("folders") ?: JSONObject()
                foldersObj.keys().forEach { key ->
                    folderIdMap[key] = foldersObj.getString(key)
                }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun persistMetadata() {
        try {
            val root = JSONObject()
            val filesObj = JSONObject()
            fileMetadataMap.forEach { (key, info) ->
                val fObj = JSONObject().apply {
                    put("fileName", info.fileName)
                    put("fileId", info.fileId)
                    put("lastRemoteMd5", info.lastRemoteMd5)
                    put("lastLocalMd5", info.lastLocalMd5)
                    put("lastSyncTimestamp", info.lastSyncTimestamp)
                    if (info.spreadsheetId != null) put("spreadsheetId", info.spreadsheetId)
                    put("rowCount", info.rowCount)
                }
                filesObj.put(key, fObj)
            }
            root.put("files", filesObj)

            val foldersObj = JSONObject()
            folderIdMap.forEach { (path, id) ->
                foldersObj.put(path, id)
            }
            root.put("folders", foldersObj)

            metaFile.writeText(root.toString(2))
        } catch (_: Exception) {}
    }

    fun getLocalFile(fileName: String): File {
        return File(cacheDir, fileName)
    }

    fun getCachedFileInfo(fileName: String): CachedFileInfo? = fileMetadataMap[fileName]

    fun removeCachedFileInfo(fileName: String) {
        fileMetadataMap.remove(fileName)
        persistMetadata()
    }

    fun getCachedFolderId(folderPath: String): String? = folderIdMap[folderPath]

    fun setCachedFolderId(folderPath: String, id: String) {
        folderIdMap[folderPath] = id
        persistMetadata()
    }

    fun removeCachedFolderId(folderPath: String) {
        folderIdMap.remove(folderPath)
        persistMetadata()
    }

    @Synchronized
    fun clearAllCache(): Int {
        var count = 0
        try {
            fileMetadataMap.clear()
            folderIdMap.clear()
            persistMetadata()
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != "metadata.json") {
                    if (file.delete()) count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun getCacheSummary(): CacheSummary {
        val files = cacheDir.listFiles() ?: emptyArray()
        val totalBytes = files.sumOf { it.length() }
        val names = fileMetadataMap.keys.toList()
        return CacheSummary(
            fileCount = fileMetadataMap.size,
            folderCount = folderIdMap.size,
            totalSizeBytes = totalBytes,
            fileNames = names
        )
    }

    fun updateCachedFile(
        fileName: String,
        fileId: String,
        remoteMd5: String,
        localMd5: String,
        rowCount: Int,
        spreadsheetId: String? = null
    ) {
        val existing = fileMetadataMap[fileName]
        val updated = CachedFileInfo(
            fileName = fileName,
            fileId = fileId,
            lastRemoteMd5 = remoteMd5,
            lastLocalMd5 = localMd5,
            lastSyncTimestamp = System.currentTimeMillis(),
            spreadsheetId = spreadsheetId ?: existing?.spreadsheetId,
            rowCount = rowCount
        )
        fileMetadataMap[fileName] = updated
        persistMetadata()
    }

    fun readLocalFileText(fileName: String): String? {
        val file = getLocalFile(fileName)
        return if (file.exists()) file.readText() else null
    }

    fun writeLocalFileText(fileName: String, content: String): String {
        val file = getLocalFile(fileName)
        file.writeText(content)
        return computeMd5(content)
    }

    fun computeMd5(content: String): String {
        return computeMd5(content.toByteArray(Charsets.UTF_8))
    }

    fun computeMd5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Merges incoming Health Connect daily biometrics records with the local baseline:
     * - In-place updates for existing dates
     * - Appends new dates
     * - Enforces 180-day active retention window
     * - Partitions older days into yearly archive CSVs (e.g. biometrics_daily_2025.csv)
     */
    fun mergeBiometricsCsv(
        existingCsv: String?,
        incomingRecords: List<DailyRecord>,
        archiveMaxDays: Int = 180
    ): BiometricsMergeResult {
        val recordMap = mutableMapOf<String, DailyRecord>()

        // 1. Parse existing rows if present
        if (!existingCsv.isNullOrBlank()) {
            val lines = existingCsv.lines().filter { it.isNotBlank() }
            if (lines.size > 1) {
                // Parse existing rows back into DailyRecord shells for date deduplication
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val cols = line.split(",")
                    if (cols.isNotEmpty()) {
                        val date = cols[0].trim()
                        if (date.isNotBlank()) {
                            // Placeholder record preserving date
                            recordMap[date] = parseCsvLineToDailyRecord(cols)
                        }
                    }
                }
            }
        }

        var updatedCount = 0
        var newCount = 0

        // 2. Apply incoming records with in-place intra-day upsert
        for (rec in incomingRecords) {
            if (recordMap.containsKey(rec.date)) {
                recordMap[rec.date] = rec
                updatedCount++
            } else {
                recordMap[rec.date] = rec
                newCount++
            }
        }

        // 3. Sort all records chronologically
        val sortedRecords = recordMap.values.sortedBy { it.date }

        // 4. Split by retention cutoff
        val cutoffDate = LocalDate.now().minusDays(archiveMaxDays.toLong()).toString()
        val activeRecords = sortedRecords.filter { it.date >= cutoffDate }
        val archivedRecords = sortedRecords.filter { it.date < cutoffDate }

        // 5. Generate active CSV
        val activeCsv = CsvConverter.toCsvString(activeRecords, includeHeader = true)

        // 6. Group archived records by year
        val archiveByYear = mutableMapOf<Int, String>()
        archivedRecords.groupBy {
            try {
                LocalDate.parse(it.date).year
            } catch (_: Exception) {
                LocalDate.now().year
            }
        }.forEach { (year, recs) ->
            archiveByYear[year] = CsvConverter.toCsvString(recs, includeHeader = true)
        }

        return BiometricsMergeResult(
            activeCsv = activeCsv,
            archiveCsvByYear = archiveByYear,
            updatedCount = updatedCount,
            newCount = newCount,
            archivedCount = archivedRecords.size,
            totalActiveRecords = activeRecords.size
        )
    }

    /**
     * Merges incoming Hevy workouts with the local baseline.
     */
    fun mergeWorkoutsCsv(
        existingCsv: String?,
        incomingWorkouts: List<WorkoutItem>,
        archiveMaxDays: Int = 180
    ): WorkoutsMergeResult {
        val workoutMap = mutableMapOf<String, WorkoutItem>()
        val workoutExercisesMap = mutableMapOf<String, MutableMap<String, MutableList<ExerciseSet>>>()
        val workoutExerciseMetaMap = mutableMapOf<String, MutableMap<String, Pair<String?, String?>>>()
        val workoutExerciseNotesMap = mutableMapOf<String, MutableMap<String, String?>>()

        // 1. Parse existing rows if present
        if (!existingCsv.isNullOrBlank()) {
            val lines = existingCsv.lines().filter { it.isNotBlank() }
            if (lines.size > 1) {
                for (i in 1 until lines.size) {
                    val cols = parseCsvLine(lines[i])
                    if (cols.isNotEmpty()) {
                        val workoutId = cols.getOrNull(0)?.trim() ?: ""
                        val date = cols.getOrNull(1)?.trim() ?: ""
                        val title = cols.getOrNull(2)?.trim() ?: ""
                        val startTime = cols.getOrNull(3)?.trim() ?: ""
                        val key = workoutId.ifBlank { "${date}_${startTime}_${title}" }
                        if (key.isBlank()) continue

                        if (!workoutMap.containsKey(key)) {
                            workoutMap[key] = WorkoutItem(
                                workoutId = workoutId,
                                date = date,
                                title = title,
                                startTime = startTime,
                                endTime = cols.getOrNull(4)?.trim()?.ifBlank { null },
                                durationMinutes = cols.getOrNull(5)?.trim()?.toIntOrNull() ?: 0,
                                totalVolumeKg = cols.getOrNull(6)?.trim()?.toIntOrNull() ?: 0,
                                totalSets = cols.getOrNull(7)?.trim()?.toIntOrNull() ?: 0,
                                avgHeartRateBpm = cols.getOrNull(8)?.trim()?.toIntOrNull(),
                                maxHeartRateBpm = cols.getOrNull(9)?.trim()?.toIntOrNull(),
                                caloriesActualHr = cols.getOrNull(10)?.trim()?.toIntOrNull(),
                                notes = cols.getOrNull(19)?.trim()?.ifBlank { null }
                            )
                            workoutExercisesMap[key] = mutableMapOf()
                            workoutExerciseMetaMap[key] = mutableMapOf()
                            workoutExerciseNotesMap[key] = mutableMapOf()
                        }

                        val exName = cols.getOrNull(11)?.trim() ?: ""
                        if (exName.isNotBlank()) {
                            val targetMuscle = cols.getOrNull(12)?.trim()?.ifBlank { null }
                            val equipment = cols.getOrNull(13)?.trim()?.ifBlank { null }
                            val setNum = cols.getOrNull(14)?.trim()?.toIntOrNull() ?: 1
                            val setType = cols.getOrNull(15)?.trim()?.ifBlank { "normal" } ?: "normal"
                            val weightKg = cols.getOrNull(16)?.trim()?.toDoubleOrNull() ?: 0.0
                            val reps = cols.getOrNull(17)?.trim()?.toIntOrNull() ?: 0
                            val rpe = cols.getOrNull(18)?.trim()?.toDoubleOrNull()
                            val notes = cols.getOrNull(19)?.trim()?.ifBlank { null }

                            val exercises = workoutExercisesMap[key]!!
                            val setList = exercises.getOrPut(exName) { mutableListOf() }
                            setList.add(
                                ExerciseSet(
                                    setNumber = setNum,
                                    setType = setType,
                                    weightKg = weightKg,
                                    reps = reps,
                                    rpe = rpe
                                )
                            )
                            workoutExerciseMetaMap[key]?.put(exName, Pair(targetMuscle, equipment))
                            if (notes != null) workoutExerciseNotesMap[key]?.put(exName, notes)
                        }
                    }
                }

                // Attach reconstructed exercises to each workout
                for ((key, workout) in workoutMap) {
                    val exercisesForWorkout = workoutExercisesMap[key] ?: emptyMap()
                    val exerciseList = exercisesForWorkout.map { (exName, sets) ->
                        val (muscle, equip) = workoutExerciseMetaMap[key]?.get(exName) ?: Pair(null, null)
                        val exNote = workoutExerciseNotesMap[key]?.get(exName)
                        WorkoutExercise(
                            exerciseName = exName,
                            targetMuscleGroup = muscle,
                            equipment = equip,
                            sets = sets,
                            notes = exNote
                        )
                    }
                    workoutMap[key] = workout.copy(exercises = exerciseList)
                }
            }
        }

        var updatedCount = 0
        var newCount = 0

        for (workout in incomingWorkouts) {
            val key = workout.workoutId.ifBlank { "${workout.date}_${workout.startTime}_${workout.title}" }
            if (workoutMap.containsKey(key)) {
                workoutMap[key] = workout
                updatedCount++
            } else {
                workoutMap[key] = workout
                newCount++
            }
        }

        val sortedWorkouts = workoutMap.values.sortedBy { "${it.date} ${it.startTime}" }

        val (activeWorkouts, archivedWorkouts) = if (archiveMaxDays <= 0) {
            Pair(sortedWorkouts, emptyList<WorkoutItem>())
        } else {
            val cutoffDate = LocalDate.now().minusDays(archiveMaxDays.toLong()).toString()
            Pair(
                sortedWorkouts.filter { it.date >= cutoffDate },
                sortedWorkouts.filter { it.date < cutoffDate }
            )
        }

        val activeCsv = WorkoutCsvConverter.toCsvString(activeWorkouts, includeHeader = true)
        val archiveByYear = mutableMapOf<Int, String>()
        archivedWorkouts.groupBy {
            try {
                LocalDate.parse(it.date).year
            } catch (_: Exception) {
                LocalDate.now().year
            }
        }.forEach { (year, wks) ->
            archiveByYear[year] = WorkoutCsvConverter.toCsvString(wks, includeHeader = true)
        }

        return WorkoutsMergeResult(
            activeCsv = activeCsv,
            archiveCsvByYear = archiveByYear,
            updatedCount = updatedCount,
            newCount = newCount,
            archivedCount = archivedWorkouts.size,
            totalActiveRecords = activeWorkouts.size
        )
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        sb.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseCsvLineToDailyRecord(cols: List<String>): DailyRecord {
        val date = cols.getOrNull(0)?.trim() ?: LocalDate.now().toString()
        val sources = cols.getOrNull(1)?.trim()?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val steps = cols.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
        val distance = cols.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0
        val totalCalories = cols.getOrNull(4)?.trim()?.toDoubleOrNull() ?: 0.0
        val activeCalories = cols.getOrNull(5)?.trim()?.toDoubleOrNull() ?: 0.0
        val activeDuration = cols.getOrNull(6)?.trim()?.toLongOrNull() ?: 0L
        val vo2Max = cols.getOrNull(7)?.trim()?.toDoubleOrNull()?.let { ValueAvg(it) }

        val totalSleep = cols.getOrNull(8)?.trim()?.toLongOrNull() ?: 0L
        val lightSleep = cols.getOrNull(9)?.trim()?.toLongOrNull() ?: 0L
        val deepSleep = cols.getOrNull(10)?.trim()?.toLongOrNull() ?: 0L
        val remSleep = cols.getOrNull(11)?.trim()?.toLongOrNull() ?: 0L
        val awake = cols.getOrNull(12)?.trim()?.toLongOrNull() ?: 0L
        val sleepScore = cols.getOrNull(13)?.trim()?.toIntOrNull() ?: 0

        val rhrMin = cols.getOrNull(14)?.trim()?.toDoubleOrNull()
        val rhrMax = cols.getOrNull(15)?.trim()?.toDoubleOrNull()
        val rhrAvg = cols.getOrNull(16)?.trim()?.toDoubleOrNull()
        val restingHr = if (rhrAvg != null || rhrMin != null || rhrMax != null) {
            ValueMinMaxAvg(min = rhrMin ?: 0.0, max = rhrMax ?: 0.0, avg = rhrAvg ?: 0.0)
        } else null

        val hrvAvg = cols.getOrNull(17)?.trim()?.toDoubleOrNull()?.let { ValueAvg(it) }
        val spo2Avg = cols.getOrNull(18)?.trim()?.toDoubleOrNull()?.let { ValueAvg(it) }

        val sys = cols.getOrNull(19)?.trim()?.toDoubleOrNull()
        val dia = cols.getOrNull(20)?.trim()?.toDoubleOrNull()
        val pulse = cols.getOrNull(21)?.trim()?.toDoubleOrNull()
        val bp = if (sys != null && dia != null) {
            BloodPressure(systolic = sys, diastolic = dia, pulse = pulse ?: 0.0)
        } else null

        val weight = cols.getOrNull(22)?.trim()?.toDoubleOrNull()
        val bodyFat = cols.getOrNull(23)?.trim()?.toDoubleOrNull()
        val leanMass = cols.getOrNull(24)?.trim()?.toDoubleOrNull()

        return DailyRecord(
            date = date,
            sources = sources,
            activity = ActivityMetrics(
                steps = steps,
                distanceMeters = distance,
                totalCaloriesKcal = totalCalories,
                activeCaloriesKcal = activeCalories,
                activeDurationMinutes = activeDuration,
                vo2MaxMlKgMin = vo2Max
            ),
            sleep = SleepMetrics(
                totalSleepMinutes = totalSleep,
                lightSleepMinutes = lightSleep,
                deepSleepMinutes = deepSleep,
                remSleepMinutes = remSleep,
                awakeMinutes = awake,
                sleepEfficiencyScore = sleepScore
            ),
            vitals = VitalsMetrics(
                restingHeartRateBpm = restingHr,
                heartRateVariabilityMs = hrvAvg,
                oxygenSaturationPct = spo2Avg,
                bloodPressureMmHg = bp
            ),
            bodyMeasurements = BodyMeasurements(
                weightKg = weight,
                bodyFatPct = bodyFat,
                leanBodyMassKg = leanMass
            )
        )
    }
}
