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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WorkoutItem
import com.example.model.WorkoutsExportPayload

@Composable
fun HevyWorkoutsCard(
    payload: WorkoutsExportPayload?,
    isApiKeyConfigured: Boolean,
    isDemoMode: Boolean = false,
    onOpenSettings: () -> Unit,
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

            if (!isDemoMode && !isApiKeyConfigured) {
                // Setup not complete: Hevy API key missing
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hevy_setup_not_complete")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Setup not complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hevy API key is not configured. Please enter your API key in Settings to sync workout records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        // Pro subscriber callout
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "★ Hevy Pro Subscription Required",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "API keys are a Hevy Pro feature. Create your developer key at hevy.com/settings?developer",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri("https://hevy.com/settings?developer")
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_hevy_developer_portal_card_btn")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get API Key", fontSize = 12.sp)
                            }
                            Button(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_settings_for_hevy_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                val workouts = payload?.workouts ?: emptyList()

                if (workouts.isEmpty()) {
                    // Summary Stats Row with clean "--" placeholders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WorkoutStatTile(
                            modifier = Modifier.weight(1f),
                            label = "Workouts",
                            value = "--",
                            icon = Icons.Default.EventNote
                        )
                        WorkoutStatTile(
                            modifier = Modifier.weight(1f),
                            label = "Volume",
                            value = "--",
                            icon = Icons.Default.FitnessCenter
                        )
                        WorkoutStatTile(
                            modifier = Modifier.weight(1f),
                            label = "Total Sets",
                            value = "--",
                            icon = Icons.Default.Repeat
                        )
                        WorkoutStatTile(
                            modifier = Modifier.weight(1f),
                            label = "Duration",
                            value = "--",
                            icon = Icons.Default.Timer
                        )
                    }

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
                            Text(
                                text = "No workouts recorded for today",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Completed workouts in Hevy will appear here automatically.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
}

@Composable
private fun WorkoutStatTile(
    label: String,
    value: String,
    icon: ImageVector,
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
