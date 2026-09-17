package com.example.model

/**
 * Data models for Gym / Workout tracking matching Hevy canonical export schema:
 * {
 *   "exportVersion": "1.0",
 *   "sourceApp": "Hevy",
 *   "syncedAt": "2026-09-06T08:45:00Z",
 *   "workouts": [ ... ]
 * }
 */
data class WorkoutsExportPayload(
    val exportVersion: String = "1.0",
    val sourceApp: String = "Hevy",
    val syncedAt: String,
    val workouts: List<WorkoutItem> = emptyList()
)

data class WorkoutItem(
    val workoutId: String,
    val date: String, // "YYYY-MM-DD"
    val title: String,
    val startTime: String, // "07:15"
    val endTime: String? = null, // "08:10"
    val durationMinutes: Int = 0,
    val totalVolumeKg: Int = 0,
    val totalSets: Int = 0,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val caloriesActualHr: Int? = null,
    val notes: String? = null,
    val exercises: List<WorkoutExercise> = emptyList()
)

data class WorkoutExercise(
    val exerciseName: String,
    val targetMuscleGroup: String? = null,
    val equipment: String? = null,
    val sets: List<ExerciseSet> = emptyList()
)

data class ExerciseSet(
    val setNumber: Int,
    val setType: String = "normal", // "warmup", "normal", "failure", "drop"
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null
)
