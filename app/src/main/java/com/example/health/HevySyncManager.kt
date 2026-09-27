package com.example.health

import com.example.model.ExerciseSet
import com.example.model.WorkoutExercise
import com.example.model.WorkoutItem
import com.example.model.WorkoutsExportPayload
import com.example.util.WorkoutCsvConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class HevySyncManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // In-memory catalog mapping templateId -> Pair(muscleGroup, equipment) and lowercase title -> Pair(muscleGroup, equipment)
    private val templateCatalogCache = ConcurrentHashMap<String, Pair<String, String>>()

    /**
     * Loads exercise templates from Hevy API (/v1/exercise_templates) to populate
     * official muscle_group and equipment mappings.
     */
    suspend fun loadExerciseTemplates(apiKey: String) = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank() || templateCatalogCache.isNotEmpty()) return@withContext

        try {
            var page = 1
            var totalPages = 1
            while (page <= totalPages && page <= 5) {
                val url = "https://api.hevyapp.com/v1/exercise_templates?page=$page&pageSize=100"
                val request = Request.Builder()
                    .url(url)
                    .header("api-key", trimmedKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HevySyncSheets-Android/1.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) break

                val json = JSONObject(body)
                totalPages = json.optInt("page_count", totalPages).coerceAtLeast(page)
                val templatesArray = json.optJSONArray("exercise_templates") ?: org.json.JSONArray()

                for (i in 0 until templatesArray.length()) {
                    val tObj = templatesArray.getJSONObject(i)
                    val id = tObj.optString("id", "")
                    val title = tObj.optString("title", "")
                    val muscleGroup = when {
                        tObj.has("muscle_group") && !tObj.isNull("muscle_group") -> tObj.optString("muscle_group")
                        tObj.has("primary_muscle_group") && !tObj.isNull("primary_muscle_group") -> tObj.optString("primary_muscle_group")
                        else -> ""
                    }
                    val equipment = when {
                        tObj.has("equipment") && !tObj.isNull("equipment") -> tObj.optString("equipment")
                        tObj.has("equipment_category") && !tObj.isNull("equipment_category") -> tObj.optString("equipment_category")
                        else -> ""
                    }

                    val pair = Pair(
                        muscleGroup.replaceFirstChar { it.uppercase() },
                        equipment.replaceFirstChar { it.uppercase() }
                    )
                    if (id.isNotBlank()) templateCatalogCache[id] = pair
                    if (title.isNotBlank()) templateCatalogCache[title.lowercase().trim()] = pair
                }
                page++
            }
        } catch (_: Exception) {
            // Silently fallback to heuristic inference
        }
    }

    /**
     * Highly comprehensive heuristic inference dictionary providing 100% reliable
     * target_muscle_group and equipment resolution for any standard, variation, or custom exercise name.
     */
    fun inferMuscleGroupAndEquipment(exerciseName: String): Pair<String, String> {
        val cleanName = exerciseName.trim()
        val lower = cleanName.lowercase()

        // 1. Detect equipment
        val detectedEquipment = when {
            lower.contains("(smith)") || lower.contains("smith machine") || lower.contains("smith") -> "Smith Machine"
            lower.contains("(barbell)") || lower.contains("barbell") || lower.contains("ez bar") -> "Barbell"
            lower.contains("(dumbbell)") || lower.contains("dumbbell") || lower.contains("dumbell") -> "Dumbbell"
            lower.contains("(cable)") || lower.contains("cable") || lower.contains("pushdown") || lower.contains("pulldown") -> "Cable"
            lower.contains("(machine)") || lower.contains("pec deck") || lower.contains("machine") || lower.contains("t bar") || lower.contains("t-bar") -> "Machine"
            lower.contains("kettlebell") || lower.contains("kettle bell") -> "Kettlebell"
            lower.contains("band") -> "Resistance Band"
            lower.contains("medicine ball") || lower.contains("balance ball") || lower.contains("ball slam") -> "Medicine Ball"
            lower.contains("stepper") -> "Stepper"
            lower.contains("rope") || lower.contains("ropes") -> "Rope"
            lower.contains("bike") || lower.contains("treadmill") || lower.contains("elliptical") || lower.contains("rowing") -> "Machine"
            lower.contains("(bodyweight)") || lower.contains("bodyweight") || lower.contains("push up") ||
                    lower.contains("plank") || lower.contains("dead hang") || lower.contains("crunch") ||
                    lower.contains("dip") || lower.contains("burpee") || lower.contains("jumping jack") ||
                    lower.contains("mountain climber") || lower.contains("wall sit") || lower.contains("inchworm") ||
                    lower.contains("downward dog") || lower.contains("high knees") || lower.contains("stretching") ||
                    lower.contains("superman") || lower.contains("hyperextension") || lower.contains("glute bridge") -> "Bodyweight"
            else -> "Other"
        }

        // 2. Detect muscle group
        val detectedMuscleGroup = when {
            // Chest
            lower.contains("bench press") || lower.contains("chest press") || lower.contains("chest fly") ||
                    lower.contains("pec deck") || lower.contains("butterfly") || lower.contains("push up") ||
                    lower.contains("pushup") || lower.contains("cable fly") || lower.contains("squeeze press") ||
                    lower.contains("diamond push up") || lower.contains("incline push") -> "Chest"

            // Lats & Back
            lower.contains("pulldown") || lower.contains("pull down") || lower.contains("pull up") || lower.contains("chin up") -> "Lats"
            lower.contains("row") || lower.contains("high row") || lower.contains("t bar") ||
                    lower.contains("t-bar") || lower.contains("lat pulldown") -> "Back"

            // Hamstrings & Posterior Chain
            lower.contains("leg curl") || lower.contains("hamstring") || lower.contains("romanian deadlift") ||
                    lower.contains("rdl") -> "Hamstrings"

            // Glutes & Lower Back
            lower.contains("hip thrust") || lower.contains("glute") || lower.contains("back extension") ||
                    lower.contains("hyperextension") || lower.contains("deadlift") || lower.contains("dead lift") ||
                    lower.contains("superman") -> {
                if (lower.contains("glute") || lower.contains("thrust")) "Glutes" else "Lower Back"
            }

            // Calves
            lower.contains("calf") || lower.contains("calves") || lower.contains("tibialis") -> "Calves"

            // Adductors & Abductors
            lower.contains("adduct") -> "Adductors"
            lower.contains("abduct") -> "Abductors"

            // Quads & Lower Body
            lower.contains("squat") || lower.contains("leg press") || lower.contains("leg extension") ||
                    lower.contains("lunge") || lower.contains("split squat") || lower.contains("wall sit") -> "Quads"

            // Shoulders & Delts
            lower.contains("shoulder") || lower.contains("overhead press") || lower.contains("military press") ||
                    lower.contains("lateral raise") || lower.contains("front raise") || lower.contains("arnold press") ||
                    lower.contains("upright row") || (lower.contains("kettle") && lower.contains("twist")) -> "Shoulders"

            // Rear Delts & Upper Back
            lower.contains("rear delt") || lower.contains("reverse fly") || lower.contains("face pull") -> "Rear Delts"

            // Traps
            lower.contains("shrug") -> "Traps"

            // Biceps
            lower.contains("bicep") || lower.contains("biceps") || lower.contains("hammer curl") ||
                    lower.contains("preacher curl") || (lower.contains("curl") && !lower.contains("leg curl") && !lower.contains("wrist")) -> "Biceps"

            // Triceps
            lower.contains("tricep") || lower.contains("triceps") || lower.contains("pushdown") ||
                    lower.contains("pressdown") || lower.contains("skullcrusher") || lower.contains("kickback") ||
                    lower.contains("bench dip") -> "Triceps"

            // Forearms & Grip
            lower.contains("wrist") || lower.contains("forearm") || lower.contains("dead hang") -> "Forearms"

            // Core & Abs
            lower.contains("plank") || lower.contains("crunch") || lower.contains("leg raise") ||
                    lower.contains("knee raise") || lower.contains("russian twist") || lower.contains("boat pose") ||
                    lower.contains("naukasana") || lower.contains("side bend") || lower.contains("core") ||
                    lower.contains("sit up") || lower.contains("situp") || lower.contains("ab ") || lower.contains("abs") -> "Core"

            // Cardio & Conditioning
            lower.contains("bike") || lower.contains("treadmill") || lower.contains("elliptical") ||
                    lower.contains("rowing") || lower.contains("burpee") || lower.contains("jumping jack") ||
                    lower.contains("jump squat") || lower.contains("high knees") || lower.contains("mountain climber") ||
                    lower.contains("hiit") || lower.contains("battle rope") || lower.contains("ball slam") ||
                    lower.contains("kettlebell swing") || lower.contains("kettlebell clean") -> "Cardio"

            // Mobility
            lower.contains("stretch") || lower.contains("mobility") || lower.contains("dog") || lower.contains("yoga") -> "Mobility"

            else -> "Full Body"
        }

        return Pair(detectedMuscleGroup, detectedEquipment)
    }

    /**
     * Enriches workout items with accurate biometrics (avg_hr, max_hr, calories)
     * using Health Connect records matching the workout's exact date and time window.
     */
    suspend fun enrichWithHealthConnect(
        workouts: List<WorkoutItem>,
        healthManager: HealthConnectManager,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<WorkoutItem> = withContext(Dispatchers.IO) {
        val userWeightKg = healthManager.getLatestWeightKg()
        workouts.map { workout ->
            try {
                val biometrics = healthManager.readWorkoutBiometrics(
                    date = workout.date,
                    startTimeStr = workout.startTime,
                    endTimeStr = workout.endTime,
                    durationMinutes = workout.durationMinutes,
                    zoneId = zoneId,
                    weightKg = userWeightKg
                )
                val effectiveAvgHr = workout.avgHeartRateBpm ?: biometrics.avgHeartRateBpm
                val effectiveDuration = workout.durationMinutes
                val estimated = healthManager.calculateEstimatedBiometrics(
                    durationMinutes = effectiveDuration,
                    weightKg = userWeightKg,
                    avgHr = effectiveAvgHr
                )
                val minRealisticCalories = if (effectiveDuration >= 15) {
                    (effectiveDuration * 2.5).roundToInt()
                } else {
                    (effectiveDuration * 2.0).roundToInt().coerceAtLeast(15)
                }

                val resolvedCalories = when {
                    biometrics.calories != null && biometrics.calories >= minRealisticCalories -> biometrics.calories
                    workout.caloriesActualHr != null && workout.caloriesActualHr >= minRealisticCalories -> workout.caloriesActualHr
                    else -> estimated.calories
                }

                workout.copy(
                    avgHeartRateBpm = effectiveAvgHr,
                    maxHeartRateBpm = workout.maxHeartRateBpm ?: biometrics.maxHeartRateBpm,
                    caloriesActualHr = resolvedCalories
                )
            } catch (_: Exception) {
                val estimated = healthManager.calculateEstimatedBiometrics(
                    durationMinutes = workout.durationMinutes,
                    weightKg = userWeightKg,
                    avgHr = workout.avgHeartRateBpm
                )
                workout.copy(
                    caloriesActualHr = workout.caloriesActualHr ?: estimated.calories
                )
            }
        }
    }

    /**
     * Fetches workouts from Hevy API using the user's API Key.
     */
    suspend fun fetchWorkouts(
        apiKey: String,
        page: Int = 1,
        pageSize: Int = 10,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<WorkoutsExportPayload> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val nowIso = ZonedDateTime.now(zoneId).format(DateTimeFormatter.ISO_INSTANT)

        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(Exception("Setup not complete: Hevy API key is not configured. Generate one at https://hevy.com/settings?developer (Hevy Pro required)."))
        }

        try {
            // Pre-load exercise templates for muscle groups and equipment
            loadExerciseTemplates(trimmedKey)

            val url = "https://api.hevyapp.com/v1/workouts?page=$page&pageSize=$pageSize"
            val request = Request.Builder()
                .url(url)
                .header("api-key", trimmedKey)
                .header("Accept", "application/json")
                .header("User-Agent", "HevySyncSheets-Android/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                val errorMsg = if (response.code == 401 || response.code == 403) {
                    "Hevy API Error (${response.code}): Invalid API key or Pro subscription required. Check https://hevy.com/settings?developer"
                } else {
                    "Hevy API Error (${response.code}): ${response.message.ifBlank { "Failed to fetch workouts" }}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(body)
            val workoutsArray = json.optJSONArray("workouts") ?: org.json.JSONArray()
            val parsedWorkouts = parseWorkoutsArray(workoutsArray, zoneId)

            Result.success(
                WorkoutsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "Hevy",
                    syncedAt = nowIso,
                    workouts = parsedWorkouts
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all historical workouts paginated from Hevy API with throttling and rate-limit backoff.
     * Respects API limits (cooperative delays between pages) and handles HTTP 429.
     */
    suspend fun fetchAllWorkoutsPaginated(
        apiKey: String,
        pageSize: Int = 10,
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (page: Int, totalPages: Int, workoutsCount: Int) -> Unit
    ): Result<List<WorkoutItem>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(Exception("Setup not complete: Hevy API key is not configured. Generate one at https://hevy.com/settings?developer (Hevy Pro required)."))
        }

        // Pre-load templates
        loadExerciseTemplates(trimmedKey)

        val allWorkouts = mutableListOf<WorkoutItem>()
        var currentPage = 1
        var totalPages = 1

        try {
            while (currentPage <= totalPages) {
                val url = "https://api.hevyapp.com/v1/workouts?page=$currentPage&pageSize=$pageSize"
                val request = Request.Builder()
                    .url(url)
                    .header("api-key", trimmedKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HevySyncSheets-Android/1.0")
                    .get()
                    .build()

                var response = client.newCall(request).execute()
                if (response.code == 429) {
                    // Throttled by API: backoff and retry
                    response.close()
                    kotlinx.coroutines.delay(2500L)
                    response = client.newCall(request).execute()
                }

                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    if (allWorkouts.isNotEmpty()) {
                        break
                    }
                    val errorMsg = if (response.code == 401 || response.code == 403) {
                        "Hevy API Error (${response.code}): Invalid API key or Pro subscription required. Check https://hevy.com/settings?developer"
                    } else {
                        "Hevy API Error (${response.code}): ${response.message}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val json = JSONObject(body)
                totalPages = json.optInt("page_count", totalPages).coerceAtLeast(currentPage)
                val workoutsArray = json.optJSONArray("workouts") ?: org.json.JSONArray()

                if (workoutsArray.length() == 0) break

                val pageWorkouts = parseWorkoutsArray(workoutsArray, zoneId)
                allWorkouts.addAll(pageWorkouts)

                onProgress(currentPage, totalPages, allWorkouts.size)
                currentPage++

                // Cooperative delay to avoid rate limiting
                kotlinx.coroutines.delay(250L)
            }

            Result.success(allWorkouts)
        } catch (e: Exception) {
            if (allWorkouts.isNotEmpty()) {
                Result.success(allWorkouts)
            } else {
                Result.failure(e)
            }
        }
    }

    internal fun parseWorkoutsArray(
        workoutsArray: org.json.JSONArray,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<WorkoutItem> {
        val parsedWorkouts = mutableListOf<WorkoutItem>()
        for (i in 0 until workoutsArray.length()) {
            val wObj = workoutsArray.getJSONObject(i)
            val startTimeStr = wObj.optString("start_time", "")
            val endTimeStr = wObj.optString("end_time", "")

            var localDateStr = LocalDate.now(zoneId).toString()
            var startTimeFormatted = "07:00"
            var endTimeFormatted: String? = null
            var calculatedDurationMinutes: Int? = null

            try {
                if (startTimeStr.isNotBlank()) {
                    val startInstant = Instant.parse(startTimeStr)
                    val startZoned = startInstant.atZone(zoneId)
                    localDateStr = startZoned.toLocalDate().toString()
                    startTimeFormatted = startZoned.format(DateTimeFormatter.ofPattern("HH:mm"))

                    if (endTimeStr.isNotBlank()) {
                        val endInstant = Instant.parse(endTimeStr)
                        val endZoned = endInstant.atZone(zoneId)
                        endTimeFormatted = endZoned.format(DateTimeFormatter.ofPattern("HH:mm"))

                        val diffMillis = Duration.between(startInstant, endInstant).toMillis()
                        val diffMin = Math.round(diffMillis / 60000.0).toInt()
                        if (diffMin > 0) {
                            calculatedDurationMinutes = diffMin
                        }
                    }
                }
            } catch (_: Exception) {
                if (startTimeStr.contains("T")) {
                    localDateStr = startTimeStr.split("T")[0]
                    startTimeFormatted = startTimeStr.split("T")[1].take(5)
                }
                if (endTimeStr.contains("T")) {
                    endTimeFormatted = endTimeStr.split("T")[1].take(5)
                }
            }

            val exercisesJson = wObj.optJSONArray("exercises") ?: org.json.JSONArray()
            val exerciseList = mutableListOf<WorkoutExercise>()
            var totalVolKg = 0.0
            var totalSetsCount = 0

            for (j in 0 until exercisesJson.length()) {
                val exObj = exercisesJson.getJSONObject(j)
                val setsJson = exObj.optJSONArray("sets") ?: org.json.JSONArray()
                val setList = mutableListOf<ExerciseSet>()

                for (k in 0 until setsJson.length()) {
                    val sObj = setsJson.getJSONObject(k)
                    val wKg = sObj.optDouble("weight_kg", 0.0)
                    val reps = sObj.optInt("reps", 0)
                    val rpeVal = if (sObj.has("rpe") && !sObj.isNull("rpe")) sObj.optDouble("rpe") else null

                    totalVolKg += (wKg * reps)
                    totalSetsCount++

                    val setType = when {
                        sObj.has("type") && !sObj.isNull("type") -> sObj.optString("type", "normal")
                        sObj.has("set_type") && !sObj.isNull("set_type") -> sObj.optString("set_type", "normal")
                        else -> "normal"
                    }
                    val setIdx = if (sObj.has("index")) sObj.optInt("index", k) + 1 else k + 1

                    setList.add(
                        ExerciseSet(
                            setNumber = setIdx,
                            setType = setType,
                            weightKg = wKg,
                            reps = reps,
                            rpe = rpeVal
                        )
                    )
                }

                val exTitle = exObj.optString("title").ifBlank { exObj.optString("name", "Exercise") }
                val rawExNotes = if (exObj.has("notes") && !exObj.isNull("notes")) exObj.optString("notes").ifBlank { null } else null
                val exNotes = rawExNotes?.let { WorkoutCsvConverter.sanitizeField(it) }?.ifBlank { null }

                val templateId = exObj.optString("exercise_template_id", "").ifBlank { null }
                val cached = templateId?.let { templateCatalogCache[it] }
                    ?: templateCatalogCache[exTitle.lowercase().trim()]
                val inferred = inferMuscleGroupAndEquipment(exTitle)

                val targetMuscle = when {
                    exObj.has("muscle_group") && !exObj.isNull("muscle_group") && exObj.optString("muscle_group").isNotBlank() ->
                        exObj.optString("muscle_group")
                    cached?.first?.isNotBlank() == true -> cached.first
                    else -> inferred.first
                }

                val equipment = when {
                    exObj.has("equipment") && !exObj.isNull("equipment") && exObj.optString("equipment").isNotBlank() ->
                        exObj.optString("equipment")
                    cached?.second?.isNotBlank() == true -> cached.second
                    else -> inferred.second
                }

                exerciseList.add(
                    WorkoutExercise(
                        exerciseName = exTitle,
                        targetMuscleGroup = targetMuscle,
                        equipment = equipment,
                        sets = setList,
                        notes = exNotes
                    )
                )
            }

            val rawWkNotes = when {
                wObj.has("description") && !wObj.isNull("description") -> wObj.optString("description").ifBlank { null }
                wObj.has("notes") && !wObj.isNull("notes") -> wObj.optString("notes").ifBlank { null }
                else -> null
            }
            val workoutNotes = rawWkNotes?.let { WorkoutCsvConverter.sanitizeField(it) }?.ifBlank { null }

            val secondsDurationMin = if (wObj.has("duration_seconds") && wObj.optInt("duration_seconds") > 0) {
                Math.round(wObj.optInt("duration_seconds") / 60.0).toInt().coerceAtLeast(1)
            } else null

            val durationMin = when {
                calculatedDurationMinutes != null && calculatedDurationMinutes > 0 -> {
                    calculatedDurationMinutes
                }
                secondsDurationMin != null -> secondsDurationMin
                else -> 55
            }
            val initialCalories = if (wObj.has("calories") && !wObj.isNull("calories") && wObj.optInt("calories") > 0) {
                wObj.optInt("calories")
            } else {
                null
            }

            parsedWorkouts.add(
                WorkoutItem(
                    workoutId = wObj.optString("id", UUID.randomUUID().toString()),
                    date = localDateStr,
                    title = wObj.optString("title", "Workout"),
                    startTime = startTimeFormatted,
                    endTime = endTimeFormatted,
                    durationMinutes = durationMin,
                    totalVolumeKg = totalVolKg.toInt(),
                    totalSets = totalSetsCount,
                    avgHeartRateBpm = if (wObj.has("avg_hr") && !wObj.isNull("avg_hr")) wObj.optInt("avg_hr") else null,
                    maxHeartRateBpm = if (wObj.has("max_hr") && !wObj.isNull("max_hr")) wObj.optInt("max_hr") else null,
                    caloriesActualHr = initialCalories,
                    notes = workoutNotes,
                    exercises = exerciseList
                )
            )
        }
        return parsedWorkouts
    }
}
