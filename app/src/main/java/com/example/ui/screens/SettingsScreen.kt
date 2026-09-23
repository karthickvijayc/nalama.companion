package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SyncSourceApp
import com.example.ui.HealthSyncUiState

@Composable
fun SettingsScreen(
    uiState: HealthSyncUiState,
    onUpdateSourceApp: (SyncSourceApp) -> Unit,
    onUpdateHevyApiKey: (String) -> Unit,
    onTestDriveConnection: () -> Unit,
    onDismissTestResult: () -> Unit,
    onUpdateSyncInterval: (Int) -> Unit,
    onUpdateAutoSync: (Boolean) -> Unit,
    onUpdateCustomFolderPath: (String) -> Unit,
    onUpdateTimezone: (String) -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit = {},
    onNavigateToLanding: () -> Unit = {},
    onDisconnectGoogle: () -> Unit = {},
    onBulkExport: () -> Unit = {},
    onOpenDriveAuth: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onClearCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var hevyApiKeyInput by remember(uiState.settings.hevyApiKey) { mutableStateOf(uiState.settings.hevyApiKey) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val hasDriveToken = uiState.settings.googleOAuthAccessToken.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Google Account & Drive Storage
        SettingsCard(title = "Google Account & Drive", icon = Icons.Default.CloudQueue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_google_g),
                            contentDescription = "Google",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.settings.isGoogleConnected) {
                            uiState.settings.connectedDisplayName.ifEmpty { "Google Account" }
                        } else if (uiState.settings.demoModeEnabled) {
                            "Offline Demo Mode"
                        } else {
                            "Not Connected"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (uiState.settings.isGoogleConnected) {
                            uiState.settings.connectedEmail
                        } else {
                            "Link your Google Drive for automatic backup"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!uiState.settings.isGoogleConnected) {
                    Button(
                        onClick = onNavigateToLanding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("settings_switch_account_button")
                    ) {
                        Text(
                            text = "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (uiState.settings.isGoogleConnected) {
                // Token Authorization Status Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasDriveToken) Color(0xFFE6F4EA) else Color(0xFFFEF7E0),
                    border = BorderStroke(1.dp, if (hasDriveToken) Color(0xFFCEEAD6) else Color(0xFFFEEFC3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (hasDriveToken) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasDriveToken) Color(0xFF137333) else Color(0xFFB06000),
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasDriveToken) "Drive Authorization Active" else "Authorization Required for Cloud Upload",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (hasDriveToken) Color(0xFF137333) else Color(0xFFB06000)
                            )
                            Text(
                                text = if (hasDriveToken) {
                                    "Exports are actively pushed to Google Drive."
                                } else {
                                    "Exports are currently saved locally on phone only until authorized."
                                },
                                fontSize = 11.sp,
                                color = if (hasDriveToken) Color(0xFF137333) else Color(0xFF8A4900)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Google Drive Folder",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = uiState.settings.customFolderPath.ifBlank { uiState.settings.targetFolder.folderPath },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Saves your health data as easy-to-open CSV files directly in your Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Manage Authorization / Token Button
                OutlinedButton(
                    onClick = onOpenDriveAuth,
                    modifier = Modifier.fillMaxWidth().testTag("manage_drive_auth_button")
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasDriveToken) "Manage Drive Token & Permissions" else "Authorize Google Drive (Configure Token)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                // Verify Drive Connection Button
                Button(
                    onClick = onTestDriveConnection,
                    enabled = !uiState.isTestingDrive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("test_drive_connection_button")
                ) {
                    if (uiState.isTestingDrive) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying Drive connection...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify Drive Connection", fontWeight = FontWeight.Bold)
                    }
                }

                // Test Result Banner
                uiState.testDriveResult?.let { result ->
                    Surface(
                        color = if (result.isSuccess) Color(0xFFE6F4EA) else Color(0xFFFCE8E6),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (result.isSuccess) "Connection Successful" else "Connection Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = result.message,
                                    color = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F),
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = onDismissTestResult, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDisconnectGoogle,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section: Diagnostics & Tester Tools
        SettingsCard(title = "Diagnostics & Tester Tools", icon = Icons.Default.BugReport) {
            Text(
                text = "Troubleshooting tools for testers. Inspect live logs, run direct Drive API checks, or reset cache if remote files were deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cache Summary Info Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Local sync cache: ${uiState.cacheSummary.fileCount} file(s) tracked",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.weight(1.3f).testTag("open_diagnostics_console_btn")
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live Logs & Console", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f).testTag("settings_reset_cache_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Cache", fontSize = 12.sp)
                }
            }
        }

        // Section: Source App Selection
        SettingsCard(title = "Primary Sync Source", icon = Icons.Default.Apps) {
            Text(
                text = "Choose which fitness app to sync to Google Drive:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SyncSourceApp.values().forEach { app ->
                    val isSelected = uiState.settings.sourceApp == app
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateSourceApp(app) },
                        label = {
                            Text(
                                text = app.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (app == SyncSourceApp.HEVY) Icons.Default.FitnessCenter else Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_source_${app.id}")
                    )
                }
            }

            if (uiState.settings.sourceApp == SyncSourceApp.HEVY) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = hevyApiKeyInput,
                    onValueChange = {
                        hevyApiKeyInput = it
                        onUpdateHevyApiKey(it)
                    },
                    label = { Text("Hevy API Key") },
                    placeholder = { Text("Enter Hevy Personal API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hevy_api_key_input"),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (hevyApiKeyInput.isBlank()) "API key required to fetch workouts from your account." else "Configured",
                            fontSize = 11.sp
                        )
                    },
                    trailingIcon = {
                        if (hevyApiKeyInput.isNotBlank()) {
                            IconButton(onClick = {
                                hevyApiKeyInput = ""
                                onUpdateHevyApiKey("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hevy_pro_developer_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Hevy Pro Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "To generate an API key for your account, access your developer settings on Hevy:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://hevy.com/settings?developer")
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_hevy_developer_portal_btn")
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open hevy.com/settings?developer", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenHealthConnectSettings,
                    modifier = Modifier.fillMaxWidth().testTag("open_hc_settings_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Health Connect Permissions & Settings", fontSize = 12.sp)
                }
            }
        }

        // Section: Sync Schedule & Frequency
        SettingsCard(title = "Schedule & Frequency", icon = Icons.Default.Timer) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Sync in Background",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically backs up health records to Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.settings.autoSyncEnabled,
                    onCheckedChange = onUpdateAutoSync,
                    modifier = Modifier.testTag("auto_sync_switch")
                )
            }

            if (uiState.settings.autoSyncEnabled) {
                Text(
                    text = "Sync Frequency:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                val intervals = listOf(15, 30, 60, 120)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { mins ->
                        val isSelected = uiState.settings.syncIntervalMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateSyncInterval(mins) },
                            label = {
                                Text(
                                    text = if (mins == 15) "15 min" else "${mins} min",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("interval_chip_$mins")
                        )
                    }
                }
            }
        }

        // Section: Bulk History Sync
        SettingsCard(title = "Historical Sync", icon = Icons.Default.Backup) {
            Text(
                text = "Export your past health records and workout history to Google Drive as CSV files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onBulkExport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Full History to Drive", fontWeight = FontWeight.SemiBold)
            }
        }

        // Section: About Nalama Platform
        SettingsCard(title = "About Nalama", icon = Icons.Default.Info) {
            Text(
                text = "Nalama Health Companion securely bridges your fitness metrics and workout logs into your personal Google Drive in CSV format.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://nalama.family")
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f).testTag("open_nalama_family_btn")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("nalama.family", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://nalama.ai.studio")
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f).testTag("open_nalama_studio_btn")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("nalama.ai.studio", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}
