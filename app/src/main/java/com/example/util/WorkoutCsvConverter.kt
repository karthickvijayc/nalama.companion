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

    fun getHeaderList(): List<String> = WORKOUT_CSV_HEADER.split(",")

    /**
     * Extracts flattened row values for direct spreadsheet rows.
     */
    fun toRowList(workouts: List<WorkoutItem>): List<List<Any?>> {
        val result = mutableListOf<List<Any?>>()
        for (workout in workouts) {
            if (workout.exercises.isEmpty()) {
                result.add(listOf(
                    workout.workoutId,
                    workout.date,
                    workout.title,
                    workout.startTime,
                    workout.endTime ?: "",
                    workout.durationMinutes,
                    workout.totalVolumeKg,
                    workout.totalSets,
                    workout.avgHeartRateBpm ?: "",
                    workout.maxHeartRateBpm ?: "",
                    workout.caloriesActualHr ?: "",
                    "", "", "", "", "", "", "", "",
                    sanitizeField(workout.notes)
                ))
            } else {
                for (ex in workout.exercises) {
                    val combinedNotes = combineNotes(workout.notes, ex.notes)
                    if (ex.sets.isEmpty()) {
                        result.add(listOf(
                            workout.workoutId,
                            workout.date,
                            workout.title,
                            workout.startTime,
                            workout.endTime ?: "",
                            workout.durationMinutes,
                            workout.totalVolumeKg,
                            workout.totalSets,
                            workout.avgHeartRateBpm ?: "",
                            workout.maxHeartRateBpm ?: "",
                            workout.caloriesActualHr ?: "",
                            ex.exerciseName,
                            ex.targetMuscleGroup ?: "",
                            ex.equipment ?: "",
                            "", "", "", "", "",
                            combinedNotes
                        ))
                    } else {
                        for (set in ex.sets) {
                            result.add(listOf(
                                workout.workoutId,
                                workout.date,
                                workout.title,
                                workout.startTime,
                                workout.endTime ?: "",
                                workout.durationMinutes,
                                workout.totalVolumeKg,
                                workout.totalSets,
                                workout.avgHeartRateBpm ?: "",
                                workout.maxHeartRateBpm ?: "",
                                workout.caloriesActualHr ?: "",
                                ex.exerciseName,
                                ex.targetMuscleGroup ?: "",
                                ex.equipment ?: "",
                                set.setNumber,
                                set.setType,
                                set.weightKg,
                                set.reps,
                                set.rpe ?: "",
                                combinedNotes
                            ))
                        }
                    }
                }
            }
        }
        return result
    }

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
                    escapeCsv(sanitizeField(workout.notes))
                )
                sb.append(row.joinToString(",")).append("\n")
            } else {
                for (ex in workout.exercises) {
                    val combinedNotes = combineNotes(workout.notes, ex.notes)
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
                            escapeCsv(combinedNotes)
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
                                escapeCsv(combinedNotes)
                            )
                            sb.append(row.joinToString(",")).append("\n")
                        }
                    }
                }
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Intelligently combines and deduplicates workout-level and exercise-level notes.
     * Prevents runaway repetition ("note | note | note") across repeated exports.
     */
    fun combineNotes(wkNotes: String?, exNotes: String?): String {
        val cleanWk = wkNotes?.let { sanitizeField(it) }?.ifBlank { null }
        val cleanEx = exNotes?.let { sanitizeField(it) }?.ifBlank { null }
        val rawCombined = when {
            cleanWk != null && cleanEx != null -> {
                if (cleanWk.equals(cleanEx, ignoreCase = true) || cleanWk.contains(cleanEx, ignoreCase = true)) {
                    cleanWk
                } else if (cleanEx.contains(cleanWk, ignoreCase = true)) {
                    cleanEx
                } else {
                    "$cleanWk | $cleanEx"
                }
            }
            cleanWk != null -> cleanWk
            cleanEx != null -> cleanEx
            else -> ""
        }

        if (rawCombined.contains(" | ")) {
            val distinctParts = rawCombined.split(" | ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            return distinctParts.joinToString(" | ")
        }
        return rawCombined
    }

    fun sanitizeField(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.replace("\r\n", " | ")
            .replace("\n", " | ")
            .replace("\r", " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun escapeCsv(value: String): String {
        val clean = sanitizeField(value)
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }
}
