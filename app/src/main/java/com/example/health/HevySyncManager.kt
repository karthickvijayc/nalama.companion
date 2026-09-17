package com.example.health

import com.example.model.ExerciseSet
import com.example.model.WorkoutExercise
import com.example.model.WorkoutItem
import com.example.model.WorkoutsExportPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class HevySyncManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches workouts from Hevy API using the user's API Key.
     * If no API key is set or in demo/offline mode, returns realistic sample workouts
     * strictly matching the user's schema.
     */
    suspend fun fetchWorkouts(
        apiKey: String,
        isDemoMode: Boolean = false,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<WorkoutsExportPayload> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)

        if (isDemoMode || trimmedKey.isBlank()) {
            // Return realistic workouts matching the user's exact specification
            return@withContext Result.success(
                WorkoutsExportPayload(
                    exportVersion = "1.0",
                    sourceApp = "Hevy",
                    syncedAt = nowIso,
                    workouts = getSampleWorkouts()
                )
            )
        }

        try {
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
                val errorMsg = "Hevy API Error (${response.code}): ${response.message.ifBlank { "Failed to fetch workouts" }}"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(body)
            val workoutsArray = json.optJSONArray("workouts") ?: org.json.JSONArray()
            val parsedWorkouts = mutableListOf<WorkoutItem>()

            for (i in 0 until workoutsArray.length()) {
                val wObj = workoutsArray.getJSONObject(i)
                val startTimeStr = wObj.optString("start_time", "")
                val endTimeStr = wObj.optString("end_time", "")

                val date = if (startTimeStr.contains("T")) startTimeStr.split("T")[0] else LocalDate.now().toString()
                val startTimeFormatted = if (startTimeStr.contains("T")) {
                    startTimeStr.split("T")[1].take(5)
                } else "07:00"

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

                        setList.add(
                            ExerciseSet(
                                setNumber = k + 1,
                                setType = sObj.optString("set_type", "normal"),
                                weightKg = wKg,
                                reps = reps,
                                rpe = rpeVal
                            )
                        )
                    }

                    exerciseList.add(
                        WorkoutExercise(
                            exerciseName = exObj.optString("title", "Exercise"),
                            targetMuscleGroup = exObj.optString("muscle_group", null),
                            equipment = exObj.optString("equipment", null),
                            sets = setList
                        )
                    )
                }

                parsedWorkouts.add(
                    WorkoutItem(
                        workoutId = wObj.optString("id", UUID.randomUUID().toString()),
                        date = date,
                        title = wObj.optString("title", "Workout"),
                        startTime = startTimeFormatted,
                        endTime = if (endTimeStr.contains("T")) endTimeStr.split("T")[1].take(5) else null,
                        durationMinutes = wObj.optInt("duration_seconds", 3300) / 60,
                        totalVolumeKg = totalVolKg.toInt(),
                        totalSets = totalSetsCount,
                        avgHeartRateBpm = if (wObj.has("avg_hr")) wObj.optInt("avg_hr") else null,
                        maxHeartRateBpm = if (wObj.has("max_hr")) wObj.optInt("max_hr") else null,
                        caloriesActualHr = if (wObj.has("calories")) wObj.optInt("calories") else null,
                        notes = wObj.optString("description", null),
                        exercises = exerciseList
                    )
                )
            }

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
     * Prepares sample data exactly matching the user's schema provided in prompt.
     */
    fun getSampleWorkouts(): List<WorkoutItem> {
        return listOf(
            WorkoutItem(
                workoutId = "c45cee5b-ccf3-40a8-912a-2d9ff9cdfe09",
                date = LocalDate.now().toString(),
                title = "Push & Core Power",
                startTime = "07:15",
                endTime = "08:10",
                durationMinutes = 55,
                totalVolumeKg = 4850,
                totalSets = 16,
                avgHeartRateBpm = 134,
                maxHeartRateBpm = 162,
                caloriesActualHr = 380,
                notes = "Felt strong on dumbbell presses, increased weight on set 3.",
                exercises = listOf(
                    WorkoutExercise(
                        exerciseName = "Bench Press (Dumbbell)",
                        targetMuscleGroup = "Chest",
                        equipment = "Dumbbell",
                        sets = listOf(
                            ExerciseSet(setNumber = 1, setType = "warmup", weightKg = 16.0, reps = 12, rpe = 6.0),
                            ExerciseSet(setNumber = 2, setType = "normal", weightKg = 24.0, reps = 10, rpe = 8.0),
                            ExerciseSet(setNumber = 3, setType = "normal", weightKg = 26.0, reps = 8, rpe = 9.0),
                            ExerciseSet(setNumber = 4, setType = "failure", weightKg = 26.0, reps = 7, rpe = 10.0)
                        )
                    ),
                    WorkoutExercise(
                        exerciseName = "Goblet Squat",
                        targetMuscleGroup = "Quads",
                        equipment = "Kettlebell",
                        sets = listOf(
                            ExerciseSet(setNumber = 1, setType = "normal", weightKg = 20.0, reps = 12, rpe = 7.0),
                            ExerciseSet(setNumber = 2, setType = "normal", weightKg = 24.0, reps = 10, rpe = 8.5)
                        )
                    ),
                    WorkoutExercise(
                        exerciseName = "Standing Overhead Press",
                        targetMuscleGroup = "Shoulders",
                        equipment = "Barbell",
                        sets = listOf(
                            ExerciseSet(setNumber = 1, setType = "normal", weightKg = 40.0, reps = 10, rpe = 8.0),
                            ExerciseSet(setNumber = 2, setType = "normal", weightKg = 45.0, reps = 8, rpe = 9.0)
                        )
                    )
                )
            ),
            WorkoutItem(
                workoutId = "a98df12b-77c1-4b11-9a3d-114f5e7832cd",
                date = LocalDate.now().minusDays(1).toString(),
                title = "Pull & Back Hypertrophy",
                startTime = "07:30",
                endTime = "08:25",
                durationMinutes = 55,
                totalVolumeKg = 5120,
                totalSets = 14,
                avgHeartRateBpm = 128,
                maxHeartRateBpm = 158,
                caloriesActualHr = 360,
                notes = "Lat pulldowns felt very controlled. Solid mind-muscle connection.",
                exercises = listOf(
                    WorkoutExercise(
                        exerciseName = "Lat Pulldown (Cable)",
                        targetMuscleGroup = "Lats",
                        equipment = "Cable",
                        sets = listOf(
                            ExerciseSet(setNumber = 1, setType = "normal", weightKg = 55.0, reps = 12, rpe = 7.5),
                            ExerciseSet(setNumber = 2, setType = "normal", weightKg = 60.0, reps = 10, rpe = 8.5),
                            ExerciseSet(setNumber = 3, setType = "normal", weightKg = 65.0, reps = 8, rpe = 9.0)
                        )
                    ),
                    WorkoutExercise(
                        exerciseName = "Barbell Romanian Deadlift",
                        targetMuscleGroup = "Hamstrings",
                        equipment = "Barbell",
                        sets = listOf(
                            ExerciseSet(setNumber = 1, setType = "normal", weightKg = 70.0, reps = 10, rpe = 8.0),
                            ExerciseSet(setNumber = 2, setType = "normal", weightKg = 80.0, reps = 8, rpe = 9.0)
                        )
                    )
                )
            )
        )
    }
}
