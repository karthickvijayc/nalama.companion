package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel {
    DEBUG, INFO, SUCCESS, WARN, ERROR
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    fun toFormattedString(): String {
        val lvl = when (level) {
            LogLevel.DEBUG -> "[DEBUG]"
            LogLevel.INFO -> "[INFO ]"
            LogLevel.SUCCESS -> "[OK   ]"
            LogLevel.WARN -> "[WARN ]"
            LogLevel.ERROR -> "[ERROR]"
        }
        val detailStr = if (!details.isNullOrBlank()) " | $details" else ""
        return "$formattedTime $lvl [$tag] $message$detailStr"
    }
}

object AppLogger {

    private const val MAX_LOGS = 300
    private const val LOG_FILE_NAME = "diagnostic_events.log"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }
        logFile = File(logDir, LOG_FILE_NAME)
        loadLogsFromFile()
        i("APP", "Diagnostic logging initialized on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) - ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    @Synchronized
    private fun loadLogsFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val lines = file.readLines().takeLast(MAX_LOGS)
            val list = mutableListOf<LogEntry>()
            for (line in lines) {
                // Parse simple formatted line
                val level = when {
                    line.contains("[ERROR]") -> LogLevel.ERROR
                    line.contains("[WARN ]") -> LogLevel.WARN
                    line.contains("[OK   ]") -> LogLevel.SUCCESS
                    line.contains("[DEBUG]") -> LogLevel.DEBUG
                    else -> LogLevel.INFO
                }
                list.add(
                    LogEntry(
                        formattedTime = line.take(12).trim(),
                        level = level,
                        tag = extractTag(line),
                        message = extractMessage(line)
                    )
                )
            }
            _logs.value = list
        } catch (_: Exception) {}
    }

    private fun extractTag(line: String): String {
        val start = line.indexOf('[', 13)
        val end = line.indexOf(']', start + 1)
        return if (start != -1 && end != -1) line.substring(start + 1, end) else "APP"
    }

    private fun extractMessage(line: String): String {
        val tagEnd = line.indexOf(']', 14)
        return if (tagEnd != -1 && tagEnd + 2 < line.length) line.substring(tagEnd + 2) else line
    }

    fun d(tag: String, message: String, details: String? = null) = log(LogLevel.DEBUG, tag, message, details)
    fun i(tag: String, message: String, details: String? = null) = log(LogLevel.INFO, tag, message, details)
    fun s(tag: String, message: String, details: String? = null) = log(LogLevel.SUCCESS, tag, message, details)
    fun w(tag: String, message: String, details: String? = null) = log(LogLevel.WARN, tag, message, details)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val details = throwable?.let { "${it.javaClass.simpleName}: ${it.message}\n${it.stackTraceToString().take(500)}" }
        log(LogLevel.ERROR, tag, message, details)
    }

    private fun log(level: LogLevel, tag: String, message: String, details: String? = null) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            timestamp = now,
            formattedTime = timeFormat.format(Date(now)),
            level = level,
            tag = tag,
            message = message,
            details = details
        )

        // Android logcat mirroring
        when (level) {
            LogLevel.DEBUG -> Log.d("Nalama_$tag", message)
            LogLevel.INFO, LogLevel.SUCCESS -> Log.i("Nalama_$tag", message)
            LogLevel.WARN -> Log.w("Nalama_$tag", message)
            LogLevel.ERROR -> Log.e("Nalama_$tag", "$message ${details ?: ""}")
        }

        // Memory StateFlow update
        val current = _logs.value.toMutableList()
        current.add(entry)
        val trimmed = if (current.size > MAX_LOGS) current.subList(current.size - MAX_LOGS, current.size) else current
        _logs.value = trimmed

        // File persistence asynchronously
        scope.launch {
            try {
                logFile?.appendText("${entry.toFormattedString()}\n")
            } catch (_: Exception) {}
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        scope.launch {
            try {
                logFile?.delete()
            } catch (_: Exception) {}
        }
        i("APP", "Diagnostic logs cleared by tester/user")
    }

    fun generateDiagnosticReport(
        connectedEmail: String,
        targetFolder: String,
        hasToken: Boolean,
        isDemoMode: Boolean,
        cacheSummary: com.example.drive.CacheSummary,
        oauthClientInfo: String = ""
    ): String {
        val syncStatus = """
            • Connected Account: ${if (connectedEmail.isNotBlank()) connectedEmail else "(None)"}
            • Target Folder: $targetFolder
            • Drive Token Configured: $hasToken
            • Demo/Offline Mode: $isDemoMode
            • Local Cache Indexed Files: ${cacheSummary.fileCount}
            • Local Cache Indexed Folders: ${cacheSummary.folderCount}
            • Local Cache Total Size: ${cacheSummary.totalSizeBytes / 1024} KB
        """.trimIndent()
        return generateDiagnosticReport(oauthClientInfo, syncStatus)
    }

    fun generateDiagnosticReport(
        deviceInfoExtra: String = "",
        syncStatusExtra: String = ""
    ): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("NALAMA HEALTH DATA COMPANION - DIAGNOSTIC REPORT")
        sb.appendLine("Generated: ${fullDateFormat.format(Date())}")
        sb.appendLine("==========================================")
        sb.appendLine("DEVICE INFORMATION:")
        sb.appendLine("• Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("• Android Version: ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})")
        sb.appendLine("• Build Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine()
        if (deviceInfoExtra.isNotBlank()) {
            sb.appendLine("OAUTH 2.0 REGISTRATION CREDENTIALS:")
            sb.appendLine(deviceInfoExtra.trim())
            sb.appendLine()
        }
        if (syncStatusExtra.isNotBlank()) {
            sb.appendLine("SYNC & STORAGE STATUS:")
            sb.appendLine(syncStatusExtra.trim())
            sb.appendLine()
        }
        sb.appendLine("RECENT DIAGNOSTIC EVENTS (last ${_logs.value.size} items):")
        sb.appendLine("------------------------------------------")
        if (_logs.value.isEmpty()) {
            sb.appendLine("(No log events recorded yet)")
        } else {
            for (entry in _logs.value) {
                sb.appendLine(entry.toFormattedString())
            }
        }
        sb.appendLine("==========================================")
        sb.appendLine("END OF DIAGNOSTIC REPORT")
        return sb.toString()
    }
}
