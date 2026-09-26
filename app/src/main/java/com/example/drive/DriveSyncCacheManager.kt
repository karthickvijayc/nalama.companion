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
import com.example.model.ExerciseSet
import com.example.model.WorkoutExercise
import com.example.model.WorkoutItem
import com.example.util.CsvConverter
import com.example.util.WorkoutCsvConverter
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val archivedRecordsByYear: Map<Int, List<DailyRecord>> = emptyMap(),
    val updatedCount: Int,
    val newCount: Int,
    val archivedCount: Int,
    val totalActiveRecords: Int
)

data class WorkoutsMergeResult(
    val activeCsv: String,
    val archiveCsvByYear: Map<Int, String>,
    val archivedWorkoutsByYear: Map<Int, List<WorkoutItem>> = emptyMap(),
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
            val records = parseCsvRecords(existingCsv)
            if (records.size > 1) {
                val rawDates = (1 until records.size).map { records[it].getOrNull(0)?.trim() ?: "" }
                // Parse existing rows back into DailyRecord shells for date deduplication
                for (i in 1 until records.size) {
                    val cols = records[i]
                    if (cols.isNotEmpty()) {
                        var date = cols.getOrNull(0)?.trim() ?: ""
                        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                            continue
                        }

                        // Auto-repair sequential interpolation if row i is bracketed by d-1 and d+1
                        // (e.g. 2026-10-10 between 03-09 and 03-11, or 2026-03-30 between 07-29 and 07-31)
                        val prevDateStr = rawDates.getOrNull(i - 2)
                        val nextDateStr = rawDates.getOrNull(i)
                        if (!prevDateStr.isNullOrBlank() && !nextDateStr.isNullOrBlank() &&
                            prevDateStr.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
                            nextDateStr.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                            try {
                                val prevD = LocalDate.parse(prevDateStr)
                                val nextD = LocalDate.parse(nextDateStr)
                                if (prevD.plusDays(2) == nextD) {
                                    val expectedD = prevD.plusDays(1).toString()
                                    if (date != expectedD) {
                                        AppLogger.w("SYNC", "Auto-repaired corrupted date in CSV row $i: '$date' -> '$expectedD' (bracketed by $prevDateStr and $nextDateStr)")
                                        date = expectedD
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // Reject non-bracketed future dates beyond tomorrow
                        val todayStr = LocalDate.now().plusDays(1).toString()
                        if (date > todayStr) {
                            AppLogger.w("SYNC", "Discarded future biometrics row with date: $date")
                            continue
                        }

                        val record = parseCsvLineToDailyRecord(cols).copy(date = date)
                        if (hasDailyData(record)) {
                            recordMap[date] = record
                        }
                    }
                }
            }
        }

        var updatedCount = 0
        var newCount = 0

        // 2. Apply incoming records with in-place intra-day upsert
        for (rec in incomingRecords) {
            if (hasDailyData(rec)) {
                if (recordMap.containsKey(rec.date)) {
                    recordMap[rec.date] = rec
                    updatedCount++
                } else {
                    recordMap[rec.date] = rec
                    newCount++
                }
            }
        }

        // 3. Sort all records chronologically
        val sortedRecords = recordMap.values.sortedBy { it.date }

        // 4. Split by retention cutoff (if archiveMaxDays <= 0, retain all in active partition)
        val (activeRecords, archivedRecords) = if (archiveMaxDays <= 0) {
            Pair(sortedRecords, emptyList<DailyRecord>())
        } else {
            val cutoffDate = LocalDate.now().minusDays(archiveMaxDays.toLong()).toString()
            Pair(
                sortedRecords.filter { it.date >= cutoffDate },
                sortedRecords.filter { it.date < cutoffDate }
            )
        }

        // 5. Generate active CSV
        val activeCsv = CsvConverter.toCsvString(activeRecords, includeHeader = true)

        // 6. Group archived records by year
        val archiveByYear = mutableMapOf<Int, String>()
        val archivedRecordsByYear = mutableMapOf<Int, List<DailyRecord>>()
        archivedRecords.groupBy {
            try {
                LocalDate.parse(it.date).year
            } catch (_: Exception) {
                LocalDate.now().year
            }
        }.forEach { (year, recs) ->
            archiveByYear[year] = CsvConverter.toCsvString(recs, includeHeader = true)
            archivedRecordsByYear[year] = recs
        }

        return BiometricsMergeResult(
            activeCsv = activeCsv,
            archiveCsvByYear = archiveByYear,
            archivedRecordsByYear = archivedRecordsByYear,
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
            val records = parseCsvRecords(existingCsv)
            if (records.size > 1) {
                for (i in 1 until records.size) {
                    val cols = records[i]
                    if (cols.isNotEmpty()) {
                        val workoutId = cols.getOrNull(0)?.trim() ?: ""
                        val date = cols.getOrNull(1)?.trim() ?: ""
                        val title = cols.getOrNull(2)?.trim() ?: ""
                        val startTime = cols.getOrNull(3)?.trim() ?: ""

                        // SANITY CHECK: A valid workout row MUST have a valid date in YYYY-MM-DD format!
                        // This strictly purges corrupted ghost rows (like orphaned multiline notes).
                        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                            continue
                        }

                        val key = workoutId.ifBlank { "${date}_${startTime}_${title}" }
                        if (key.isBlank()) continue

                        if (!workoutMap.containsKey(key)) {
                            val durationMin = cols.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
                            val parsedCalories = cols.getOrNull(10)?.trim()?.toIntOrNull()
                            val initialCalories = parsedCalories ?: if (durationMin > 0) (durationMin * 6.0).toInt().coerceAtLeast(30) else null

                            workoutMap[key] = WorkoutItem(
                                workoutId = workoutId,
                                date = date,
                                title = title,
                                startTime = startTime,
                                endTime = cols.getOrNull(4)?.trim()?.ifBlank { null },
                                durationMinutes = durationMin,
                                totalVolumeKg = cols.getOrNull(6)?.trim()?.toIntOrNull() ?: 0,
                                totalSets = cols.getOrNull(7)?.trim()?.toIntOrNull() ?: 0,
                                avgHeartRateBpm = cols.getOrNull(8)?.trim()?.toIntOrNull(),
                                maxHeartRateBpm = cols.getOrNull(9)?.trim()?.toIntOrNull(),
                                caloriesActualHr = initialCalories,
                                notes = null
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

                // Attach reconstructed exercises to each workout (auto-healing missing metadata)
                val hevyManager = com.example.health.HevySyncManager()
                for ((key, workout) in workoutMap) {
                    val exercisesForWorkout = workoutExercisesMap[key] ?: emptyMap()
                    val exerciseList = exercisesForWorkout.map { (exName, sets) ->
                        val (rawMuscle, rawEquip) = workoutExerciseMetaMap[key]?.get(exName) ?: Pair(null, null)
                        val exNote = workoutExerciseNotesMap[key]?.get(exName)
                        val inferred = hevyManager.inferMuscleGroupAndEquipment(exName)
                        val finalMuscle = if (!rawMuscle.isNullOrBlank()) rawMuscle else inferred.first
                        val finalEquip = if (!rawEquip.isNullOrBlank()) rawEquip else inferred.second
                        WorkoutExercise(
                            exerciseName = exName,
                            targetMuscleGroup = finalMuscle,
                            equipment = finalEquip,
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
        val archivedWorkoutsByYear = mutableMapOf<Int, List<WorkoutItem>>()
        archivedWorkouts.groupBy {
            try {
                LocalDate.parse(it.date).year
            } catch (_: Exception) {
                LocalDate.now().year
            }
        }.forEach { (year, wks) ->
            archiveByYear[year] = WorkoutCsvConverter.toCsvString(wks, includeHeader = true)
            archivedWorkoutsByYear[year] = wks
        }

        return WorkoutsMergeResult(
            activeCsv = activeCsv,
            archiveCsvByYear = archiveByYear,
            archivedWorkoutsByYear = archivedWorkoutsByYear,
            updatedCount = updatedCount,
            newCount = newCount,
            archivedCount = archivedWorkouts.size,
            totalActiveRecords = activeWorkouts.size
        )
    }

    /**
     * Quote-aware CSV record parser that handles multiline fields and standard CSV escaping.
     */
    fun parseCsvRecords(csvText: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentCols = mutableListOf<String>()
        val currentField = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        val len = csvText.length

        while (i < len) {
            val c = csvText[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < len && csvText[i + 1] == '\"') {
                        currentField.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    currentCols.add(currentField.toString())
                    currentField.setLength(0)
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && csvText[i + 1] == '\n') {
                        i++
                    }
                    currentCols.add(currentField.toString())
                    currentField.setLength(0)
                    if (currentCols.any { it.isNotBlank() }) {
                        records.add(currentCols.toList())
                    }
                    currentCols.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentCols.isNotEmpty()) {
            currentCols.add(currentField.toString())
            if (currentCols.any { it.isNotBlank() }) {
                records.add(currentCols.toList())
            }
        }
        return records
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

    fun hasDailyData(record: DailyRecord): Boolean {
        return record.sources.isNotEmpty() ||
                record.activity.steps > 0 ||
                record.activity.totalCaloriesKcal > 0.0 ||
                record.activity.activeCaloriesKcal > 0.0 ||
                record.activity.activeDurationMinutes > 0 ||
                record.sleep.totalSleepMinutes > 0 ||
                record.vitals.restingHeartRateBpm != null ||
                record.vitals.heartRateVariabilityMs != null ||
                record.vitals.oxygenSaturationPct != null ||
                record.vitals.bloodPressureMmHg != null ||
                record.bodyMeasurements.weightKg != null
    }

    private fun parseCsvLineToDailyRecord(cols: List<String>): DailyRecord {
        val date = cols.getOrNull(0)?.trim() ?: LocalDate.now().toString()
        val rawSources = cols.getOrNull(1)?.trim() ?: ""
        val sources = rawSources
            .replace("\"", "")
            .split(";")
            .map { src ->
                val clean = src.trim()
                if (clean.startsWith("com.android.healthconnect")) "com.android.healthconnect" else clean
            }
            .filter { it.isNotBlank() }
            .distinct()

        val steps = cols.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
        var distance = cols.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0
        val minExpectedDistance = steps * 0.40
        if ((distance == 0.0 || distance < minExpectedDistance) && steps > 0) {
            distance = ((steps * 0.762) * 10).roundToInt() / 10.0
        }

        var totalCalories = cols.getOrNull(4)?.trim()?.toDoubleOrNull() ?: 0.0
        var activeCalories = cols.getOrNull(5)?.trim()?.toDoubleOrNull() ?: 0.0
        val minExpectedCalories = steps * 0.03
        if (totalCalories < minExpectedCalories && steps > 1500) {
            val est = ((steps * 0.045) * 10).roundToInt() / 10.0
            totalCalories = est
            if (activeCalories < minExpectedCalories) {
                activeCalories = est
            }
        } else {
            if (activeCalories == 0.0 && totalCalories > 0.0) {
                activeCalories = totalCalories
            } else if (totalCalories == 0.0 && activeCalories > 0.0) {
                totalCalories = activeCalories
            } else if (totalCalories == 0.0 && steps > 0) {
                val est = ((steps * 0.045) * 10).roundToInt() / 10.0
                totalCalories = est
                activeCalories = est
            }
        }

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
        var leanMass = cols.getOrNull(24)?.trim()?.toDoubleOrNull()
        if (leanMass == null && weight != null && bodyFat != null && bodyFat in 1.0..99.0) {
            leanMass = ((weight * (1.0 - (bodyFat / 100.0))) * 10).roundToInt() / 10.0
        }

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
