package com.example.util

import com.example.model.ExerciseSet
import com.example.model.WorkoutExercise
import com.example.model.WorkoutItem
import com.example.model.WorkoutsExportPayload
import org.json.JSONArray
import org.json.JSONObject

object WorkoutJsonConverter {

    /**
     * Converts a [WorkoutsExportPayload] into the exact JSON structure requested:
     * {
     *   "exportVersion": "1.0",
     *   "sourceApp": "Hevy",
     *   "syncedAt": "...",
     *   "workouts": [ ... ]
     * }
     */
    fun toJsonString(payload: WorkoutsExportPayload, indentSpaces: Int = 2): String {
        val root = JSONObject()
        root.put("exportVersion", payload.exportVersion)
        root.put("sourceApp", payload.sourceApp)
        root.put("syncedAt", payload.syncedAt)

        val workoutsArray = JSONArray()
        for (w in payload.workouts) {
            workoutsArray.put(workoutToJson(w))
        }
        root.put("workouts", workoutsArray)

        return if (indentSpaces > 0) root.toString(indentSpaces) else root.toString()
    }

    private fun workoutToJson(workout: WorkoutItem): JSONObject {
        val obj = JSONObject()
        obj.put("workoutId", workout.workoutId)
        obj.put("date", workout.date)
        obj.put("title", workout.title)
        obj.put("startTime", workout.startTime)
        workout.endTime?.let { obj.put("endTime", it) }
        obj.put("durationMinutes", workout.durationMinutes)
        obj.put("totalVolumeKg", workout.totalVolumeKg)
        obj.put("totalSets", workout.totalSets)
        workout.avgHeartRateBpm?.let { obj.put("avgHeartRateBpm", it) }
        workout.maxHeartRateBpm?.let { obj.put("maxHeartRateBpm", it) }
        workout.caloriesActualHr?.let { obj.put("caloriesActualHr", it) }
        workout.notes?.let { obj.put("notes", it) }

        val exercisesArray = JSONArray()
        for (ex in workout.exercises) {
            val exObj = JSONObject()
            exObj.put("exerciseName", ex.exerciseName)
            ex.targetMuscleGroup?.let { exObj.put("targetMuscleGroup", it) }
            ex.equipment?.let { exObj.put("equipment", it) }

            val setsArray = JSONArray()
            for (s in ex.sets) {
                val setObj = JSONObject()
                setObj.put("setNumber", s.setNumber)
                setObj.put("setType", s.setType)
                setObj.put("weightKg", s.weightKg)
                setObj.put("reps", s.reps)
                s.rpe?.let { setObj.put("rpe", it) }
                setsArray.put(setObj)
            }
            exObj.put("sets", setsArray)
            exercisesArray.put(exObj)
        }
        obj.put("exercises", exercisesArray)

        return obj
    }
}
