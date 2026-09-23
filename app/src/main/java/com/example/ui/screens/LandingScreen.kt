package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings

/**
 * Landing Page for Nalama Health Data Companion.
 * Explains the companion app's purpose as a sensor and workout bridge for the parent Nalama platform (nalama.family)
 * and connects Google Drive BYOS storage.
 */
@Composable
fun LandingScreen(
    settings: AppSettings,
    onRequestGoogleSignIn: () -> Unit = {},
    onSignInWithGoogle: (email: String, displayName: String) -> Unit,
    onSelectLanguage: (String) -> Unit = {},
    onTryOfflineDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPermissionNoticeExpanded by remember { mutableStateOf(false) }
    var showGoogleSignInDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("landing_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Main White Card
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .testTag("landing_main_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo
                    Image(
                        painter = painterResource(id = R.drawable.logo_nalama_hdc_white_bg),
                        contentDescription = "Nalama Health Data Companion Logo",
                        modifier = Modifier
                            .size(180.dp)
                            .testTag("landing_logo")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pill Badge: 100% Private Google Drive Storage
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF3EDF9),
                        border = BorderStroke(1.dp, Color(0xFFDECFF0)),
                        modifier = Modifier.testTag("landing_privacy_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Security Shield",
                                tint = Color(0xFF36245A),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "100% Private Google Drive Storage",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF36245A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title: Nalama Health Data Companion across two lines
                    Text(
                        text = "NALAMA",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "HEALTH DATA COMPANION",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Companion app designation badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Companion App for Nalama (nalama.family)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Purpose Statement
                    Text(
                        text = "Privately sync your Health Connect records and workouts directly to your personal Google Drive or Sheets.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Private storage • Your health data stays on your personal account.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sign in with Google Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRequestGoogleSignIn() }
                            .testTag("google_sign_in_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_g),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = { showGoogleSignInDialog = true },
                        modifier = Modifier.testTag("manual_account_config_button")
                    ) {
                        Text(
                            text = "Or enter account manually",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Expandable Permission Notice
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isPermissionNoticeExpanded = !isPermissionNoticeExpanded }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("permission_notice_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Privacy & Data Security",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (isPermissionNoticeExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isPermissionNoticeExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🔒 Private & Local First",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Nalama writes backup records only to your personal Google Drive and Sheets.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                    Text(
                                        text = "• No metric or workout data is transmitted to third-party databases.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                    Text(
                                        text = "• Health data reading operates entirely on this device.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Links: Privacy Policy & Terms of Service
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Privacy Policy",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showPrivacyDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("link_privacy_policy")
                        )
                        Text(
                            text = " • ",
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "Terms of Service",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showTermsDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("link_terms_service")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tester Offline Demo Link
                    Text(
                        text = "Are you a Tester? Try Offline Demo Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF36245A),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onTryOfflineDemo() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("landing_try_demo_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // "About this Companion App" Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF36245A),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "About this Companion App",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                // BYOS Explainer Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("landing_byos_card")
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDF9),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_drive),
                                    contentDescription = "Google Drive",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Bring Your Own Storage (BYOS)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This Android app functions strictly as a sensor & workout companion for the parent Nalama platform (nalama.family). It writes clean CSV files directly to your personal Google Drive, with zero intermediary databases.",
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Google Sign-In Confirmation Dialog
        if (showGoogleSignInDialog) {
            var inputEmail by remember {
                mutableStateOf(
                    if (settings.connectedEmail.isNotEmpty()) settings.connectedEmail else "karthickvijayc@gmail.com"
                )
            }
            var inputName by remember {
                mutableStateOf(
                    if (settings.connectedDisplayName.isNotEmpty()) settings.connectedDisplayName else "Karthick Vijay"
                )
            }

            AlertDialog(
                onDismissRequest = { showGoogleSignInDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_g),
                            contentDescription = "Google",
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Connect Google Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Connect your Google account to enable 100% private sync directly into your personal Google Drive (/nalama.family).",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_name_input")
                        )

                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("Google Account Email") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_email_input")
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Your credentials stay securely on your device.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showGoogleSignInDialog = false
                            val finalEmail = if (inputEmail.isBlank()) "karthickvijayc@gmail.com" else inputEmail.trim()
                            val finalName = if (inputName.isBlank()) "Karthick Vijay" else inputName.trim()
                            onSignInWithGoogle(finalEmail, finalName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("confirm_google_sign_in_button")
                    ) {
                        Text("Connect & Continue", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleSignInDialog = false }) {
                        Text(
                            "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        // Privacy Policy Dialog
        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyDialog = false },
                title = {
                    Text(
                        text = "Health Data Companion Privacy Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Zero-Knowledge & Bring Your Own Storage (BYOS):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF36245A)
                        )
                        Text(
                            text = "1. Data Ownership: All health metrics from Health Connect and Hevy gym logs are processed entirely on-device and exported solely to your designated Google Drive folder (/nalama.family).",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "2. No Central Servers: There are no company databases collecting, analyzing, or storing your biometrics.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "3. Complete Transparency: Data is stored as readable CSV files that you can inspect, delete, or export anytime.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyDialog = false }) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Terms of Service Dialog
        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = {
                    Text(
                        text = "Terms of Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Health Data Companion for Nalama:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF36245A)
                        )
                        Text(
                            text = "• This companion app functions as an on-device data bridge for the Nalama wellness platform (nalama.family).",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "• It syncs permission-approved Health Connect metrics and workout logs directly into your personal Google Drive or Sheets.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "• It is not a medical device and does not provide clinical diagnoses or replace medical advice.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "• You maintain 100% ownership and control over all exported files in your private storage.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF475569)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTermsDialog = false }) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
