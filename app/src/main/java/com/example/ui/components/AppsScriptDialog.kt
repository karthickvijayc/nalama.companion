package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.GoogleAppsScriptWebhookClient

@Composable
fun AppsScriptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(0) } // 0: myFunction snippet, 1: full script
    var hasAutoCopied by remember { mutableStateOf(false) }
    var showRawCode by remember { mutableStateOf(false) }

    val activeCode = if (selectedMode == 0) {
        GoogleAppsScriptWebhookClient.MY_FUNCTION_APPS_SCRIPT_CONTENT
    } else {
        GoogleAppsScriptWebhookClient.FULL_APPS_SCRIPT_CODE
    }

    fun copyCodeToClipboard(showToastNotification: Boolean = true, mode: Int = selectedMode) {
        val codeToCopy = if (mode == 0) {
            GoogleAppsScriptWebhookClient.MY_FUNCTION_APPS_SCRIPT_CONTENT
        } else {
            GoogleAppsScriptWebhookClient.FULL_APPS_SCRIPT_CODE
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Nalama Google Apps Script", codeToCopy)
        clipboard.setPrimaryClip(clip)
        hasAutoCopied = true
        if (showToastNotification) {
            val msg = if (mode == 0) {
                "Function content copied! Ready to paste inside myFunction() { }"
            } else {
                "Full Apps Script copied to clipboard!"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("apps_script_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "One-Click Webhook Creator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automate Google Sheets & Drive Bridge",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Paste Mode Selector
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Choose Paste Format:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedMode == 0,
                                    onClick = {
                                        selectedMode = 0
                                        copyCodeToClipboard(showToastNotification = true, mode = 0)
                                    },
                                    label = { Text("Inside myFunction()", fontSize = 11.sp) },
                                    leadingIcon = if (selectedMode == 0) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = selectedMode == 1,
                                    onClick = {
                                        selectedMode = 1
                                        copyCodeToClipboard(showToastNotification = true, mode = 1)
                                    },
                                    label = { Text("Full Script", fontSize = 11.sp) },
                                    leadingIcon = if (selectedMode == 1) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // ONE-CLICK PRIMARY ACTION CARD
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "1-Click Launch & Auto-Copy",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Text(
                                text = if (selectedMode == 0) {
                                    "Optimized for Google's default myFunction(): Copies clean function code with fixed spacing and opens script.new in your browser. Just tap between { } and paste!"
                                } else {
                                    "Copies the full standalone script and opens script.new in your browser. Select-all and replace the file."
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    copyCodeToClipboard(showToastNotification = false, mode = selectedMode)
                                    val scriptUrl = "https://script.new"
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(scriptUrl))
                                    context.startActivity(browserIntent)
                                    val notifyMsg = if (selectedMode == 0) {
                                        "Function content copied! Tap inside myFunction() { } and paste."
                                    } else {
                                        "Script copied to clipboard! Opening script.new..."
                                    }
                                    Toast.makeText(context, notifyMsg, Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("one_click_create_script_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch Google Script Studio", fontWeight = FontWeight.SemiBold)
                            }

                            if (hasAutoCopied) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF137333),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (selectedMode == 0) {
                                            "Function content ready in clipboard (formatted for myFunction)!"
                                        } else {
                                            "Full script ready in clipboard!"
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFF137333),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Guided 2-Step Activation Guide
                    Text(
                        text = "Quick 2-Step Activation:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("①", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (selectedMode == 0) {
                                        "In script.new, tap inside { } on line 2 in the default function myFunction() and Paste (📋). Press Save (💾)."
                                    } else {
                                        "In script.new, select all text in Code.gs and Paste (Ctrl+V / Long-press). Press Save (💾)."
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("②", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "Click Deploy → New deployment → Web app:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("• Execute as: Me\n• Who has access: Anyone", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("• Copy Web App URL into Nalama Settings.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    // Code Viewer Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showRawCode = !showRawCode },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (showRawCode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showRawCode) "Hide Script Source" else "View Script Source",
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { copyCodeToClipboard(showToastNotification = true) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("copy_script_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-copy Code", fontSize = 11.sp)
                        }
                    }

                    if (showRawCode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E24))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = activeCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFD4D4D8),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
