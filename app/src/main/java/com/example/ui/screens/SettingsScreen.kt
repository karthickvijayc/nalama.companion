package com.example.ui.screens

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
import com.example.data.AppSettings
import com.example.health.HealthConnectAvailability
import com.example.model.SyncSourceApp
import com.example.model.TargetFolder
import com.example.network.WebhookResult
import com.example.ui.HealthSyncUiState
import com.example.ui.components.AppsScriptDialog
import java.time.ZoneId

@Composable
fun SettingsScreen(
    uiState: HealthSyncUiState,
    onUpdateSourceApp: (SyncSourceApp) -> Unit,
    onUpdateHevyApiKey: (String) -> Unit,
    onUpdateWebhookUrl: (String) -> Unit,
    onTestWebhook: (String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    var webhookUrlInput by remember(uiState.settings.webhookUrl) { mutableStateOf(uiState.settings.webhookUrl) }
    var hevyApiKeyInput by remember(uiState.settings.hevyApiKey) { mutableStateOf(uiState.settings.hevyApiKey) }
    var folderPathInput by remember(uiState.settings.customFolderPath) { mutableStateOf(uiState.settings.customFolderPath) }
    var showAppsScriptDialog by remember { mutableStateOf(false) }

    if (showAppsScriptDialog) {
        AppsScriptDialog(onDismiss = { showAppsScriptDialog = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Google Account & Storage Status
        SettingsCard(title = "Google Account & Backup", icon = Icons.Default.CloudQueue) {
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
                            "Link your Google Drive for private backup"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                        text = if (uiState.settings.isGoogleConnected) "Switch" else "Connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.settings.isGoogleConnected) {
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

        // Section 0: Source App Selection
        SettingsCard(title = "Primary Sync Source", icon = Icons.Default.Apps) {
            Text(
                text = "Choose which fitness ecosystem to sync to Google Sheets:",
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

            // If Hevy selected, show Hevy API Key input
            if (uiState.settings.sourceApp == SyncSourceApp.HEVY) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = hevyApiKeyInput,
                    onValueChange = {
                        hevyApiKeyInput = it
                        onUpdateHevyApiKey(it)
                    },
                    label = { Text("Hevy API Key (Optional for Live Sync)") },
                    placeholder = { Text("Enter Hevy Personal API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hevy_api_key_input"),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (hevyApiKeyInput.isBlank()) "Leave blank to use preloaded Hevy workouts schema." else "Will sync directly via https://api.hevyapp.com/v1/workouts",
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

        // Section 1: Google Apps Script Webhook
        SettingsCard(title = "Google Apps Script Webhook", icon = Icons.Default.Link) {
            Text(
                text = "Enter your Google Apps Script Web App URL to receive biometrics and workout data:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick One-Click Creation Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Need a Webhook URL?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Auto-launch Google Script Studio & copy code in 1 tap.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAppsScriptDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("one_click_create_script_entry_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1-Click Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = webhookUrlInput,
                onValueChange = {
                    webhookUrlInput = it
                    onUpdateWebhookUrl(it)
                },
                label = { Text("Webhook URL (https://script.google.com/...)") },
                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("webhook_url_input"),
                singleLine = true,
                trailingIcon = {
                    if (webhookUrlInput.isNotBlank()) {
                        IconButton(onClick = {
                            webhookUrlInput = ""
                            onUpdateWebhookUrl("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onTestWebhook(webhookUrlInput) },
                    enabled = !uiState.isTestingWebhook && webhookUrlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).testTag("test_webhook_button")
                ) {
                    if (uiState.isTestingWebhook) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { showAppsScriptDialog = true },
                    modifier = Modifier.weight(1f).testTag("view_script_guide_button")
                ) {
                    Icon(Icons.Default.IntegrationInstructions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Setup Guide", fontWeight = FontWeight.SemiBold)
                }
            }

            // Test Result Banner
            uiState.testWebhookResult?.let { result ->
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
                                text = if (result.isSuccess) "Connection Successful (${result.durationMs}ms)" else "Connection Failed",
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
        }

        // Section 2: Sync Schedule / Frequency
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
                        text = "Runs periodic sync using Android WorkManager",
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
                    text = "Sync Frequency (Minutes):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Interval chips (15 default, 30, 60, 120)
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
                                    text = if (mins == 15) "15m (Default)" else "${mins}m",
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
                Text(
                    text = "Note: WorkManager enforces a minimum 15-minute periodic interval to optimize battery.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 3: Target Folder Hierarchy
        SettingsCard(title = "Google Drive Target Hierarchy", icon = Icons.Default.Folder) {
            Text(
                text = "Target directory path in Google Drive (auto-created if missing):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = folderPathInput,
                onValueChange = {
                    folderPathInput = it
                    onUpdateCustomFolderPath(it)
                },
                label = { Text("Drive Folder Path") },
                placeholder = { Text("nalama.family/imports/...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_path_input"),
                singleLine = true
            )

            // Preset shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = folderPathInput == "nalama.family/imports/health_data",
                    onClick = {
                        folderPathInput = "nalama.family/imports/health_data"
                        onUpdateCustomFolderPath(folderPathInput)
                    },
                    label = { Text("Health Data Path", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = folderPathInput == "nalama.family/imports/gym_workouts",
                    onClick = {
                        folderPathInput = "nalama.family/imports/gym_workouts"
                        onUpdateCustomFolderPath(folderPathInput)
                    },
                    label = { Text("Gym Workouts Path", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section 4: Timezone & Demo Mode
        SettingsCard(title = "Timezone & Testing", icon = Icons.Default.Tune) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Timezone: ${uiState.settings.timezoneId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Schema expects 'Asia/Kolkata' or local system zone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        val newTz = if (uiState.settings.timezoneId == "Asia/Kolkata") ZoneId.systemDefault().id else "Asia/Kolkata"
                        onUpdateTimezone(newTz)
                    }
                ) {
                    Text("Toggle Timezone")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Demo Mode switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo Simulation Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Use realistic metrics for testing in emulator without Google Fit installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.settings.demoModeEnabled,
                    onCheckedChange = onToggleDemoMode,
                    modifier = Modifier.testTag("demo_mode_switch")
                )
            }
        }

        // Section 5: About Nalama Platform
        SettingsCard(title = "About Nalama Platform", icon = Icons.Default.Info) {
            Text(
                text = "Nalama Health Companion securely bridges your fitness metrics and workout logs into your personal Google Drive and Sheets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
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
