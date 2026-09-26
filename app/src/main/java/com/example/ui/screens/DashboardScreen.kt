package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.health.HealthConnectAvailability
import com.example.model.SyncSourceApp
import com.example.model.TargetFolder
import com.example.model.WriteMode
import com.example.ui.HealthSyncUiState
import com.example.ui.components.*

@Composable
fun DashboardScreen(
    uiState: HealthSyncUiState,
    onRequestHealthPermissions: () -> Unit,
    onRequestGoogleSignIn: () -> Unit = {},
    onAuthorizeDrive: () -> Unit = onRequestGoogleSignIn,
    onSelectSourceApp: (SyncSourceApp) -> Unit = {},
    onWriteModeSelected: (WriteMode) -> Unit = {},
    onFolderSelected: (TargetFolder) -> Unit = {},
    onExportNow: () -> Unit = {},
    onBulkExport: () -> Unit = {},
    onRefreshHealthData: () -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onDismissExportResult: () -> Unit = {},
    onDismissInitialBulkBanner: () -> Unit = {},
    onDismissBulkExportDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hasDriveToken = uiState.settings.googleOAuthAccessToken.isNotBlank()
    val isDriveGood = uiState.settings.demoModeEnabled || hasDriveToken

    val isHc = uiState.settings.isHealthConnectEnabled
    val isHevy = uiState.settings.isHevyEnabled
    val isHcOk = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE && uiState.hasPermissions
    val isHevyOk = uiState.settings.hevyApiKey.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Row (Sources Status, Google Drive, Auto-Sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source Status
            val sourceStatusLabel = when {
                isHc && isHevy -> "Sources"
                isHc -> "Health Connect"
                isHevy -> "Hevy Workouts"
                else -> "Sources"
            }
            val sourceStatusVal = when {
                isHc && isHevy -> if ((isHcOk || uiState.settings.demoModeEnabled) && (isHevyOk || uiState.settings.demoModeEnabled)) "2 Active" else "Setup Needed"
                isHc -> if (uiState.settings.demoModeEnabled) "Demo Mode" else if (isHcOk) "Connected" else "Needs Access"
                isHevy -> if (uiState.settings.demoModeEnabled) "Demo Mode" else if (isHevyOk) "Ready" else "Key Needed"
                else -> "None Enabled"
            }
            val isSourceGood = when {
                isHc && isHevy -> (isHcOk || uiState.settings.demoModeEnabled) && (isHevyOk || uiState.settings.demoModeEnabled)
                isHc -> isHcOk || uiState.settings.demoModeEnabled
                isHevy -> isHevyOk || uiState.settings.demoModeEnabled
                else -> false
            }

            StatusBadge(
                modifier = Modifier.weight(1f),
                label = sourceStatusLabel,
                status = sourceStatusVal,
                icon = if (isSourceGood) Icons.Default.CheckCircle else Icons.Default.Apps,
                isGood = isSourceGood,
                onClick = onOpenScheduleSettings
            )

            // Destination Status (Google Drive)
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = "Google Drive",
                status = if (uiState.settings.demoModeEnabled) "Demo Mode" else if (hasDriveToken) "Connected" else if (uiState.settings.isGoogleConnected) "Auth Needed" else "Not Linked",
                icon = if (isDriveGood) Icons.Default.CloudDone else Icons.Default.CloudOff,
                isGood = isDriveGood,
                onClick = {
                    if (!isDriveGood) onRequestGoogleSignIn() else onOpenScheduleSettings()
                }
            )

            // Schedule Status
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = "Schedule",
                status = if (uiState.settings.autoSyncEnabled) "${uiState.settings.syncIntervalMinutes}m interval" else "Manual Only",
                icon = Icons.Default.Timer,
                isGood = uiState.settings.autoSyncEnabled,
                onClick = onOpenScheduleSettings
            )
        }

        // Google Drive Authorization Banner if connected account lacks token
        if (!uiState.settings.demoModeEnabled && !hasDriveToken) {
            Surface(
                color = Color(0xFFFEF7E0),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFEEFC3)),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_drive_auth_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Authorization Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB06000)
                        )
                        Text(
                            text = "Tap to grant Nalama permission to save files to your Google Drive account (${uiState.settings.connectedEmail.ifBlank { "Personal Drive" }}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368)
                        )
                        if (uiState.authError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Setup required in Google Cloud Console. See Settings for details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = if (uiState.settings.connectedEmail.isNotBlank()) onAuthorizeDrive else onRequestGoogleSignIn,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("dashboard_authorize_drive_button")
                    ) {
                        Text("Authorize", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Health Permissions notice if needed (only when Health Connect is enabled)
        if (isHc && !uiState.hasPermissions && !uiState.settings.demoModeEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Health Permissions Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Grant access to read steps, sleep, and activity records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onRequestHealthPermissions,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DATA CARDS: Display active data for all enabled sources
        if (isHc) {
            HealthMetricsCard(
                record = uiState.todayRecord,
                hasPermissions = uiState.hasPermissions,
                isHealthAvailable = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE,
                isDemoMode = uiState.settings.demoModeEnabled,
                onRequestPermissions = onRequestHealthPermissions,
                onRefresh = onRefreshHealthData
            )
        }

        if (isHevy) {
            HevyWorkoutsCard(
                payload = uiState.workoutsPayload,
                isApiKeyConfigured = uiState.settings.hevyApiKey.isNotBlank(),
                isDemoMode = uiState.settings.demoModeEnabled,
                onOpenSettings = onOpenScheduleSettings,
                onRefresh = onRefreshHealthData
            )
        }

        if (!isHc && !isHevy) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_no_sources_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No Sync Sources Enabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Head to Settings to enable Health Connect or Hevy to view your daily health metrics and workout logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onOpenScheduleSettings,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Settings")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusBadge(
    label: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGood: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = status,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
