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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyRecord

@Composable
fun HealthMetricsCard(
    record: DailyRecord?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("health_metrics_card"),
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
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Today's Biometrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = record?.date ?: "Reading Health Connect...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_metrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Health Data"
                    )
                }
            }

            if (record != null) {
                // Section 1: Activity
                MetricSectionHeader(title = "Activity & Energy", icon = Icons.Default.DirectionsRun)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Steps",
                        value = "%,d".format(record.activity.steps),
                        subtext = "%,.1f km".format(record.activity.distanceMeters / 1000.0)
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Total Calories",
                        value = "%,.0f".format(record.activity.totalCaloriesKcal),
                        subtext = "Active: %,.0f kcal".format(record.activity.activeCaloriesKcal)
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Active Time",
                        value = "${record.activity.activeDurationMinutes}m",
                        subtext = record.activity.vo2MaxMlKgMin?.let { "VO2: ${it.avg}" } ?: "VO2: N/A"
                    )
                }

                // Section 2: Sleep
                MetricSectionHeader(title = "Sleep Architecture", icon = Icons.Default.Bedtime)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalHrs = record.sleep.totalSleepMinutes / 60
                    val totalMins = record.sleep.totalSleepMinutes % 60
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Total Sleep",
                        value = "${totalHrs}h ${totalMins}m",
                        subtext = "Awake: ${record.sleep.awakeMinutes}m"
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Efficiency",
                        value = "${record.sleep.sleepEfficiencyScore}%",
                        subtext = "Score"
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Stages",
                        value = "${record.sleep.deepSleepMinutes}m deep",
                        subtext = "REM: ${record.sleep.remSleepMinutes}m | Light: ${record.sleep.lightSleepMinutes}m"
                    )
                }

                // Section 3: Vitals
                MetricSectionHeader(title = "Cardiovascular Vitals", icon = Icons.Default.MonitorHeart)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Resting HR",
                        value = record.vitals.restingHeartRateBpm?.let { "${it.avg.toInt()} bpm" } ?: "N/A",
                        subtext = record.vitals.restingHeartRateBpm?.let { "Min: ${it.min.toInt()} Max: ${it.max.toInt()}" } ?: ""
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "HRV & SpO2",
                        value = record.vitals.heartRateVariabilityMs?.let { "${it.avg.toInt()} ms" } ?: "N/A",
                        subtext = record.vitals.oxygenSaturationPct?.let { "SpO2: ${it.avg}%" } ?: "SpO2: N/A"
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Blood Pressure",
                        value = record.vitals.bloodPressureMmHg?.let { "${it.systolic.toInt()}/${it.diastolic.toInt()}" } ?: "N/A",
                        subtext = record.vitals.bloodPressureMmHg?.let { "Pulse: ${it.pulse.toInt()} bpm" } ?: ""
                    )
                }

                // Section 4: Body Measurements
                MetricSectionHeader(title = "Body Composition", icon = Icons.Default.AccessibilityNew)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Weight",
                        value = record.bodyMeasurements.weightKg?.let { "$it kg" } ?: "N/A",
                        subtext = "Body Weight"
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Body Fat",
                        value = record.bodyMeasurements.bodyFatPct?.let { "$it%" } ?: "N/A",
                        subtext = "Percentage"
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "Lean Mass",
                        value = record.bodyMeasurements.leanBodyMassKg?.let { "$it kg" } ?: "N/A",
                        subtext = "Lean Muscle"
                    )
                }

                // Sources badges
                if (record.sources.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Detected Sources:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            record.sources.forEach { sourcePkg ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(sourcePkg.substringAfterLast("."), fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (subtext.isNotBlank()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
