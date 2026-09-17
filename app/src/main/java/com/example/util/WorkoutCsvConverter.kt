package com.example.util

import com.example.model.WorkoutItem

object WorkoutCsvConverter {

    val WORKOUT_CSV_HEADER = listOf(
        "workout_id",
        "date",
        "title",
        "start_time",
        "end_time",
        "duration_minutes",
        "total_volume_kg",
        "total_sets",
        "avg_hr_bpm",
        "max_hr_bpm",
        "calories",
        "exercise_name",
        "target_muscle_group",
        "equipment",
        "set_number",
        "set_type",
        "weight_kg",
        "reps",
        "rpe",
        "notes"
    ).joinToString(",")

    /**
     * Converts a list of [WorkoutItem]s into a flattened CSV format suitable for Google Sheets.
     */
    fun toCsvString(workouts: List<WorkoutItem>, includeHeader: Boolean = true): String {
        val sb = StringBuilder()
        if (includeHeader) {
            sb.append(WORKOUT_CSV_HEADER).append("\n")
        }

        for (workout in workouts) {
            if (workout.exercises.isEmpty()) {
                val row = listOf(
                    escapeCsv(workout.workoutId),
                    escapeCsv(workout.date),
                    escapeCsv(workout.title),
                    escapeCsv(workout.startTime),
                    escapeCsv(workout.endTime ?: ""),
                    workout.durationMinutes.toString(),
                    workout.totalVolumeKg.toString(),
                    workout.totalSets.toString(),
                    workout.avgHeartRateBpm?.toString() ?: "",
                    workout.maxHeartRateBpm?.toString() ?: "",
                    workout.caloriesActualHr?.toString() ?: "",
                    "", "", "", "", "", "", "", "",
                    escapeCsv(workout.notes ?: "")
                )
                sb.append(row.joinToString(",")).append("\n")
            } else {
                for (ex in workout.exercises) {
                    if (ex.sets.isEmpty()) {
                        val row = listOf(
                            escapeCsv(workout.workoutId),
                            escapeCsv(workout.date),
                            escapeCsv(workout.title),
                            escapeCsv(workout.startTime),
                            escapeCsv(workout.endTime ?: ""),
                            workout.durationMinutes.toString(),
                            workout.totalVolumeKg.toString(),
                            workout.totalSets.toString(),
                            workout.avgHeartRateBpm?.toString() ?: "",
                            workout.maxHeartRateBpm?.toString() ?: "",
                            workout.caloriesActualHr?.toString() ?: "",
                            escapeCsv(ex.exerciseName),
                            escapeCsv(ex.targetMuscleGroup ?: ""),
                            escapeCsv(ex.equipment ?: ""),
                            "", "", "", "", "",
                            escapeCsv(workout.notes ?: "")
                        )
                        sb.append(row.joinToString(",")).append("\n")
                    } else {
                        for (s in ex.sets) {
                            val row = listOf(
                                escapeCsv(workout.workoutId),
                                escapeCsv(workout.date),
                                escapeCsv(workout.title),
                                escapeCsv(workout.startTime),
                                escapeCsv(workout.endTime ?: ""),
                                workout.durationMinutes.toString(),
                                workout.totalVolumeKg.toString(),
                                workout.totalSets.toString(),
                                workout.avgHeartRateBpm?.toString() ?: "",
                                workout.maxHeartRateBpm?.toString() ?: "",
                                workout.caloriesActualHr?.toString() ?: "",
                                escapeCsv(ex.exerciseName),
                                escapeCsv(ex.targetMuscleGroup ?: ""),
                                escapeCsv(ex.equipment ?: ""),
                                s.setNumber.toString(),
                                escapeCsv(s.setType),
                                s.weightKg.toString(),
                                s.reps.toString(),
                                s.rpe?.toString() ?: "",
                                escapeCsv(workout.notes ?: "")
                            )
                            sb.append(row.joinToString(",")).append("\n")
                        }
                    }
                }
            }
        }

        return sb.toString().trimEnd()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
