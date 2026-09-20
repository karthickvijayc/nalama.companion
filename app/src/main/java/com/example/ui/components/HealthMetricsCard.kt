package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.example.model.DailyRecord
import java.time.LocalDate

@Composable
fun HealthMetricsCard(
    record: DailyRecord?,
    hasPermissions: Boolean,
    isHealthAvailable: Boolean = true,
    isDemoMode: Boolean = false,
    onRequestPermissions: () -> Unit,
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
                            text = record?.date ?: LocalDate.now().toString(),
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

            // Body rendering based on setup & permissions
            when {
                !isDemoMode && !isHealthAvailable -> {
                    // Setup not complete: Health Connect unavailable on device
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("health_metrics_setup_not_complete")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Setup not complete",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Health Connect is not available on this device. Please install Health Connect from Google Play to sync biometrics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                !isDemoMode && !hasPermissions -> {
                    // Permission not granted
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("health_metrics_permission_not_granted")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Permission not granted",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Health Connect permissions have not been granted. Grant permissions to view and export today's biometrics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = onRequestPermissions,
                                modifier = Modifier.testTag("grant_health_permissions_button")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Permission")
                            }
                        }
                    }
                }

                record == null -> {
                    // Loading or waiting for initial read
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }

                else -> {
                    // Permissions granted: display metrics with clean "--" fallback when no data logged today
                    val hasAnyData = record.activity.steps > 0 ||
                            record.activity.distanceMeters > 0.0 ||
                            record.activity.totalCaloriesKcal > 0.0 ||
                            record.sleep.totalSleepMinutes > 0 ||
                            record.vitals.restingHeartRateBpm != null ||
                            record.vitals.bloodPressureMmHg != null ||
                            record.bodyMeasurements.weightKg != null

                    if (!hasAnyData) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "No biometrics recorded yet for today",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Section 1: Activity
                    MetricSectionHeader(title = "Activity & Energy", icon = Icons.Default.DirectionsRun)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Steps",
                            value = if (record.activity.steps > 0) "%,d".format(record.activity.steps) else "--",
                            subtext = if (record.activity.distanceMeters > 0.0) "%,.1f km".format(record.activity.distanceMeters / 1000.0) else "-- km"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Total Calories",
                            value = if (record.activity.totalCaloriesKcal > 0.0) "%,.0f".format(record.activity.totalCaloriesKcal) else "--",
                            subtext = if (record.activity.activeCaloriesKcal > 0.0) "Active: %,.0f kcal".format(record.activity.activeCaloriesKcal) else "Active: --"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Active Time",
                            value = if (record.activity.activeDurationMinutes > 0) "${record.activity.activeDurationMinutes}m" else "--",
                            subtext = record.activity.vo2MaxMlKgMin?.let { "VO2: ${it.avg}" } ?: "VO2: --"
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
                            value = if (record.sleep.totalSleepMinutes > 0) "${totalHrs}h ${totalMins}m" else "--",
                            subtext = if (record.sleep.totalSleepMinutes > 0) "Awake: ${record.sleep.awakeMinutes}m" else "Awake: --"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Efficiency",
                            value = if (record.sleep.sleepEfficiencyScore > 0) "${record.sleep.sleepEfficiencyScore}%" else "--",
                            subtext = "Score"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Stages",
                            value = if (record.sleep.deepSleepMinutes > 0) "${record.sleep.deepSleepMinutes}m deep" else "--",
                            subtext = if (record.sleep.remSleepMinutes > 0 || record.sleep.lightSleepMinutes > 0) "REM: ${record.sleep.remSleepMinutes}m | Light: ${record.sleep.lightSleepMinutes}m" else "--"
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
                            value = record.vitals.restingHeartRateBpm?.let { "${it.avg.toInt()} bpm" } ?: "--",
                            subtext = record.vitals.restingHeartRateBpm?.let { "Min: ${it.min.toInt()} Max: ${it.max.toInt()}" } ?: ""
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "HRV & SpO2",
                            value = record.vitals.heartRateVariabilityMs?.let { "${it.avg.toInt()} ms" } ?: "--",
                            subtext = record.vitals.oxygenSaturationPct?.let { "SpO2: ${it.avg}%" } ?: "SpO2: --"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Blood Pressure",
                            value = record.vitals.bloodPressureMmHg?.let { "${it.systolic.toInt()}/${it.diastolic.toInt()}" } ?: "--",
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
                            value = record.bodyMeasurements.weightKg?.let { "$it kg" } ?: "--",
                            subtext = "Body Weight"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Body Fat",
                            value = record.bodyMeasurements.bodyFatPct?.let { "$it%" } ?: "--",
                            subtext = "Percentage"
                        )
                        MetricBox(
                            modifier = Modifier.weight(1f),
                            label = "Lean Mass",
                            value = record.bodyMeasurements.leanBodyMassKg?.let { "$it kg" } ?: "--",
                            subtext = "Lean Muscle"
                        )
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
