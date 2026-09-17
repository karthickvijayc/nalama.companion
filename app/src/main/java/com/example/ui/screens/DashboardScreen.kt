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
import com.example.model.ExportFormat
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
    onFormatSelected: (ExportFormat) -> Unit,
    onWriteModeSelected: (WriteMode) -> Unit,
    onFolderSelected: (TargetFolder) -> Unit,
    onExportNow: () -> Unit,
    onRefreshHealthData: () -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onDismissExportResult: () -> Unit,
    onViewPreview: () -> Unit,
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
        // Nalama Ecosystem Companion Banner
        NalamaCompanionBanner()

        // Status Row (Source App Status, Webhook URL, Auto-Sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source Status
            val isHevy = uiState.settings.sourceApp == SyncSourceApp.HEVY
            val isHcOk = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE && uiState.hasPermissions
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = if (isHevy) "Source: Hevy" else "Health Connect",
                status = if (isHevy) {
                    if (uiState.settings.hevyApiKey.isNotBlank()) "API Key Set" else "Ready (Sample/Local)"
                } else {
                    if (uiState.settings.demoModeEnabled) "Demo Mode" else if (isHcOk) "Connected" else "Attention"
                },
                icon = if (isHevy) Icons.Default.FitnessCenter else (if (isHcOk) Icons.Default.CheckCircle else Icons.Default.Warning),
                isGood = isHevy || isHcOk || uiState.settings.demoModeEnabled,
                onClick = {
                    if (!isHevy && !isHcOk && !uiState.settings.demoModeEnabled) {
                        onRequestHealthPermissions()
                    } else if (isHevy) {
                        onOpenScheduleSettings()
                    }
                }
            )

            // Webhook Status
            val isWebhookConfigured = uiState.settings.webhookUrl.isNotBlank()
            StatusBadge(
                modifier = Modifier.weight(1f),
                label = "Google Sheets",
                status = if (isWebhookConfigured) "Configured" else "Needs URL",
                icon = if (isWebhookConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                isGood = isWebhookConfigured,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Health Connect Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Grant permissions to read steps, sleep, vitals, and body metrics directly.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestHealthPermissions,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant")
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
                onRefresh = onRefreshHealthData
            )
        } else {
            HealthMetricsCard(
                record = uiState.todayRecord,
                onRefresh = onRefreshHealthData
            )
        }

        // Export Controls Card (Format: CSV/JSON/Both, Mode: Append/Overwrite, Folder, Trigger)
        ExportControlsCard(
            sourceApp = uiState.settings.sourceApp,
            selectedFormat = uiState.settings.exportFormat,
            onFormatSelected = onFormatSelected,
            selectedWriteMode = uiState.settings.writeMode,
            onWriteModeSelected = onWriteModeSelected,
            selectedFolder = uiState.settings.targetFolder,
            onFolderSelected = onFolderSelected,
            customFolderPath = uiState.settings.customFolderPath,
            autoSyncEnabled = uiState.settings.autoSyncEnabled,
            syncIntervalMinutes = uiState.settings.syncIntervalMinutes,
            isExporting = uiState.isExporting,
            onExportNow = onExportNow,
            onOpenScheduleSettings = onOpenScheduleSettings
        )

        // Quick Link to Payload Preview & History
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onViewPreview,
                modifier = Modifier
                    .weight(1f)
                    .testTag("view_payload_preview_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Payload Preview")
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = status,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
