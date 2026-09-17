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
            }
        }

        // Section 1: Google Apps Script Webhook
        SettingsCard(title = "Google Apps Script Webhook", icon = Icons.Default.Link) {
            Text(
                text = "Enter your Google Apps Script Web App URL to receive biometrics and workout data:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    modifier = Modifier.weight(1f).testTag("test_webhook_button")
                ) {
                    if (uiState.isTestingWebhook) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...")
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection")
                    }
                }

                OutlinedButton(
                    onClick = { showAppsScriptDialog = true },
                    modifier = Modifier.weight(1f).testTag("view_script_guide_button")
                ) {
                    Icon(Icons.Default.IntegrationInstructions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get Apps Script")
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

        // Section 5: Nalama Ecosystem Integration
        SettingsCard(title = "Nalama Ecosystem Integration", icon = Icons.Default.Language) {
            Text(
                text = "This Android app is the companion sensor & workout ingestion engine for your Nalama PWA at nalama.ai.studio / nalama.family.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Ecosystem Architecture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "1. Health Connect & Hevy workouts captured on Android device.\n" +
                               "2. Webhook triggers Google Apps Script.\n" +
                               "3. Data saved to nalama.family/imports folder.\n" +
                               "4. nalama.ai.studio reads spreadsheets for AI coaching & dashboards.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

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
                    Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("nalama.family", fontSize = 11.sp)
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
                    Text("nalama.ai.studio", fontSize = 11.sp)
                }
            }
        }

        // Section 6: Direct Git Distribution & Parent App Link
        SettingsCard(title = "Direct APK Distribution & Parent App Link", icon = Icons.Default.Download) {
            Text(
                text = "Push the codebase and APK to Git, then link directly from the parent app (nalama.family):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "APK Ready in /release/nalama-companion.apk",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "• Pre-signed APK is available in release/nalama-companion.apk in this repository.\n" +
                                "• Since the repository is Public, anyone can download it directly via:\n" +
                                "  https://github.com/<YOUR_USER>/<REPO>/raw/main/release/nalama-companion.apk\n" +
                                "• Pre-signed with a consistent keystore so users can upgrade future versions directly without uninstalling.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Parent App (nalama.family) HTML Snippet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "Add this button or link to nalama.family / nalama.ai.studio:\n\n" +
                                "<a href=\"https://github.com/<USER>/<REPO>/raw/main/release/nalama-companion.apk\" download class=\"nalama-download-btn\">\n" +
                                "  📱 Download Nalama Companion APK (v1.0.0)\n" +
                                "</a>",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Health Connect Settings launcher for sideloaded apps
            OutlinedButton(
                onClick = onOpenHealthConnectSettings,
                modifier = Modifier.fillMaxWidth().testTag("open_hc_settings_btn")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Manage App in Health Connect Settings", fontSize = 12.sp)
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
