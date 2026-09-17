package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.health.HealthConnectManager
import com.example.model.SyncSourceApp
import com.example.ui.HealthSyncViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PayloadPreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HealthConnectSyncTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HealthSyncViewModel by viewModels()

    // Health Connect Permission Request Launcher
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.checkHealthConnectStatus()
        if (grantedPermissions.containsAll(HealthConnectManager.PERMISSIONS)) {
            Toast.makeText(this, "Health Connect permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Granted ${grantedPermissions.size} of ${HealthConnectManager.PERMISSIONS.size} permissions.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val requestPermissions: () -> Unit = {
        healthPermissionLauncher.launch(HealthConnectManager.PERMISSIONS)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HealthConnectSyncTheme {
                val uiState by viewModel.uiState.collectAsState()
                val history by viewModel.history.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_nalama_logo),
                                                contentDescription = "Nalama Peacock Tree Logo",
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Nalama",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "நலமா",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = if (uiState.settings.sourceApp == SyncSourceApp.HEVY) "• Gym" else "• Health",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Companion for nalama.family & nalama.ai.studio",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.refreshActiveData() },
                                    modifier = Modifier.testTag("app_bar_refresh_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .testTag("main_bottom_nav")
                        ) {
                            NavigationBarItem(
                                selected = uiState.activeTab == 0,
                                onClick = { viewModel.selectTab(0) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.activeTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                        contentDescription = "Dashboard"
                                    )
                                },
                                label = { Text("Dashboard") },
                                modifier = Modifier.testTag("nav_item_dashboard")
                            )
                            NavigationBarItem(
                                selected = uiState.activeTab == 1,
                                onClick = { viewModel.selectTab(1) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.activeTab == 1) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = "History"
                                    )
                                },
                                label = { Text("History") },
                                modifier = Modifier.testTag("nav_item_history")
                            )
                            NavigationBarItem(
                                selected = uiState.activeTab == 2,
                                onClick = { viewModel.selectTab(2) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.activeTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_item_settings")
                            )
                            NavigationBarItem(
                                selected = uiState.activeTab == 3,
                                onClick = { viewModel.selectTab(3) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.activeTab == 3) Icons.Filled.Code else Icons.Outlined.Code,
                                        contentDescription = "Payload"
                                    )
                                },
                                label = { Text("Payload") },
                                modifier = Modifier.testTag("nav_item_payload")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (uiState.activeTab) {
                        0 -> DashboardScreen(
                            uiState = uiState,
                            onRequestHealthPermissions = requestPermissions,
                            onSelectSourceApp = { viewModel.updateSourceApp(it) },
                            onFormatSelected = { viewModel.updateExportFormat(it) },
                            onWriteModeSelected = { viewModel.updateWriteMode(it) },
                            onFolderSelected = { viewModel.updateTargetFolder(it) },
                            onExportNow = { viewModel.exportNow() },
                            onRefreshHealthData = { viewModel.refreshActiveData() },
                            onOpenScheduleSettings = { viewModel.selectTab(2) },
                            onDismissExportResult = { viewModel.dismissExportResult() },
                            onViewPreview = { viewModel.selectTab(3) },
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> HistoryScreen(
                            history = history,
                            onClearHistory = { viewModel.clearHistory() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> SettingsScreen(
                            uiState = uiState,
                            onUpdateSourceApp = { viewModel.updateSourceApp(it) },
                            onUpdateHevyApiKey = { viewModel.updateHevyApiKey(it) },
                            onUpdateWebhookUrl = { viewModel.updateWebhookUrl(it) },
                            onTestWebhook = { viewModel.testWebhook(it) },
                            onDismissTestResult = { viewModel.dismissTestResult() },
                            onUpdateSyncInterval = { viewModel.updateSyncInterval(it) },
                            onUpdateAutoSync = { viewModel.updateAutoSync(it) },
                            onUpdateCustomFolderPath = { viewModel.updateCustomFolderPath(it) },
                            onUpdateTimezone = { viewModel.updateTimezone(it) },
                            onToggleDemoMode = { viewModel.toggleDemoMode(it) },
                            onRequestHealthPermissions = requestPermissions,
                            onOpenHealthConnectSettings = {
                                try {
                                    startActivity(viewModel.getHealthConnectSettingsIntent())
                                } catch (_: Exception) {
                                    Toast.makeText(this@MainActivity, "Unable to open Health Connect settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> PayloadPreviewScreen(
                            jsonPayload = uiState.previewJson,
                            csvPayload = uiState.previewCsv,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkHealthConnectStatus()
    }
}
