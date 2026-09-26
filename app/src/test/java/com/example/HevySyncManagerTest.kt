package com.example

import com.example.health.HevySyncManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId

class HevySyncManagerTest {

    private val hevySyncManager = HevySyncManager()

    @Test
    fun testParseWorkoutsArrayCalculatesDurationAndLocalTime() {
        val workoutsJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "ea250478-99a8-447d-bc5b-470ac094547b")
                    put("title", "Lower 1 Quad")
                    put("start_time", "2026-03-30T01:54:00Z")
                    put("end_time", "2026-03-30T03:26:00Z")
                    put("exercises", JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("title", "Leg Press (Machine)")
                                put("sets", JSONArray().apply {
                                    put(
                                        JSONObject().apply {
                                            put("weight_kg", 250.0)
                                            put("reps", 8)
                                        }
                                    )
                                })
                            }
                        )
                    })
                }
            )
        }

        // Test with Asia/Kolkata (+05:30)
        val zoneKolkata = ZoneId.of("Asia/Kolkata")
        val items = hevySyncManager.parseWorkoutsArray(workoutsJson, zoneKolkata)

        assertEquals(1, items.size)
        val workout = items[0]
        assertEquals("ea250478-99a8-447d-bc5b-470ac094547b", workout.workoutId)
        assertEquals("2026-03-30", workout.date)
        // 01:54 UTC is 07:24 IST
        assertEquals("07:24", workout.startTime)
        // 03:26 UTC is 08:56 IST
        assertEquals("08:56", workout.endTime)
        // Duration: 01:54 to 03:26 is 92 minutes (not hardcoded 55!)
        assertEquals(92, workout.durationMinutes)
        assertEquals(1, workout.exercises.size)
        assertEquals("Quads", workout.exercises[0].targetMuscleGroup)
        assertEquals("Machine", workout.exercises[0].equipment)
    }

    @Test
    fun testInferMuscleGroupAndEquipment() {
        val (muscle1, eq1) = hevySyncManager.inferMuscleGroupAndEquipment("Leg Press (Machine)")
        assertEquals("Quads", muscle1)
        assertEquals("Machine", eq1)

        val (muscle2, eq2) = hevySyncManager.inferMuscleGroupAndEquipment("Chest Supported Incline Row (Dumbbell)")
        assertEquals("Back", muscle2)
        assertEquals("Dumbbell", eq2)

        val (muscle3, eq3) = hevySyncManager.inferMuscleGroupAndEquipment("Butterfly (Pec Deck)")
        assertEquals("Chest", muscle3)
        assertEquals("Machine", eq3)
    }
}
