package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TargetFolder

@Composable
fun FolderStructureCard(
    selectedFolder: TargetFolder,
    onSelectFolder: (TargetFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder_structure_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Google Drive Folder Paths",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target directory matching Google Drive export structure:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive tree visual container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E24))
                    .padding(14.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "My Drive /",
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  └── nalama.family/",
                        color = Color(0xFF34D399),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "       ├── context_memory.json (Facts & Profile)",
                        color = Color(0xFFFBBF24),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "       ├── logs_2026_09.json (Monthly partitions)",
                        color = Color(0xFFFBBF24),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "       └── imports/",
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    // Target 1: health_data (active target)
                    val isHealthSelected = selectedFolder == TargetFolder.HEALTH_DATA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isHealthSelected) Color(0xFF064E3B) else Color.Transparent
                            )
                            .clickable { onSelectFolder(TargetFolder.HEALTH_DATA) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "            ├── health_data/  ← Biometrics, Sleep, Steps",
                            color = if (isHealthSelected) Color(0xFF6EE7B7) else Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isHealthSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Text(
                        text = "            │    └── biometrics_daily.json / *.csv",
                        color = Color(0xFFA7F3D0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 24.dp)
                    )

                    // Target 2: gym_workouts
                    val isGymSelected = selectedFolder == TargetFolder.GYM_WORKOUTS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isGymSelected) Color(0xFF1E3A8A) else Color.Transparent
                            )
                            .clickable { onSelectFolder(TargetFolder.GYM_WORKOUTS) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "            └── gym_workouts/ ← Hevy / Strong workouts",
                            color = if (isGymSelected) Color(0xFF93C5FD) else Color(0xFF60A5FA),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isGymSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Text(
                        text = "                 └── hevy_workouts.json / *.csv",
                        color = Color(0xFFBFDBFE),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Target selector row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFolder == TargetFolder.HEALTH_DATA,
                    onClick = { onSelectFolder(TargetFolder.HEALTH_DATA) },
                    label = { Text("health_data (Active)") },
                    leadingIcon = {
                        if (selectedFolder == TargetFolder.HEALTH_DATA) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.testTag("chip_target_health_data")
                )
                FilterChip(
                    selected = selectedFolder == TargetFolder.GYM_WORKOUTS,
                    onClick = { onSelectFolder(TargetFolder.GYM_WORKOUTS) },
                    label = { Text("gym_workouts") },
                    leadingIcon = {
                        if (selectedFolder == TargetFolder.GYM_WORKOUTS) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.testTag("chip_target_gym_workouts")
                )
            }
        }
    }
}
