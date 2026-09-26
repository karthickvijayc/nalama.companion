package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.health.HealthConnectAvailability
import com.example.model.SyncSourceApp
import com.example.model.WriteMode
import com.example.ui.HealthSyncUiState
import com.example.ui.components.BulkExportProgressDialog
import com.example.util.CertificateHelper

@Composable
fun SettingsScreen(
    uiState: HealthSyncUiState,
    onUpdateHealthConnectEnabled: (Boolean) -> Unit = {},
    onUpdateHevyEnabled: (Boolean) -> Unit = {},
    onUpdateSourceApp: (SyncSourceApp) -> Unit = {},
    onUpdateHevyApiKey: (String) -> Unit = {},
    onWriteModeSelected: (WriteMode) -> Unit = {},
    onExportNow: () -> Unit = {},
    onDismissExportResult: () -> Unit = {},
    onDismissBulkExportDialog: () -> Unit = {},
    onTestDriveConnection: () -> Unit = {},
    onDismissTestResult: () -> Unit = {},
    onUpdateSyncInterval: (Int) -> Unit = {},
    onUpdateAutoSync: (Boolean) -> Unit = {},
    onUpdateCustomFolderPath: (String) -> Unit = {},
    onUpdateTimezone: (String) -> Unit = {},
    onToggleDemoMode: (Boolean) -> Unit = {},
    onRequestHealthPermissions: () -> Unit = {},
    onRequestGoogleSignIn: () -> Unit = {},
    onAuthorizeDrive: () -> Unit = {},
    onVerifyAndSaveOAuthToken: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onClearAuthError: () -> Unit = {},
    onOpenHealthConnectSettings: () -> Unit = {},
    onNavigateToLanding: () -> Unit = {},
    onDisconnectGoogle: () -> Unit = {},
    onBulkExport: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onClearCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var hevyApiKeyInput by remember(uiState.settings.hevyApiKey) { mutableStateOf(uiState.settings.hevyApiKey) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val hasDriveToken = uiState.settings.googleOAuthAccessToken.isNotBlank()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showManualTokenDialog by remember { mutableStateOf(false) }
    var manualTokenInput by remember { mutableStateOf("") }
    var manualTokenVerifying by remember { mutableStateOf(false) }
    var manualTokenFeedback by remember { mutableStateOf<String?>(null) }
    var isAdvancedOptionsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Google Account & Drive Storage
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("settings_switch_account_button")
                    ) {
                        Text(
                            text = "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (hasDriveToken || uiState.settings.demoModeEnabled) {
                    // Space-optimized Green "Connected" Badge inline with account
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE6F4EA),
                        border = BorderStroke(1.dp, Color(0xFFCEEAD6)),
                        modifier = Modifier.testTag("google_drive_connected_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF137333),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Connected",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF137333)
                            )
                        }
                    }
                }
            }

            if (uiState.settings.isGoogleConnected) {
                if (!hasDriveToken && !uiState.settings.demoModeEnabled) {
                    // Authorization Required Warning Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF7E0),
                        border = BorderStroke(1.dp, Color(0xFFFEEFC3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFB06000),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Google Drive Permission Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB06000)
                                )
                            }
                            Text(
                                text = "Your Google account is linked (${uiState.settings.connectedEmail}), but Drive storage access needs to be authorized so files can be created in your Drive.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF5F6368)
                            )

                            if (uiState.authError != null) {
                                val pkgName = remember { CertificateHelper.getPackageName(context) }
                                val sha1 = remember { CertificateHelper.getSigningSha1(context) }
                                Surface(
                                    color = Color(0xFFFCE8E6),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFAD2CF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Google Cloud Console Registration Required",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFC5221F),
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = onClearAuthError,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Text(
                                            text = uiState.authError,
                                            fontSize = 11.sp,
                                            color = Color(0xFFC5221F)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Package: $pkgName",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF202124)
                                        )
                                        Text(
                                            text = "SHA-1: $sha1",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF202124)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val copyText = "Package Name: $pkgName\nSigning SHA-1: $sha1"
                                                clipboardManager.setText(AnnotatedString(copyText))
                                                Toast.makeText(context, "Copied OAuth setup details to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy OAuth Setup Info", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onAuthorizeDrive,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.2f).testTag("settings_authorize_drive_button")
                                ) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Authorize Drive", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }

                                OutlinedButton(
                                    onClick = onRequestGoogleSignIn,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_switch_account_btn")
                                ) {
                                    Text("Switch", fontSize = 11.5.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    manualTokenFeedback = null
                                    showManualTokenDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("settings_manual_token_button")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enter Access Token Manually", fontSize = 11.5.sp)
                            }
                        }
                    }
                }

                // Actions Row: Verify Connection & Sign Out side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTestDriveConnection,
                        enabled = !uiState.isTestingDrive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1.3f).testTag("test_drive_connection_button")
                    ) {
                        if (uiState.isTestingDrive) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify Connection", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onDisconnectGoogle,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(0.9f).testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", fontSize = 11.5.sp)
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
            }
        }

        // Section 2: Sync Sources (Multi-Source Management)
        SettingsCard(title = "Sync Sources", icon = Icons.Default.Apps) {
            Text(
                text = "Enable the fitness sources you want to back up to Google Drive and configure their settings:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Source 1: Health Connect
            val isHcOk = uiState.healthAvailability == HealthConnectAvailability.AVAILABLE && uiState.hasPermissions
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
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
                                    text = "Android Health Connect",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Steps, sleep, heart rate, BP, and vitals",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.settings.isHealthConnectEnabled,
                            onCheckedChange = onUpdateHealthConnectEnabled,
                            modifier = Modifier.testTag("health_connect_enable_switch")
                        )
                    }

                    if (uiState.settings.isHealthConnectEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        val isGoodHc = isHcOk || uiState.settings.demoModeEnabled
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Space-optimized status badge matching Connected badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isGoodHc) Color(0xFFE6F4EA) else Color(0xFFFEF7E0),
                                border = BorderStroke(1.dp, if (isGoodHc) Color(0xFFCEEAD6) else Color(0xFFFEEFC3)),
                                modifier = Modifier.testTag("health_connect_status_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGoodHc) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isGoodHc) Color(0xFF137333) else Color(0xFFB06000),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (uiState.settings.demoModeEnabled) "Demo Mode" else if (isHcOk) "Permissions Granted" else "Permissions Needed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isGoodHc) Color(0xFF137333) else Color(0xFFB06000)
                                    )
                                }
                            }

                            if (!isHcOk && !uiState.settings.demoModeEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = onOpenHealthConnectSettings,
                                        modifier = Modifier.size(32.dp).testTag("open_hc_settings_btn")
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                                    }
                                    Button(
                                        onClick = onRequestHealthPermissions,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                        modifier = Modifier.testTag("settings_grant_hc_perms_btn")
                                    ) {
                                        Text("Grant", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onOpenHealthConnectSettings,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    modifier = Modifier.testTag("open_hc_settings_btn")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Settings", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Source 2: Hevy Workouts
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Hevy Gym Workouts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Exercises, weights (kg), reps, sets, RPE",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.settings.isHevyEnabled,
                            onCheckedChange = onUpdateHevyEnabled,
                            modifier = Modifier.testTag("hevy_enable_switch")
                        )
                    }

                    if (uiState.settings.isHevyEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

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
                                    fontSize = 11.sp,
                                    color = if (hevyApiKeyInput.isBlank()) Color(0xFFB06000) else Color(0xFF137333)
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("hevy_pro_developer_card")
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
                                        Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Hevy Pro Required for API Access",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    text = "Generate your personal API key on the Hevy developer settings page:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            uriHandler.openUri("https://hevy.com/settings?developer")
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("open_hevy_developer_portal_btn")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open hevy.com/settings?developer", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Sync Controls (Merged from Dashboard)
        SettingsCard(title = "Sync Controls", icon = Icons.Default.CloudSync) {
            Text(
                text = "Manual sync and historical export for all enabled sources:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Write Mode (Update vs Overwrite)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Save Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WriteMode.values().forEach { mode ->
                        val isSelected = uiState.settings.writeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onWriteModeSelected(mode) },
                            label = {
                                Text(
                                    text = mode.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("write_mode_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = uiState.settings.writeMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            // Action Buttons: Sync Now & Bulk Export
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportNow,
                    enabled = !uiState.isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_now_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Syncing all enabled sources...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sync Now to Google Drive",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onBulkExport,
                    enabled = !uiState.isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("bulk_export_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync Full History to Drive",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // Last Export Result Banner
            uiState.lastExportResult?.let { result ->
                Surface(
                    color = if (result.isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_export_result_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (result.isSuccess) "Sync Succeeded" else "Sync Notice",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        IconButton(onClick = onDismissExportResult, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Section 4: Sync Schedule & Frequency
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
                        text = "Automatically backs up active health and workout records to Drive",
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

        // Section 5: About Nalama Platform
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

        // Section 6: Advanced Options (Collapsible header at bottom)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("advanced_options_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancedOptionsExpanded = !isAdvancedOptionsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Advanced Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Diagnostics, tester logs, and cache controls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { isAdvancedOptionsExpanded = !isAdvancedOptionsExpanded }) {
                        Icon(
                            imageVector = if (isAdvancedOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isAdvancedOptionsExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                AnimatedVisibility(visible = isAdvancedOptionsExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!manualTokenVerifying) showManualTokenDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Manual OAuth Token", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a Google Drive OAuth access token (e.g. from Google OAuth 2.0 Playground or gcloud with scope https://www.googleapis.com/auth/drive.file) to immediately test Drive sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = { manualTokenInput = it },
                        label = { Text("Access Token (ya29...)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        enabled = !manualTokenVerifying
                    )
                    manualTokenFeedback?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("Success", ignoreCase = true)) Color(0xFF137333) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        manualTokenVerifying = true
                        manualTokenFeedback = "Verifying token with Google Drive API..."
                        onVerifyAndSaveOAuthToken(manualTokenInput) { success, msg ->
                            manualTokenVerifying = false
                            manualTokenFeedback = msg
                            if (success) {
                                showManualTokenDialog = false
                                Toast.makeText(context, "Google Drive Connected!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !manualTokenVerifying && manualTokenInput.isNotBlank()
                ) {
                    if (manualTokenVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verifying...")
                    } else {
                        Text("Verify & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualTokenDialog = false },
                    enabled = !manualTokenVerifying
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Export Progress Dialog - shows immediately right here in Settings when triggered!
    if (uiState.bulkExportState.isRunning || uiState.bulkExportState.isCompleted || uiState.bulkExportState.error != null) {
        val enabledSourcesText = buildList {
            if (uiState.settings.isHealthConnectEnabled) add("Health Connect")
            if (uiState.settings.isHevyEnabled) add("Hevy")
        }.joinToString(" & ").ifEmpty { "Enabled Sources" }

        BulkExportProgressDialog(
            bulkState = uiState.bulkExportState,
            sourceAppName = enabledSourcesText,
            onDismiss = onDismissBulkExportDialog
        )
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
