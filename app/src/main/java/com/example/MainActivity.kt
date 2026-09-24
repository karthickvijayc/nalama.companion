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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.health.HealthConnectManager
import com.example.model.SyncSourceApp
import com.example.ui.AppScreen
import com.example.ui.HealthSyncViewModel
import com.example.ui.components.DiagnosticsDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LandingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
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

    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selectedEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            ?: result.data?.getStringExtra("authAccount")

        if (!selectedEmail.isNullOrBlank()) {
            val name = selectedEmail.substringBefore("@")
                .split(".", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            viewModel.connectGoogleAccount(selectedEmail, name)
            Toast.makeText(this, "Signed in as $selectedEmail", Toast.LENGTH_SHORT).show()
            com.example.util.AppLogger.s("AUTH", "Account chosen via system AccountPicker: $selectedEmail")
        } else if (result.data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                viewModel.onGoogleAccountConnected(account)
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                com.example.util.AppLogger.d("AUTH", "Account picker result note: ${e.message}")
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val fallbackEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            ?: result.data?.getStringExtra("authAccount")

        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.onGoogleAccountConnected(account)
            Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
        } catch (e: com.google.android.gms.common.api.ApiException) {
            com.example.util.AppLogger.e("AUTH", "Google Sign-In failed: code=${e.statusCode} ${e.message}")
            if (!fallbackEmail.isNullOrBlank()) {
                val name = fallbackEmail.substringBefore("@")
                    .split(".", "_", "-")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                viewModel.connectGoogleAccount(fallbackEmail, name)
                Toast.makeText(this, "Signed in as $fallbackEmail", Toast.LENGTH_SHORT).show()
            } else if (result.resultCode != RESULT_CANCELED) {
                Toast.makeText(this, "Google Sign-In failed (${e.statusCode}): ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            com.example.util.AppLogger.e("AUTH", "Google Sign-In error: ${e.message}")
            if (!fallbackEmail.isNullOrBlank()) {
                viewModel.connectGoogleAccount(fallbackEmail, fallbackEmail.substringBefore("@"))
                Toast.makeText(this, "Signed in as $fallbackEmail", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.refreshGoogleDriveToken()
            Toast.makeText(this, "Google Drive authorization granted!", Toast.LENGTH_SHORT).show()
        } else {
            com.example.util.AppLogger.w("AUTH", "Drive consent not granted or dismissed: ${result.resultCode}")
        }
    }

    private fun launchGoogleSignIn() {
        try {
            val intent = com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
                com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            com.example.util.AppLogger.w("AUTH", "AccountPicker fallback to AccountManager: ${e.message}")
            try {
                @Suppress("DEPRECATION")
                val intent = android.accounts.AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    false,
                    null,
                    null,
                    null,
                    null
                )
                accountPickerLauncher.launch(intent)
            } catch (e2: Exception) {
                com.example.util.AppLogger.e("AUTH", "Fallback to GoogleSignInClient: ${e2.message}")
                val client = com.example.auth.GoogleAuthHelper.getClient(this)
                googleSignInLauncher.launch(client.signInIntent)
            }
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
                val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.userConsentIntentEvent.collect { consentIntent ->
                        if (consentIntent != null) {
                            driveConsentLauncher.launch(consentIntent)
                            viewModel.consumeUserConsentIntent()
                        }
                    }
                }

                when (uiState.currentScreen) {
                    AppScreen.SPLASH -> {
                        SplashScreen(
                            onSplashFinished = { viewModel.onSplashFinished() }
                        )
                    }

                    AppScreen.LANDING -> {
                        LandingScreen(
                            settings = uiState.settings,
                            onRequestGoogleSignIn = { launchGoogleSignIn() },
                            onSignInWithGoogle = { email, name ->
                                viewModel.connectGoogleAccount(email, name)
                                Toast.makeText(this@MainActivity, "Connected Google Account: $email", Toast.LENGTH_SHORT).show()
                            },
                            onSelectLanguage = { viewModel.updateSelectedLanguage(it) },
                            onTryOfflineDemo = {
                                viewModel.enterDemoModeFromLanding()
                                Toast.makeText(this@MainActivity, "Entered Offline Demo Mode", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    AppScreen.MAIN -> {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White,
                                                shadowElevation = 1.dp,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(2.dp)
                                                ) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.logo_nalama_hdc_white_bg),
                                                        contentDescription = "Nalama Health Data Companion Logo",
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Nalama Health Companion",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = if (uiState.settings.sourceApp == SyncSourceApp.HEVY) "• Hevy" else "• Health Connect",
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = "Companion for Nalama (nalama.family)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    },
                                    actions = {
                                        // Tester Diagnostics Quick Button
                                        IconButton(
                                            onClick = { viewModel.openDiagnosticsDialog() },
                                            modifier = Modifier.testTag("app_bar_diagnostics_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BugReport,
                                                contentDescription = "Diagnostics & Tester Tools",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
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
                                        .testTag("main_bottom_nav"),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 3.dp
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
                                }
                            }
                        ) { innerPadding ->
                            when (uiState.activeTab) {
                                0 -> DashboardScreen(
                                    uiState = uiState,
                                    onRequestHealthPermissions = requestPermissions,
                                    onRequestGoogleSignIn = { launchGoogleSignIn() },
                                    onSelectSourceApp = { viewModel.updateSourceApp(it) },
                                    onWriteModeSelected = { viewModel.updateWriteMode(it) },
                                    onFolderSelected = { viewModel.updateTargetFolder(it) },
                                    onExportNow = { viewModel.exportNow() },
                                    onBulkExport = { viewModel.startBulkExport(365) },
                                    onRefreshHealthData = { viewModel.refreshActiveData() },
                                    onOpenScheduleSettings = { viewModel.selectTab(2) },
                                    onDismissExportResult = { viewModel.dismissExportResult() },
                                    onDismissInitialBulkBanner = { viewModel.dismissInitialBulkExportPrompt() },
                                    onDismissBulkExportDialog = { viewModel.dismissBulkExportDialog() },
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
                                    onTestDriveConnection = { viewModel.testDriveConnection() },
                                    onDismissTestResult = { viewModel.dismissTestResult() },
                                    onUpdateSyncInterval = { viewModel.updateSyncInterval(it) },
                                    onUpdateAutoSync = { viewModel.updateAutoSync(it) },
                                    onUpdateCustomFolderPath = { viewModel.updateCustomFolderPath(it) },
                                    onUpdateTimezone = { viewModel.updateTimezone(it) },
                                    onToggleDemoMode = { viewModel.toggleDemoMode(it) },
                                    onRequestHealthPermissions = requestPermissions,
                                    onRequestGoogleSignIn = { launchGoogleSignIn() },
                                    onOpenHealthConnectSettings = {
                                        try {
                                            startActivity(viewModel.getHealthConnectSettingsIntent())
                                        } catch (_: Exception) {
                                            Toast.makeText(this@MainActivity, "Unable to open Health Connect settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onNavigateToLanding = { viewModel.navigateTo(AppScreen.LANDING) },
                                    onDisconnectGoogle = { viewModel.disconnectGoogleAccount() },
                                    onBulkExport = { viewModel.startBulkExport(365) },
                                    onOpenDiagnostics = { viewModel.openDiagnosticsDialog() },
                                    onClearCache = { viewModel.clearSyncCache() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }

                // Tester Diagnostics Dialog
                if (uiState.showDiagnosticsDialog) {
                    DiagnosticsDialog(
                        logs = diagnosticLogs,
                        cacheSummary = uiState.cacheSummary,
                        isDiagnosingDrive = uiState.isDiagnosingDrive,
                        diagnosticTestResult = uiState.diagnosticTestResult,
                        diagnosticReportText = viewModel.getDiagnosticReport(),
                        onRunDriveDiagnostic = { viewModel.runDriveDiagnostic() },
                        onClearCache = { viewModel.clearSyncCache() },
                        onClearLogs = { viewModel.clearDiagnosticLogs() },
                        onDismiss = { viewModel.dismissDiagnosticsDialog() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkHealthConnectStatus()
    }
}
