package com.example.network

import com.example.model.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WebhookResult(
    val isSuccess: Boolean,
    val httpCode: Int?,
    val message: String,
    val durationMs: Long,
    val responseBody: String? = null
)

class GoogleAppsScriptWebhookClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Clean function-body snippet designed to paste directly inside Google's default:
         * function myFunction() {
         *   [PASTE HERE]
         * }
         *
         * It delegates myFunction to handleWebhook, defines doPost(e) and doGet(e),
         * and completes smoothly with zero syntax errors.
         * Formatted with strict 2-space indentation and UNIX newlines (\n) to prevent
         * mobile auto-indent expansion and line-wrap spacing issues.
         */
        val MY_FUNCTION_APPS_SCRIPT_CONTENT = listOf(
            "  return handleWebhook(arguments[0]);",
            "}",
            "",
            "function doPost(e) {",
            "  return handleWebhook(e);",
            "}",
            "",
            "function doGet(e) {",
            "  return ContentService.createTextOutput(JSON.stringify({ status: \"active\", message: \"Nalama Webhook is running\" }))",
            "    .setMimeType(ContentService.MimeType.JSON);",
            "}",
            "",
            "function handleWebhook(e) {",
            "  try {",
            "    if (!e || !e.postData || !e.postData.contents) {",
            "      return ContentService.createTextOutput(JSON.stringify({ status: \"error\", message: \"No post data received\" }))",
            "        .setMimeType(ContentService.MimeType.JSON);",
            "    }",
            "",
            "    var payload = JSON.parse(e.postData.contents);",
            "    var sourceApp = payload.sourceApp || \"HealthConnect\";",
            "    var folderPath = payload.folderPath || (sourceApp === \"Hevy\" ? \"nalama.family/imports/gym_workouts\" : \"nalama.family/imports/health_data\");",
            "    var format = payload.format || \"csv\";",
            "    var writeMode = payload.writeMode || \"append\";",
            "    var fileName = payload.fileName || (sourceApp === \"Hevy\" ? \"hevy_workouts\" : \"biometrics_daily\");",
            "",
            "    var folder = DriveApp.getRootFolder();",
            "    var parts = folderPath.split(\"/\");",
            "    for (var i = 0; i < parts.length; i++) {",
            "      var name = parts[i].trim();",
            "      if (!name) continue;",
            "      var sub = folder.getFoldersByName(name);",
            "      folder = sub.hasNext() ? sub.next() : folder.createFolder(name);",
            "    }",
            "",
            "    var filesUpdated = [];",
            "",
            "    if (format === \"csv\" || format === \"both\") {",
            "      var csvName = fileName + \".csv\";",
            "      var csvFiles = folder.getFilesByName(csvName);",
            "      if (csvFiles.hasNext() && writeMode === \"append\") {",
            "        var cf = csvFiles.next();",
            "        var lines = (payload.csvData || \"\").trim().split(\"\\n\");",
            "        var toAppend = lines.slice(1).join(\"\\n\");",
            "        if (toAppend.length > 0) {",
            "          cf.setContent(cf.getBlob().getDataAsString() + \"\\n\" + toAppend);",
            "        }",
            "        filesUpdated.push(csvName + \" (appended)\");",
            "      } else if (csvFiles.hasNext()) {",
            "        csvFiles.next().setContent(payload.csvData || \"\");",
            "        filesUpdated.push(csvName + \" (overwritten)\");",
            "      } else {",
            "        folder.createFile(csvName, payload.csvData || \"\", MimeType.CSV);",
            "        filesUpdated.push(csvName + \" (created)\");",
            "      }",
            "    }",
            "",
            "    if (format === \"json\" || format === \"both\") {",
            "      var jsonName = fileName + \".json\";",
            "      var jsonFiles = folder.getFilesByName(jsonName);",
            "      var jsonStr = typeof payload.jsonData === \"string\" ? payload.jsonData : JSON.stringify(payload.jsonData, null, 2);",
            "",
            "      if (jsonFiles.hasNext() && writeMode === \"append\") {",
            "        var jf = jsonFiles.next();",
            "        try {",
            "          var old = JSON.parse(jf.getBlob().getDataAsString());",
            "          if (sourceApp === \"Hevy\" && old.workouts && payload.jsonData && payload.jsonData.workouts) {",
            "            var existingIds = new Set(old.workouts.map(function(w) { return w.workoutId; }));",
            "            payload.jsonData.workouts.forEach(function(w) {",
            "              if (!existingIds.has(w.workoutId)) old.workouts.push(w);",
            "            });",
            "            old.syncedAt = payload.syncedAt || new Date().toISOString();",
            "            jf.setContent(JSON.stringify(old, null, 2));",
            "          } else if (old.dailyRecords && payload.jsonData && payload.jsonData.dailyRecords) {",
            "            var existingDates = new Set(old.dailyRecords.map(function(r) { return r.date; }));",
            "            payload.jsonData.dailyRecords.forEach(function(rec) {",
            "              if (!existingDates.has(rec.date)) old.dailyRecords.push(rec);",
            "            });",
            "            old.exportedAt = payload.exportedAt;",
            "            jf.setContent(JSON.stringify(old, null, 2));",
            "          } else {",
            "            jf.setContent(jsonStr);",
            "          }",
            "        } catch (err) {",
            "          jf.setContent(jsonStr);",
            "        }",
            "        filesUpdated.push(jsonName + \" (appended)\");",
            "      } else if (jsonFiles.hasNext()) {",
            "        jsonFiles.next().setContent(jsonStr);",
            "        filesUpdated.push(jsonName + \" (overwritten)\");",
            "      } else {",
            "        folder.createFile(jsonName, jsonStr, MimeType.PLAIN_TEXT);",
            "        filesUpdated.push(jsonName + \" (created)\");",
            "      }",
            "    }",
            "",
            "    return ContentService.createTextOutput(JSON.stringify({",
            "      status: \"success\",",
            "      sourceApp: sourceApp,",
            "      message: \"Export processed successfully\",",
            "      folder: folderPath,",
            "      files: filesUpdated,",
            "      recordsCount: payload.recordsCount || 1",
            "    })).setMimeType(ContentService.MimeType.JSON);",
            "",
            "  } catch (err) {",
            "    return ContentService.createTextOutput(JSON.stringify({",
            "      status: \"error\",",
            "      message: err.toString()",
            "    })).setMimeType(ContentService.MimeType.JSON);",
            "  }"
        ).joinToString("\n")

        /**
         * Full standalone Code.gs script for users who prefer to clear the entire editor.
         */
        val FULL_APPS_SCRIPT_CODE = listOf(
            "/**",
            " * Google Apps Script Webhook Handler for Health Connect & Hevy Gym Sync",
            " * Target Folder: My Drive / nalama.family / imports / ...",
            " */",
            "function doPost(e) {",
            "  return handleWebhook(e);",
            "}",
            "",
            "function doGet(e) {",
            "  return ContentService.createTextOutput(JSON.stringify({ status: \"active\", message: \"Nalama Webhook is running\" }))",
            "    .setMimeType(ContentService.MimeType.JSON);",
            "}",
            "",
            "function handleWebhook(e) {",
            "  try {",
            "    if (!e || !e.postData || !e.postData.contents) {",
            "      return ContentService.createTextOutput(JSON.stringify({ status: \"error\", message: \"No post data received\" }))",
            "        .setMimeType(ContentService.MimeType.JSON);",
            "    }",
            "",
            "    var payload = JSON.parse(e.postData.contents);",
            "    var sourceApp = payload.sourceApp || \"HealthConnect\";",
            "    var folderPath = payload.folderPath || (sourceApp === \"Hevy\" ? \"nalama.family/imports/gym_workouts\" : \"nalama.family/imports/health_data\");",
            "    var format = payload.format || \"csv\";",
            "    var writeMode = payload.writeMode || \"append\";",
            "    var fileName = payload.fileName || (sourceApp === \"Hevy\" ? \"hevy_workouts\" : \"biometrics_daily\");",
            "",
            "    var folder = DriveApp.getRootFolder();",
            "    var parts = folderPath.split(\"/\");",
            "    for (var i = 0; i < parts.length; i++) {",
            "      var name = parts[i].trim();",
            "      if (!name) continue;",
            "      var sub = folder.getFoldersByName(name);",
            "      folder = sub.hasNext() ? sub.next() : folder.createFolder(name);",
            "    }",
            "",
            "    var filesUpdated = [];",
            "",
            "    if (format === \"csv\" || format === \"both\") {",
            "      var csvName = fileName + \".csv\";",
            "      var csvFiles = folder.getFilesByName(csvName);",
            "      if (csvFiles.hasNext() && writeMode === \"append\") {",
            "        var cf = csvFiles.next();",
            "        var lines = (payload.csvData || \"\").trim().split(\"\\n\");",
            "        var toAppend = lines.slice(1).join(\"\\n\");",
            "        if (toAppend.length > 0) {",
            "          cf.setContent(cf.getBlob().getDataAsString() + \"\\n\" + toAppend);",
            "        }",
            "        filesUpdated.push(csvName + \" (appended)\");",
            "      } else if (csvFiles.hasNext()) {",
            "        csvFiles.next().setContent(payload.csvData || \"\");",
            "        filesUpdated.push(csvName + \" (overwritten)\");",
            "      } else {",
            "        folder.createFile(csvName, payload.csvData || \"\", MimeType.CSV);",
            "        filesUpdated.push(csvName + \" (created)\");",
            "      }",
            "    }",
            "",
            "    if (format === \"json\" || format === \"both\") {",
            "      var jsonName = fileName + \".json\";",
            "      var jsonFiles = folder.getFilesByName(jsonName);",
            "      var jsonStr = typeof payload.jsonData === \"string\" ? payload.jsonData : JSON.stringify(payload.jsonData, null, 2);",
            "",
            "      if (jsonFiles.hasNext() && writeMode === \"append\") {",
            "        var jf = jsonFiles.next();",
            "        try {",
            "          var old = JSON.parse(jf.getBlob().getDataAsString());",
            "          if (sourceApp === \"Hevy\" && old.workouts && payload.jsonData && payload.jsonData.workouts) {",
            "            var existingIds = new Set(old.workouts.map(function(w) { return w.workoutId; }));",
            "            payload.jsonData.workouts.forEach(function(w) {",
            "              if (!existingIds.has(w.workoutId)) old.workouts.push(w);",
            "            });",
            "            old.syncedAt = payload.syncedAt || new Date().toISOString();",
            "            jf.setContent(JSON.stringify(old, null, 2));",
            "          } else if (old.dailyRecords && payload.jsonData && payload.jsonData.dailyRecords) {",
            "            var existingDates = new Set(old.dailyRecords.map(function(r) { return r.date; }));",
            "            payload.jsonData.dailyRecords.forEach(function(rec) {",
            "              if (!existingDates.has(rec.date)) old.dailyRecords.push(rec);",
            "            });",
            "            old.exportedAt = payload.exportedAt;",
            "            jf.setContent(JSON.stringify(old, null, 2));",
            "          } else {",
            "            jf.setContent(jsonStr);",
            "          }",
            "        } catch (err) {",
            "          jf.setContent(jsonStr);",
            "        }",
            "        filesUpdated.push(jsonName + \" (appended)\");",
            "      } else if (jsonFiles.hasNext()) {",
            "        jsonFiles.next().setContent(jsonStr);",
            "        filesUpdated.push(jsonName + \" (overwritten)\");",
            "      } else {",
            "        folder.createFile(jsonName, jsonStr, MimeType.PLAIN_TEXT);",
            "        filesUpdated.push(jsonName + \" (created)\");",
            "      }",
            "    }",
            "",
            "    return ContentService.createTextOutput(JSON.stringify({",
            "      status: \"success\",",
            "      sourceApp: sourceApp,",
            "      message: \"Export processed successfully\",",
            "      folder: folderPath,",
            "      files: filesUpdated,",
            "      recordsCount: payload.recordsCount || 1",
            "    })).setMimeType(ContentService.MimeType.JSON);",
            "",
            "  } catch (err) {",
            "    return ContentService.createTextOutput(JSON.stringify({",
            "      status: \"error\",",
            "      message: err.toString()",
            "    })).setMimeType(ContentService.MimeType.JSON);",
            "  }",
            "}"
        ).joinToString("\n")

        /**
         * Backward compatible reference defaulting to the clean function content.
         */
        val SAMPLE_APPS_SCRIPT_CODE = MY_FUNCTION_APPS_SCRIPT_CONTENT
    }

    /**
     * Posts Health Connect biometrics export payload to Google Apps Script Webhook.
     */
    suspend fun postExport(
        webhookUrl: String,
        payload: BiometricsExportPayload,
        folderPath: String,
        targetSubfolder: String,
        fileName: String,
        format: ExportFormat,
        writeMode: WriteMode
    ): WebhookResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (webhookUrl.isBlank()) {
            return@withContext WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Google Apps Script Webhook URL is not configured. Please enter the webhook URL in settings.",
                durationMs = 0
            )
        }

        try {
            val jsonFormatted = JsonConverter.toJsonString(payload, indentSpaces = 2)
            val csvFormatted = CsvConverter.toCsvString(
                records = payload.dailyRecords,
                includeHeader = (writeMode == WriteMode.OVERWRITE)
            )

            val requestJson = JSONObject().apply {
                put("action", "export_health_data")
                put("exportVersion", payload.exportVersion)
                put("sourceApp", payload.sourceApp)
                put("timezone", payload.timezone)
                put("exportedAt", payload.exportedAt)
                put("folderPath", folderPath)
                put("targetSubfolder", targetSubfolder)
                put("fileName", fileName)
                put("format", when (format) {
                    ExportFormat.CSV -> "csv"
                    ExportFormat.JSON -> "json"
                    ExportFormat.BOTH -> "both"
                })
                put("writeMode", when (writeMode) {
                    WriteMode.APPEND -> "append"
                    WriteMode.OVERWRITE -> "overwrite"
                })
                put("jsonData", JSONObject(jsonFormatted))
                put("csvData", csvFormatted)
                put("recordsCount", payload.dailyRecords.size)
            }

            executePost(webhookUrl, requestJson, folderPath, startTime)
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Network error: ${e.localizedMessage ?: e.message ?: "Connection failed"}",
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Error: ${e.localizedMessage ?: e.message ?: "Unexpected error"}",
                durationMs = duration
            )
        }
    }

    /**
     * Posts Hevy Gym Workouts export payload to Google Apps Script Webhook.
     */
    suspend fun postWorkoutExport(
        webhookUrl: String,
        payload: WorkoutsExportPayload,
        folderPath: String,
        targetSubfolder: String,
        fileName: String,
        format: ExportFormat,
        writeMode: WriteMode
    ): WebhookResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (webhookUrl.isBlank()) {
            return@withContext WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Google Apps Script Webhook URL is not configured. Please enter the webhook URL in settings.",
                durationMs = 0
            )
        }

        try {
            val jsonFormatted = WorkoutJsonConverter.toJsonString(payload, indentSpaces = 2)
            val csvFormatted = WorkoutCsvConverter.toCsvString(
                workouts = payload.workouts,
                includeHeader = (writeMode == WriteMode.OVERWRITE)
            )

            val requestJson = JSONObject().apply {
                put("action", "export_gym_workouts")
                put("exportVersion", payload.exportVersion)
                put("sourceApp", payload.sourceApp)
                put("syncedAt", payload.syncedAt)
                put("folderPath", folderPath)
                put("targetSubfolder", targetSubfolder)
                put("fileName", fileName)
                put("format", when (format) {
                    ExportFormat.CSV -> "csv"
                    ExportFormat.JSON -> "json"
                    ExportFormat.BOTH -> "both"
                })
                put("writeMode", when (writeMode) {
                    WriteMode.APPEND -> "append"
                    WriteMode.OVERWRITE -> "overwrite"
                })
                put("jsonData", JSONObject(jsonFormatted))
                put("csvData", csvFormatted)
                put("recordsCount", payload.workouts.size)
            }

            executePost(webhookUrl, requestJson, folderPath, startTime)
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Network error: ${e.localizedMessage ?: e.message ?: "Connection failed"}",
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Error: ${e.localizedMessage ?: e.message ?: "Unexpected error"}",
                durationMs = duration
            )
        }
    }

    private fun executePost(
        webhookUrl: String,
        requestJson: JSONObject,
        folderPath: String,
        startTime: Long
    ): WebhookResult {
        val body = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(webhookUrl.trim())
            .post(body)
            .header("Content-Type", "application/json")
            .header("User-Agent", "HealthAndGymSync-Android/1.0")
            .build()

        val response = client.newCall(request).execute()
        val duration = System.currentTimeMillis() - startTime
        val responseBody = response.body?.string() ?: ""

        return if (response.isSuccessful) {
            WebhookResult(
                isSuccess = true,
                httpCode = response.code,
                message = "Export completed successfully to Google Drive ($folderPath)",
                durationMs = duration,
                responseBody = responseBody
            )
        } else {
            WebhookResult(
                isSuccess = false,
                httpCode = response.code,
                message = "HTTP ${response.code}: ${response.message.ifBlank { "Failed to export data" }}",
                durationMs = duration,
                responseBody = responseBody
            )
        }
    }

    /**
     * Quick test to verify connectivity with the webhook endpoint.
     */
    suspend fun testWebhookConnectivity(webhookUrl: String): WebhookResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (webhookUrl.isBlank()) {
            return@withContext WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Webhook URL cannot be empty",
                durationMs = 0
            )
        }

        try {
            val pingJson = JSONObject().apply {
                put("action", "ping")
                put("timestamp", System.currentTimeMillis())
                put("source", "HealthAndGymSyncTest")
            }
            val body = pingJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(webhookUrl.trim())
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val bodyStr = response.body?.string()

            if (response.isSuccessful) {
                WebhookResult(
                    isSuccess = true,
                    httpCode = response.code,
                    message = "Connected to Google Apps Script successfully (${response.code})",
                    durationMs = duration,
                    responseBody = bodyStr
                )
            } else {
                WebhookResult(
                    isSuccess = false,
                    httpCode = response.code,
                    message = "Server returned status code: ${response.code}",
                    durationMs = duration,
                    responseBody = bodyStr
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            WebhookResult(
                isSuccess = false,
                httpCode = null,
                message = "Connection failed: ${e.localizedMessage ?: "Unknown error"}",
                durationMs = duration
            )
        }
    }
}
