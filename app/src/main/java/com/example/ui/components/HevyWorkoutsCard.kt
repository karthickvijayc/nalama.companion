package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WorkoutItem
import com.example.model.WorkoutsExportPayload

@Composable
fun HevyWorkoutsCard(
    payload: WorkoutsExportPayload?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hevy_workouts_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Hevy Workouts Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Source: Hevy (Gym Log API & Export)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh_workouts_button")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Workouts")
                }
            }

            val workouts = payload?.workouts ?: emptyList()

            if (workouts.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No workout records available", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap refresh or provide your Hevy API key in settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Summary Stats Row
                val totalVolume = workouts.sumOf { it.totalVolumeKg }
                val totalSets = workouts.sumOf { it.totalSets }
                val totalDuration = workouts.sumOf { it.durationMinutes }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WorkoutStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Workouts",
                        value = "${workouts.size}",
                        icon = Icons.Default.EventNote
                    )
                    WorkoutStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Volume",
                        value = "${totalVolume} kg",
                        icon = Icons.Default.FitnessCenter
                    )
                    WorkoutStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Total Sets",
                        value = "$totalSets",
                        icon = Icons.Default.Repeat
                    )
                    WorkoutStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Duration",
                        value = "${totalDuration}m",
                        icon = Icons.Default.Timer
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Workouts list
                Text(
                    text = "Ready to Sync Workouts:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                workouts.forEach { workout ->
                    WorkoutItemView(workout = workout)
                }
            }
        }
    }
}

@Composable
private fun WorkoutStatTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkoutItemView(workout: WorkoutItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workout.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = workout.date,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⏰ ${workout.startTime}${if (workout.endTime != null) " - ${workout.endTime}" else ""} (${workout.durationMinutes}m)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "🏋️ ${workout.totalVolumeKg} kg (${workout.totalSets} sets)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (workout.avgHeartRateBpm != null) {
                    Text(
                        text = "❤️ ${workout.avgHeartRateBpm} bpm",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Exercises
            if (workout.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                workout.exercises.forEach { ex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• ${ex.exerciseName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val setsSummary = ex.sets.joinToString(", ") { "${it.weightKg}kg×${it.reps}" }
                        Text(
                            text = setsSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!workout.notes.isNullOrBlank()) {
                Text(
                    text = "📝 ${workout.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
        }
    }
}
