package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.drive.DriveSyncCacheManager
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveSyncCacheManagerTest {

    private lateinit var cacheManager: DriveSyncCacheManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        cacheManager = DriveSyncCacheManager(context)
    }

    @Test
    fun testMd5HashConsistency() {
        val text = "date,steps,calories\n2026-09-06,8500,2200\n"
        val hash1 = cacheManager.computeMd5(text)
        val hash2 = cacheManager.computeMd5(text)
        assertNotNull(hash1)
        assertEquals(32, hash1.length)
        assertEquals(hash1, hash2)

        val modifiedText = "date,steps,calories\n2026-09-06,9000,2300\n"
        val hash3 = cacheManager.computeMd5(modifiedText)
        assertNotEquals(hash1, hash3)
    }

    @Test
    fun testInPlaceIntraDayUpsert() {
        val date = "2026-09-06"
        val morningRecord = DailyRecord(
            date = date,
            activity = ActivityMetrics(steps = 3000, totalCaloriesKcal = 1200.0)
        )
        val eveningRecord = DailyRecord(
            date = date,
            activity = ActivityMetrics(steps = 10000, totalCaloriesKcal = 2400.0)
        )

        // Initial sync with morning record
        val initialResult = cacheManager.mergeBiometricsCsv(
            existingCsv = null,
            incomingRecords = listOf(morningRecord),
            archiveMaxDays = 180
        )
        assertEquals(1, initialResult.newCount)
        assertEquals(0, initialResult.updatedCount)
        assertEquals(1, initialResult.totalActiveRecords)
        assertTrue(initialResult.activeCsv.contains("3000"))

        // Evening sync with revised step count for the same date
        val revisedResult = cacheManager.mergeBiometricsCsv(
            existingCsv = initialResult.activeCsv,
            incomingRecords = listOf(eveningRecord),
            archiveMaxDays = 180
        )
        assertEquals(1, revisedResult.updatedCount)
        assertEquals(0, revisedResult.newCount)
        assertEquals(1, revisedResult.totalActiveRecords)
        assertTrue(revisedResult.activeCsv.contains("10000"))
        assertFalse(revisedResult.activeCsv.contains("3000"))
    }

    @Test
    fun testRetentionPartitioning180Days() {
        val today = LocalDate.now()
        val recentDate = today.minusDays(30).toString()
        val oldDate = today.minusDays(200).toString()

        val recentRecord = DailyRecord(
            date = recentDate,
            activity = ActivityMetrics(steps = 8000)
        )
        val oldRecord = DailyRecord(
            date = oldDate,
            activity = ActivityMetrics(steps = 6000)
        )

        val result = cacheManager.mergeBiometricsCsv(
            existingCsv = null,
            incomingRecords = listOf(recentRecord, oldRecord),
            archiveMaxDays = 180
        )

        assertEquals(1, result.totalActiveRecords)
        assertEquals(1, result.archivedCount)
        assertTrue(result.activeCsv.contains(recentDate))
        assertFalse(result.activeCsv.contains(oldDate))

        val oldYear = LocalDate.parse(oldDate).year
        assertTrue(result.archiveCsvByYear.containsKey(oldYear))
        assertTrue(result.archiveCsvByYear[oldYear]?.contains(oldDate) == true)
    }

    @Test
    fun testWorkoutDeduplication() {
        val w1 = WorkoutItem(
            workoutId = "workout_101",
            date = "2026-09-06",
            title = "Chest Day",
            startTime = "08:00",
            durationMinutes = 45,
            totalSets = 12
        )
        val w1Updated = WorkoutItem(
            workoutId = "workout_101",
            date = "2026-09-06",
            title = "Chest & Triceps",
            startTime = "08:00",
            durationMinutes = 60,
            totalSets = 16
        )

        val initial = cacheManager.mergeWorkoutsCsv(
            existingCsv = null,
            incomingWorkouts = listOf(w1),
            archiveMaxDays = 180
        )
        assertEquals(1, initial.newCount)
        assertEquals(0, initial.updatedCount)
        assertEquals(1, initial.totalActiveRecords)
        assertTrue(initial.activeCsv.contains("Chest Day"))

        val merged = cacheManager.mergeWorkoutsCsv(
            existingCsv = initial.activeCsv,
            incomingWorkouts = listOf(w1Updated),
            archiveMaxDays = 180
        )
        assertEquals(1, merged.updatedCount)
        assertEquals(0, merged.newCount)
        assertEquals(1, merged.totalActiveRecords)
        assertTrue(merged.activeCsv.contains("Chest & Triceps"))
    }

    @Test
    fun testCacheClearAndReset() {
        val fileName = "daily_biometrics_sync.csv"
        cacheManager.updateCachedFile(
            fileName = fileName,
            fileId = "drive_file_123",
            remoteMd5 = "abcdef0123456789abcdef0123456789",
            localMd5 = "abcdef0123456789abcdef0123456789",
            rowCount = 1
        )

        assertNotNull(cacheManager.getCachedFileInfo(fileName))
        val summaryBefore = cacheManager.getCacheSummary()
        assertEquals(1, summaryBefore.fileCount)

        // Clear all cache
        val cleared = cacheManager.clearAllCache()
        assertNull(cacheManager.getCachedFileInfo(fileName))
        val summaryAfter = cacheManager.getCacheSummary()
        assertEquals(0, summaryAfter.fileCount)
    }

    @Test
    fun testRemoveSingleCachedFileOnRemoteDeletion() {
        val fileName = "workouts_hevy_sync.csv"
        cacheManager.updateCachedFile(
            fileName = fileName,
            fileId = "file_789",
            remoteMd5 = "1234567890abcdef1234567890abcdef",
            localMd5 = "1234567890abcdef1234567890abcdef",
            rowCount = 1
        )

        assertNotNull(cacheManager.getCachedFileInfo(fileName))
        assertEquals("1234567890abcdef1234567890abcdef", cacheManager.getCachedFileInfo(fileName)?.lastRemoteMd5)

        // Simulate file removed remotely
        cacheManager.removeCachedFileInfo(fileName)
        assertNull(cacheManager.getCachedFileInfo(fileName))
    }

    @Test
    fun testWorkoutExercisesAndSetsPreservedAcrossMerges() {
        val w1 = WorkoutItem(
            workoutId = "w_001",
            date = "2026-09-01",
            title = "Bench & Squat",
            startTime = "07:00",
            durationMinutes = 60,
            totalSets = 2,
            exercises = listOf(
                WorkoutExercise(
                    exerciseName = "Barbell Bench Press",
                    targetMuscleGroup = "Chest",
                    equipment = "Barbell",
                    notes = "RPE 8.5 on last set",
                    sets = listOf(
                        ExerciseSet(setNumber = 1, setType = "warmup", weightKg = 60.0, reps = 10),
                        ExerciseSet(setNumber = 2, setType = "normal", weightKg = 100.0, reps = 5, rpe = 8.5)
                    )
                )
            )
        )

        val w2 = WorkoutItem(
            workoutId = "w_002",
            date = "2026-09-02",
            title = "Deadlift & Pull",
            startTime = "07:30",
            durationMinutes = 50,
            totalSets = 1,
            exercises = listOf(
                WorkoutExercise(
                    exerciseName = "Deadlift",
                    targetMuscleGroup = "Back",
                    sets = listOf(
                        ExerciseSet(setNumber = 1, setType = "normal", weightKg = 140.0, reps = 5)
                    )
                )
            )
        )

        // 1. Initial sync with w1
        val initial = cacheManager.mergeWorkoutsCsv(
            existingCsv = null,
            incomingWorkouts = listOf(w1),
            archiveMaxDays = 0
        )
        assertEquals(1, initial.totalActiveRecords)
        assertTrue(initial.activeCsv.contains("Barbell Bench Press"))
        assertTrue(initial.activeCsv.contains("100.0"))
        assertTrue(initial.activeCsv.contains("warmup"))
        assertTrue(initial.activeCsv.contains("RPE 8.5 on last set"))

        // 2. Incremental sync with w2
        val merged = cacheManager.mergeWorkoutsCsv(
            existingCsv = initial.activeCsv,
            incomingWorkouts = listOf(w2),
            archiveMaxDays = 0
        )
        assertEquals(2, merged.totalActiveRecords)
        // Verify w1 exercises and sets survived completely
        assertTrue(merged.activeCsv.contains("Barbell Bench Press"))
        assertTrue(merged.activeCsv.contains("100.0"))
        assertTrue(merged.activeCsv.contains("warmup"))
        assertTrue(merged.activeCsv.contains("RPE 8.5 on last set"))
        // Verify w2 exercises and sets are present
        assertTrue(merged.activeCsv.contains("Deadlift"))
        assertTrue(merged.activeCsv.contains("140.0"))
    }

    @Test
    fun testWorkoutPartitioningByYear() {
        val today = LocalDate.now()
        val recentDate = today.minusDays(20).toString()
        val oldDate = today.minusDays(250).toString()

        val recentWorkout = WorkoutItem(
            workoutId = "w_recent",
            date = recentDate,
            title = "Recent Workout",
            startTime = "08:00",
            durationMinutes = 45,
            totalSets = 3
        )
        val oldWorkout = WorkoutItem(
            workoutId = "w_old",
            date = oldDate,
            title = "Old Workout Archived",
            startTime = "09:00",
            durationMinutes = 50,
            totalSets = 4
        )

        val result = cacheManager.mergeWorkoutsCsv(
            existingCsv = null,
            incomingWorkouts = listOf(recentWorkout, oldWorkout),
            archiveMaxDays = 180
        )

        assertEquals(1, result.totalActiveRecords)
        assertEquals(1, result.archivedCount)
        assertTrue(result.activeCsv.contains(recentDate))
        assertFalse(result.activeCsv.contains(oldDate))

        val oldYear = LocalDate.parse(oldDate).year
        assertTrue(result.archiveCsvByYear.containsKey(oldYear))
        assertTrue(result.archiveCsvByYear[oldYear]?.contains(oldDate) == true)
        assertEquals(1, result.archivedWorkoutsByYear[oldYear]?.size)
    }

    @Test
    fun testSourcesColumnQuoteExplosionPurgedAndCleaned() {
        val badHealthCsv = """
date,sources,steps,distance_meters,total_calories_kcal,active_calories_kcal,active_duration_minutes,vo2_max_avg,total_sleep_minutes,light_sleep_minutes,deep_sleep_minutes,rem_sleep_minutes,awake_minutes,sleep_efficiency_score,resting_hr_min,resting_hr_max,resting_hr_avg,hrv_ms_avg,oxygen_saturation_pct_avg,bp_systolic,bp_diastolic,bp_pulse,weight_kg,body_fat_pct,lean_body_mass_kg
2026-03-30,"""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""com.sec.android.app.shealth;android;com.hevy""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""",6883,0.0,825.6,0.0,91,,0,0,0,0,0,0,,,,,,,,,,,
2026-07-27,,0,0.0,0.0,0.0,0,,0,0,0,0,0,0,,,,,,,,,,,
        """.trimIndent()

        val merged = cacheManager.mergeBiometricsCsv(
            existingCsv = badHealthCsv,
            incomingRecords = emptyList(),
            archiveMaxDays = 365
        )

        // 1. Verify sources column has zero runaway quotes
        assertFalse(merged.activeCsv.contains("\"\"\"\""))
        assertTrue(merged.activeCsv.contains("com.sec.android.app.shealth;android;com.hevy"))

        // 2. Verify empty ghost days are pruned
        assertFalse(merged.activeCsv.contains("2026-07-27"))

        // 3. Verify sleep zeroes are converted to clean blanks on unmeasured days
        assertFalse(merged.activeCsv.contains(",0,0,0,0,0,0,"))

        // 4. Verify distance is estimated from steps instead of remaining 0.0
        assertFalse(merged.activeCsv.contains(",6883,0.0,"))
        assertTrue(merged.activeCsv.contains(",6883,5244.8,"))

        // 5. Verify active calories is reconciled with total calories instead of remaining 0.0
        assertFalse(merged.activeCsv.contains(",825.6,0.0,"))
        assertTrue(merged.activeCsv.contains(",825.6,825.6,"))
    }

    @Test
    fun test2026WorkoutFileOrphanedNotesPurgedAndMuscleGroupsFilled() {
        val badWorkoutCsv = """
workout_id,date,title,start_time,end_time,duration_minutes,total_volume_kg,total_sets,avg_hr_bpm,max_hr_bpm,calories,exercise_name,target_muscle_group,equipment,set_number,set_type,weight_kg,reps,rpe,notes
12 reps of straight(normal) + 20 quick bounces and,,,,,0,0,0,,,,,,,,,,,,
12 reps of outward v heels + 20 quick bounces and,,,,,0,0,0,,,,,,,,,,,,
Cable Pulls (Exercise 2),,,,,0,0,0,,,,,,,,,,,,
ee7ce691-3dfe-401e-bc82-47a8241dbb40,2026-01-08,Lower AF,01:20,02:15,55,10042,16,,,,Leg Press (Machine),,,1,warmup,100.0,10,,
ee7ce691-3dfe-401e-bc82-47a8241dbb40,2026-01-08,Lower AF,01:20,02:15,55,10042,16,,,,Romanian Deadlift (Barbell),,,1,normal,40.0,10,,
        """.trimIndent()

        val merged = cacheManager.mergeWorkoutsCsv(
            existingCsv = badWorkoutCsv,
            incomingWorkouts = emptyList(),
            archiveMaxDays = 365
        )

        // 1. Verify all orphaned notes lines are dropped
        assertFalse(merged.activeCsv.contains("12 reps of straight"))
        assertFalse(merged.activeCsv.contains("Cable Pulls"))

        // 2. Verify legitimate workout row survived
        assertTrue(merged.activeCsv.contains("ee7ce691-3dfe-401e-bc82-47a8241dbb40"))
        assertTrue(merged.activeCsv.contains("2026-01-08"))

        // 3. Verify target_muscle_group and equipment were automatically backfilled
        assertTrue(merged.activeCsv.contains("Leg Press (Machine),Quads,Machine"))
        assertTrue(merged.activeCsv.contains("Romanian Deadlift (Barbell),Hamstrings,Barbell"))

        // 4. Verify calories were estimated from duration
        assertTrue(merged.activeCsv.contains(",330,Leg Press"))
    }

    @Test
    fun test2025WorkoutFileMuscleGroupsAndCaloriesAutoFilled() {
        val workout2025Csv = """
workout_id,date,title,start_time,end_time,duration_minutes,total_volume_kg,total_sets,avg_hr_bpm,max_hr_bpm,calories,exercise_name,target_muscle_group,equipment,set_number,set_type,weight_kg,reps,rpe,notes
7790d33a-0b60-4fa9-9996-e447508d8ce1,2025-11-17,Push AF,01:16,02:36,55,2700,21,,,,Chest Press (Machine),,,1,normal,18.75,12,,Restarting after back injury
7790d33a-0b60-4fa9-9996-e447508d8ce1,2025-11-17,Push AF,01:16,02:36,55,2700,21,,,,Butterfly (Pec Deck),,,1,normal,23.75,12,,Restarting after back injury
        """.trimIndent()

        val merged = cacheManager.mergeWorkoutsCsv(
            existingCsv = workout2025Csv,
            incomingWorkouts = emptyList(),
            archiveMaxDays = 0 // Don't filter by active cutoff for this test
        )

        // 1. Verify Chest Press has Chest & Machine
        assertTrue(merged.activeCsv.contains("Chest Press (Machine),Chest,Machine"))

        // 2. Verify Butterfly (Pec Deck) has Chest & Machine
        assertTrue(merged.activeCsv.contains("Butterfly (Pec Deck),Chest,Machine"))

        // 3. Verify calories populated
        assertTrue(merged.activeCsv.contains(",330,Chest Press"))
    }

    @Test
    fun testNoteDeduplication() {
        val input = "280 kgs on leg press | 280 kgs on leg press"
        val deduplicated = com.example.util.WorkoutCsvConverter.combineNotes(input, null)
        assertEquals("280 kgs on leg press", deduplicated)

        val input2 = "Chest shoulder triceps | Using smith machine | Chest shoulder triceps | Using smith machine"
        val deduplicated2 = com.example.util.WorkoutCsvConverter.combineNotes(input2, null)
        assertEquals("Chest shoulder triceps | Using smith machine", deduplicated2)
    }

    @Test
    fun testPersonalizedWeightCalorieEstimation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val healthManager = com.example.health.HealthConnectManager(context)

        // 60 minutes for an 80 kg person: 5.5 * 80 * 1.0 = 440 kcal
        val cal80 = healthManager.calculateEstimatedBiometrics(durationMinutes = 60, weightKg = 80.0)
        assertEquals(440, cal80.calories)

        // 60 minutes for a 60 kg person: 5.5 * 60 * 1.0 = 330 kcal
        val cal60 = healthManager.calculateEstimatedBiometrics(durationMinutes = 60, weightKg = 60.0)
        assertEquals(330, cal60.calories)

        // With high heart rate (e.g. 140 bpm, multiplier 1.20): 440 * 1.20 = 528 kcal
        val cal80WithHr = healthManager.calculateEstimatedBiometrics(durationMinutes = 60, weightKg = 80.0, avgHr = 140)
        assertEquals(528, cal80WithHr.calories)

        // Default 70 kg fallback when weight is null: 5.5 * 70 * 1.0 = 385 kcal
        val calDefault = healthManager.calculateEstimatedBiometrics(durationMinutes = 60, weightKg = null)
        assertEquals(385, calDefault.calories)
    }

    @Test
    fun testArchivePartitionMergeWithZeroCutoff() {
        val oldDate1 = "2025-05-10"
        val oldDate2 = "2025-06-15"
        val r1 = DailyRecord(date = oldDate1, activity = ActivityMetrics(steps = 5000))
        val r2 = DailyRecord(date = oldDate2, activity = ActivityMetrics(steps = 6000))

        val result = cacheManager.mergeBiometricsCsv(
            existingCsv = null,
            incomingRecords = listOf(r1, r2),
            archiveMaxDays = 0
        )

        assertEquals(2, result.totalActiveRecords)
        assertEquals(0, result.archivedCount)
        assertTrue(result.activeCsv.contains(oldDate1))
        assertTrue(result.activeCsv.contains(oldDate2))
    }

    @Test
    fun testSequentialDateAutoRepair() {
        val corruptedCsv = """
            date,sources,steps,distance_meters,total_calories_kcal,active_calories_kcal,active_duration_minutes,vo2_max_avg,total_sleep_minutes,light_sleep_minutes,deep_sleep_minutes,rem_sleep_minutes,awake_minutes,sleep_efficiency_score,resting_hr_min,resting_hr_max,resting_hr_avg,hrv_ms_avg,oxygen_saturation_pct_avg,bp_systolic,bp_diastolic,bp_pulse,weight_kg,body_fat_pct,lean_body_mass_kg
            2026-03-09,com.sec.android.app.shealth,21776,16593.3,971.2,971.2,122,,,,,,,,,,,,,,,,,,
            2026-10-10,com.sec.android.app.shealth,20249,15429.7,876.3,876.3,101,,,,,,,,,,,,,,,,,,
            2026-03-11,com.sec.android.app.shealth,13919,10606.3,747.5,747.5,90,,,,,,,,,,,,,,,,,,
            2026-07-29,com.sec.android.app.shealth,5650,4305.3,254.3,254.3,0,,,,,,,,,,,,,,,,,,
            2026-03-30,com.sec.android.app.shealth,5429,4136.9,244.3,244.3,0,,,,,,,,,,,,,,,,,,
            2026-07-31,com.sec.android.app.shealth,14884,11341.6,669.8,669.8,0,,,,,,,,,,,,,,,,,,
        """.trimIndent()

        val result = cacheManager.mergeBiometricsCsv(
            existingCsv = corruptedCsv,
            incomingRecords = emptyList(),
            archiveMaxDays = 0 // retain all in active for verification
        )

        // 2026-10-10 between 03-09 and 03-11 must be auto-repaired to 2026-03-10
        assertTrue(result.activeCsv.contains("2026-03-10,com.sec.android.app.shealth,20249"))
        assertFalse(result.activeCsv.contains("2026-10-10"))

        // 2026-03-30 between 07-29 and 07-31 must be auto-repaired to 2026-07-30
        assertTrue(result.activeCsv.contains("2026-07-30,com.sec.android.app.shealth,5429"))
        assertFalse(result.activeCsv.contains("2026-03-30,com.sec.android.app.shealth,5429"))
    }

    @Test
    fun testPartialDistanceAndCalorieReconciliation() {
        // High step count (33,932) with only partial GPS distance (546.7m) and partial calories (81 kcal)
        val line = listOf(
            "2026-07-16", "com.sec.android.app.shealth", "33932", "546.7", "81.0", "81.0", "13",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""
        )
        val csv = "date,sources,steps,distance_meters,total_calories_kcal,active_calories_kcal,active_duration_minutes,vo2_max_avg,total_sleep_minutes,light_sleep_minutes,deep_sleep_minutes,rem_sleep_minutes,awake_minutes,sleep_efficiency_score,resting_hr_min,resting_hr_max,resting_hr_avg,hrv_ms_avg,oxygen_saturation_pct_avg,bp_systolic,bp_diastolic,bp_pulse,weight_kg,body_fat_pct,lean_body_mass_kg\n" +
                line.joinToString(",")

        val result = cacheManager.mergeBiometricsCsv(
            existingCsv = csv,
            incomingRecords = emptyList(),
            archiveMaxDays = 0
        )

        // Distance should be estimated from steps (33932 * 0.762 = 25856.2m)
        assertTrue(result.activeCsv.contains("25856.2"))
        assertFalse(result.activeCsv.contains(",546.7,"))

        // Calories should be estimated from steps (33932 * 0.045 = 1526.9 kcal)
        assertTrue(result.activeCsv.contains("1526.9"))
        assertFalse(result.activeCsv.contains(",81.0,"))
    }

    @Test
    fun testLeanBodyMassDerivationFromWeightAndBodyFat() {
        val line = listOf(
            "2026-04-17", "cn.fitdays.fitdays", "5000", "3810.0", "225.0", "225.0", "0",
            "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "90.7", "29.3", ""
        )
        val csv = "date,sources,steps,distance_meters,total_calories_kcal,active_calories_kcal,active_duration_minutes,vo2_max_avg,total_sleep_minutes,light_sleep_minutes,deep_sleep_minutes,rem_sleep_minutes,awake_minutes,sleep_efficiency_score,resting_hr_min,resting_hr_max,resting_hr_avg,hrv_ms_avg,oxygen_saturation_pct_avg,bp_systolic,bp_diastolic,bp_pulse,weight_kg,body_fat_pct,lean_body_mass_kg\n" +
                line.joinToString(",")

        val result = cacheManager.mergeBiometricsCsv(
            existingCsv = csv,
            incomingRecords = emptyList(),
            archiveMaxDays = 0
        )

        // 90.7 kg * (1 - 0.293) = 64.1 kg lean mass
        assertTrue(result.activeCsv.contains("90.7,29.3,64.1"))
    }
}
