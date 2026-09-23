package com.example.ui.components

import android.content.ClipDescription
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppSettings

@Composable
fun GoogleDriveAuthDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onVerifyAndSaveToken: (token: String, onComplete: (Boolean, String) -> Unit) -> Unit,
    onClearToken: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    var tokenInput by remember { mutableStateOf(settings.googleOAuthAccessToken) }
    var emailInput by remember { mutableStateOf(settings.connectedEmail.ifBlank { "karthickvijayc@gmail.com" }) }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp)
                .testTag("google_drive_auth_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Google Drive Authorization",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Required to upload CSV files to Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                // Current Status Card
                val hasToken = settings.googleOAuthAccessToken.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasToken) Color(0xFFE6F4EA) else Color(0xFFFEF7E0),
                    border = BorderStroke(1.dp, if (hasToken) Color(0xFFCEEAD6) else Color(0xFFFEEFC3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (hasToken) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasToken) Color(0xFF137333) else Color(0xFFB06000),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasToken) "Drive Authorized & Connected" else "Authorization Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (hasToken) Color(0xFF137333) else Color(0xFFB06000)
                            )
                            Text(
                                text = if (hasToken) {
                                    "Connected as ${settings.connectedEmail.ifBlank { "Google Account" }}"
                                } else {
                                    "Files are currently saving locally on device only until authorized."
                                },
                                fontSize = 11.5.sp,
                                color = if (hasToken) Color(0xFF137333) else Color(0xFF8A4900)
                            )
                        }
                    }
                }

                // Email Field
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Google Account Email") },
                    placeholder = { Text("your.email@gmail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_dialog_email_input"),
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )

                // Access Token Input
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        verificationMessage = null
                    },
                    label = { Text("Google OAuth Access Token") },
                    placeholder = { Text("ya29.a0...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_dialog_token_input"),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tokenInput.isNotBlank()) {
                                IconButton(onClick = {
                                    tokenInput = ""
                                    verificationMessage = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()
                                if (clip != null) {
                                    tokenInput = clip.text.trim()
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    },
                    supportingText = {
                        Text(
                            text = if (tokenInput.isNotBlank()) "Token length: ${tokenInput.length} chars" else "Paste bearer access token with drive.file scope",
                            fontSize = 11.sp
                        )
                    }
                )

                // Verification Result Message
                verificationMessage?.let { (success, msg) ->
                    Surface(
                        color = if (success) Color(0xFFE6F4EA) else Color(0xFFFCE8E6),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF137333) else Color(0xFFC5221F),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = msg,
                                color = if (success) Color(0xFF137333) else Color(0xFFC5221F),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Helper Guide Card for Testers
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How Testers Can Generate a Quick Token:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. Open Google OAuth Playground\n2. Select scope 'Google Drive API v3 > https://www.googleapis.com/auth/drive.file'\n3. Authorize APIs & exchange authorization code for tokens\n4. Copy the Access token and paste it here",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://developers.google.com/oauthplayground/#step1&scopes=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file")
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open OAuth Playground in Browser", fontSize = 11.5.sp)
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasToken) {
                        OutlinedButton(
                            onClick = {
                                onClearToken()
                                tokenInput = ""
                                verificationMessage = Pair(false, "Token removed. Exports will be stored locally.")
                                Toast.makeText(context, "Google Drive token removed", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("auth_dialog_remove_token_btn")
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (tokenInput.isBlank()) {
                                verificationMessage = Pair(false, "Please paste or enter an access token first.")
                                return@Button
                            }
                            isVerifying = true
                            verificationMessage = null
                            onVerifyAndSaveToken(tokenInput.trim()) { success, message ->
                                isVerifying = false
                                verificationMessage = Pair(success, message)
                                if (success) {
                                    Toast.makeText(context, "Google Drive connected successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isVerifying,
                        modifier = Modifier.weight(if (hasToken) 1.5f else 1f).testTag("auth_dialog_verify_save_btn")
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify & Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
