package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.drive.CacheSummary
import com.example.drive.DriveDiagnosticTestResult
import com.example.util.AppLogger
import com.example.util.LogEntry
import com.example.util.LogLevel

@Composable
fun DiagnosticsDialog(
    logs: List<LogEntry>,
    cacheSummary: CacheSummary,
    isDiagnosingDrive: Boolean,
    diagnosticTestResult: DriveDiagnosticTestResult?,
    diagnosticReportText: String,
    onRunDriveDiagnostic: () -> Unit,
    onClearCache: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("ALL", "DRIVE", "WORKER", "HEVY", "HEALTH", "CACHE", "ERRORS")

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "ALL" -> logs
            "DRIVE" -> logs.filter { it.tag.equals("DRIVE", ignoreCase = true) }
            "WORKER" -> logs.filter { it.tag.equals("WORKER", ignoreCase = true) }
            "HEVY" -> logs.filter { it.tag.equals("HEVY", ignoreCase = true) }
            "HEALTH" -> logs.filter { it.tag.equals("HEALTH_CONNECT", ignoreCase = true) || it.tag.equals("HEALTH", ignoreCase = true) }
            "CACHE" -> logs.filter { it.tag.equals("CACHE", ignoreCase = true) }
            "ERRORS" -> logs.filter { it.level == LogLevel.ERROR || it.level == LogLevel.WARN }
            else -> logs
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("diagnostics_dialog_root")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Diagnostics & Tester Tools",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${logs.size} events recorded • Cache: ${cacheSummary.fileCount} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Signature & OAuth Client Credentials Card
                val runtimePkg = remember { com.example.util.CertificateHelper.getPackageName(context) }
                val runtimeSha1 = remember { com.example.util.CertificateHelper.getSigningSha1(context) }
                val runtimeSha256 = remember { com.example.util.CertificateHelper.getSigningSha256(context) }
                var showSignatureDetails by remember { mutableStateOf(false) }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("OAuth 2.0 Credentials (Play Services)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                            IconButton(onClick = { showSignatureDetails = !showSignatureDetails }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (showSignatureDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Package: $runtimePkg",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    val copyText = "Package Name: $runtimePkg\nSigning SHA-1: $runtimeSha1\nSigning SHA-256: $runtimeSha256"
                                    clipboardManager.setText(AnnotatedString(copyText))
                                    Toast.makeText(context, "Copied OAuth client info to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy OAuth Credentials", modifier = Modifier.size(14.dp))
                            }
                        }

                        if (showSignatureDetails) {
                            Text(
                                text = "SHA-1: $runtimeSha1",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "SHA-256: $runtimeSha256",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Register this Package Name and SHA-1 as an Android OAuth Client ID in Google Cloud Console.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Share Report Button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Nalama Health Sync Diagnostic Report")
                                putExtra(Intent.EXTRA_TEXT, diagnosticReportText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Diagnostic Report"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("diag_share_report_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Logs", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Copy Logs Button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(diagnosticReportText))
                            Toast.makeText(context, "Copied diagnostic report to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("diag_copy_logs_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.5.sp)
                    }

                    // Run Diagnostic Ping Test
                    OutlinedButton(
                        onClick = onRunDriveDiagnostic,
                        enabled = !isDiagnosingDrive,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.3f).testTag("diag_test_drive_btn")
                    ) {
                        if (isDiagnosingDrive) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Testing...", fontSize = 11.5.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Drive API", fontSize = 11.5.sp)
                        }
                    }

                    // Clear Cache Button
                    OutlinedButton(
                        onClick = {
                            onClearCache()
                            Toast.makeText(context, "Sync cache cleared! Next sync will recreate files on Drive.", Toast.LENGTH_LONG).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.2f).testTag("diag_clear_cache_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Cache", fontSize = 11.5.sp)
                    }
                }

                // Diagnostic Result Banner (if run)
                diagnosticTestResult?.let { result ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (result.isSuccess) Color(0xFFE6F4EA) else Color(0xFFFCE8E6),
                        border = BorderStroke(1.dp, if (result.isSuccess) Color(0xFFCEEAD6) else Color(0xFFFAD2CF)),
                        modifier = Modifier.fillMaxWidth().testTag("diag_result_banner")
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (result.isSuccess) "Live Drive API Diagnostic PASSED" else "Live Drive Diagnostic FAILED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F)
                                )
                            }
                            Text(
                                text = result.message,
                                fontSize = 11.sp,
                                color = if (result.isSuccess) Color(0xFF137333) else Color(0xFFC5221F)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips Row
                ScrollableTabRow(
                    selectedTabIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filters.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Log List View
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B), // Dark Slate terminal background
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No diagnostic events recorded for filter '$selectedFilter'",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            reverseLayout = true
                        ) {
                            items(filteredLogs.reversed(), key = { it.id }) { log ->
                                LogRow(log)
                            }
                        }
                    }
                }

                // Footer Bar
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClearLogs) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs", fontSize = 11.5.sp)
                    }

                    Text(
                        text = "Auto-refreshes on export",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (log.level) {
        LogLevel.ERROR -> Color(0xFFF87171) // Light Red
        LogLevel.WARN -> Color(0xFFFBBF24)  // Amber
        LogLevel.SUCCESS -> Color(0xFF34D399) // Emerald Green
        LogLevel.DEBUG -> Color(0xFF94A3B8) // Muted Slate
        LogLevel.INFO -> Color(0xFF38BDF8)  // Sky Blue
    }

    val tagColor = when (log.tag.uppercase()) {
        "DRIVE" -> Color(0xFF60A5FA)
        "WORKER" -> Color(0xFFA78BFA)
        "CACHE" -> Color(0xFFF472B6)
        "HEVY" -> Color(0xFFF97316)
        "HEALTH", "HEALTH_CONNECT" -> Color(0xFF10B981)
        else -> Color(0xFFCBD5E1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
            .clickable { if (!log.details.isNullOrBlank()) expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = log.formattedTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B)
            )

            Text(
                text = "[${log.tag}]",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tagColor
            )

            Text(
                text = log.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = levelColor,
                modifier = Modifier.weight(1f)
            )

            if (!log.details.isNullOrBlank()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand details",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (expanded && !log.details.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF020617),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = log.details,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
