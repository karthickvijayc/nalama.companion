package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
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
    onSelectSourceApp: (SyncSourceApp) -> Unit,
    onWriteModeSelected: (WriteMode) -> Unit,
    onFolderSelected: (TargetFolder) -> Unit,
    onExportNow: () -> Unit,
    onBulkExport: () -> Unit = {},
    onRefreshHealthData: () -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onDismissExportResult: () -> Unit,
    onDismissInitialBulkBanner: () -> Unit = {},
    onDismissBulkExportDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // First-Time Bulk History Export Banner
        if (!uiState.settings.hasCompletedInitialBulkExport) {
            InitialBulkExportBanner(
                sourceAppName = uiState.settings.sourceApp.displayName,
                onStartBulkExport = onBulkExport,
                onDismiss = onDismissInitialBulkBanner
            )
        }

        // Status Row (Source App Status, Google Drive, Auto-Sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source Status
            val isHevy = uiState.settings.sourceApp == SyncSourceApp.HEVY
            val isHcOk = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE && uiState.hasPermissions
            val isHevyOk = uiState.settings.hevyApiKey.isNotBlank()
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = if (isHevy) "Source: Hevy" else "Health Connect",
                status = if (isHevy) {
                    if (isHevyOk) "Ready" else "Setup Needed"
                } else {
                    if (uiState.settings.demoModeEnabled) "Demo Mode" else if (isHcOk) "Connected" else "Needs Access"
                },
                icon = if (isHevy) Icons.Default.FitnessCenter else (if (isHcOk) Icons.Default.CheckCircle else Icons.Default.Warning),
                isGood = (isHevy && isHevyOk) || isHcOk || uiState.settings.demoModeEnabled,
                onClick = {
                    if (!isHevy && !isHcOk && !uiState.settings.demoModeEnabled) {
                        onRequestHealthPermissions()
                    } else if (isHevy) {
                        onOpenScheduleSettings()
                    }
                }
            )

            // Destination Status (Google Drive)
            val isDriveConnected = uiState.settings.isGoogleConnected
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = "Google Drive",
                status = if (isDriveConnected) "Connected" else "Not Linked",
                icon = if (isDriveConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                isGood = isDriveConnected,
                onClick = onOpenScheduleSettings
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

        // Health Permissions notice if needed (only when Health Connect is selected)
        if (uiState.settings.sourceApp == SyncSourceApp.HEALTH_CONNECT && !uiState.hasPermissions && !uiState.settings.demoModeEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
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

        // Last Export Result Banner
        AnimatedVisibility(visible = uiState.lastExportResult != null) {
            uiState.lastExportResult?.let { result ->
                Surface(
                    color = if (result.isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Column {
                                Text(
                                    text = if (result.isSuccess) "Export Succeeded!" else "Export Notice",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        IconButton(onClick = onDismissExportResult) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        }

        // SOURCE APP SELECTOR (Choose Hevy or Health Connect)
        SourceAppSelectorCard(
            selectedSource = uiState.settings.sourceApp,
            onSelectSource = onSelectSourceApp
        )

        // DATA CARD: Based on selected source app
        if (uiState.settings.sourceApp == SyncSourceApp.HEVY) {
            HevyWorkoutsCard(
                payload = uiState.workoutsPayload,
                isApiKeyConfigured = uiState.settings.hevyApiKey.isNotBlank(),
                isDemoMode = uiState.settings.demoModeEnabled,
                onOpenSettings = onOpenScheduleSettings,
                onRefresh = onRefreshHealthData
            )
        } else {
            HealthMetricsCard(
                record = uiState.todayRecord,
                hasPermissions = uiState.hasPermissions,
                isHealthAvailable = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE,
                isDemoMode = uiState.settings.demoModeEnabled,
                onRequestPermissions = onRequestHealthPermissions,
                onRefresh = onRefreshHealthData
            )
        }

        // Export Controls Card
        ExportControlsCard(
            sourceApp = uiState.settings.sourceApp,
            selectedWriteMode = uiState.settings.writeMode,
            onWriteModeSelected = onWriteModeSelected,
            selectedFolder = uiState.settings.targetFolder,
            onFolderSelected = onFolderSelected,
            customFolderPath = uiState.settings.customFolderPath,
            autoSyncEnabled = uiState.settings.autoSyncEnabled,
            syncIntervalMinutes = uiState.settings.syncIntervalMinutes,
            isExporting = uiState.isExporting,
            onExportNow = onExportNow,
            onBulkExport = onBulkExport,
            onOpenScheduleSettings = onOpenScheduleSettings
        )

        // Bulk Export Progress Dialog
        if (uiState.bulkExportState.isRunning || uiState.bulkExportState.isCompleted || uiState.bulkExportState.error != null) {
            BulkExportProgressDialog(
                bulkState = uiState.bulkExportState,
                sourceAppName = uiState.settings.sourceApp.displayName,
                onDismiss = onDismissBulkExportDialog
            )
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
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
