package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
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

@Composable
fun PayloadPreviewScreen(
    jsonPayload: String,
    csvPayload: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedView by remember { mutableIntStateOf(0) } // 0: JSON, 1: CSV

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Payload Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Exact data sent to your Google Apps Script webhook",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val textToCopy = if (selectedView == 0) jsonPayload else csvPayload
                    val label = if (selectedView == 0) "Health Data JSON" else "Health Data CSV"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, textToCopy))
                    Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("copy_payload_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy", fontSize = 12.sp)
            }
        }

        // Tab Selector (JSON vs CSV)
        TabRow(selectedTabIndex = selectedView) {
            Tab(
                selected = selectedView == 0,
                onClick = { selectedView = 0 },
                text = { Text("JSON Payload (Schema)") },
                modifier = Modifier.testTag("tab_json_preview")
            )
            Tab(
                selected = selectedView == 1,
                onClick = { selectedView = 1 },
                text = { Text("CSV Output (Default)") },
                modifier = Modifier.testTag("tab_csv_preview")
            )
        }

        // Code display area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E24))
                .padding(12.dp)
        ) {
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()

            Text(
                text = if (selectedView == 0) jsonPayload else csvPayload,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (selectedView == 0) Color(0xFF6EE7B7) else Color(0xFF93C5FD),
                lineHeight = 16.sp,
                modifier = Modifier
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
            )
        }
    }
}
