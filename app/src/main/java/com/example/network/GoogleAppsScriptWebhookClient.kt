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
         * Ready-to-use Google Apps Script code for Google Drive / Google Sheets.
         * Handles both Health Connect Biometrics and Hevy Gym Workouts!
         */
        val SAMPLE_APPS_SCRIPT_CODE = """
            /**
             * Google Apps Script Webhook Handler for Health Connect & Hevy Gym Sync
             * Target Folder Structure:
             * My Drive /
             *   └── nalama.family/
             *        └── imports/
             *             ├── health_data/  (biometrics_daily.json / *.csv)
             *             └── gym_workouts/ (hevy_workouts.json / *.csv)
             */
            function doPost(e) {
              try {
                if (!e || !e.postData || !e.postData.contents) {
                  return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "No post data received" }))
                    .setMimeType(ContentService.MimeType.JSON);
                }

                var payload = JSON.parse(e.postData.contents);
                var sourceApp = payload.sourceApp || "HealthConnect";
                var folderPath = payload.folderPath || (sourceApp === "Hevy" ? "nalama.family/imports/gym_workouts" : "nalama.family/imports/health_data");
                var format = payload.format || "csv"; // "csv", "json", or "both"
                var writeMode = payload.writeMode || "append"; // "append" or "overwrite"
                var fileName = payload.fileName || (sourceApp === "Hevy" ? "hevy_workouts" : "biometrics_daily");
                var jsonData = payload.jsonData;
                var csvData = payload.csvData;

                // 1. Resolve or create target folder in Google Drive
                var folder = getOrCreateFolderHierarchy(folderPath);

                // 2. Export based on format
                var filesUpdated = [];

                if (format === "csv" || format === "both") {
                  var csvFileName = fileName + ".csv";
                  var existingCsv = folder.getFilesByName(csvFileName);
                  if (existingCsv.hasNext() && writeMode === "append") {
                    var file = existingCsv.next();
                    var existingContent = file.getBlob().getDataAsString();
                    // Append new csv rows (strip header from new csvData if already present)
                    var lines = csvData.trim().split("\n");
                    var contentToAppend = lines.slice(1).join("\n");
                    if (contentToAppend.length > 0) {
                      file.setContent(existingContent + "\n" + contentToAppend);
                    }
                    filesUpdated.push(csvFileName + " (appended)");
                  } else {
                    if (existingCsv.hasNext()) {
                      existingCsv.next().setContent(csvData);
                      filesUpdated.push(csvFileName + " (overwritten)");
                    } else {
                      folder.createFile(csvFileName, csvData, MimeType.CSV);
                      filesUpdated.push(csvFileName + " (created)");
                    }
                  }
                }

                if (format === "json" || format === "both") {
                  var jsonFileName = fileName + ".json";
                  var existingJson = folder.getFilesByName(jsonFileName);
                  var jsonStr = typeof jsonData === "string" ? jsonData : JSON.stringify(jsonData, null, 2);

                  if (existingJson.hasNext() && writeMode === "append") {
                    var jsonFile = existingJson.next();
                    try {
                      var oldObj = JSON.parse(jsonFile.getBlob().getDataAsString());
                      if (sourceApp === "Hevy" && oldObj.workouts && payload.jsonData && payload.jsonData.workouts) {
                        // Merge Hevy workouts by workoutId
                        var existingIds = new Set(oldObj.workouts.map(function(w){ return w.workoutId; }));
                        payload.jsonData.workouts.forEach(function(w) {
                          if (!existingIds.has(w.workoutId)) {
                            oldObj.workouts.push(w);
                          }
                        });
                        oldObj.syncedAt = payload.syncedAt || new Date().toISOString();
                        jsonFile.setContent(JSON.stringify(oldObj, null, 2));
                      } else if (oldObj.dailyRecords && payload.jsonData && payload.jsonData.dailyRecords) {
                        // Merge Health Connect daily records
                        var existingDates = new Set(oldObj.dailyRecords.map(function(r){ return r.date; }));
                        payload.jsonData.dailyRecords.forEach(function(rec) {
                          if (!existingDates.has(rec.date)) {
                            oldObj.dailyRecords.push(rec);
                          }
                        });
                        oldObj.exportedAt = payload.exportedAt;
                        jsonFile.setContent(JSON.stringify(oldObj, null, 2));
                      } else {
                        jsonFile.setContent(jsonStr);
                      }
                    } catch(err) {
                      jsonFile.setContent(jsonStr);
                    }
                    filesUpdated.push(jsonFileName + " (appended)");
                  } else {
                    if (existingJson.hasNext()) {
                      existingJson.next().setContent(jsonStr);
                      filesUpdated.push(jsonFileName + " (overwritten)");
                    } else {
                      folder.createFile(jsonFileName, jsonStr, MimeType.PLAIN_TEXT);
                      filesUpdated.push(jsonFileName + " (created)");
                    }
                  }
                }

                return ContentService.createTextOutput(JSON.stringify({
                  status: "success",
                  sourceApp: sourceApp,
                  message: "Export processed successfully",
                  folder: folderPath,
                  files: filesUpdated,
                  recordsCount: payload.recordsCount || 1
                })).setMimeType(ContentService.MimeType.JSON);

              } catch (err) {
                return ContentService.createTextOutput(JSON.stringify({
                  status: "error",
                  message: err.toString()
                })).setMimeType(ContentService.MimeType.JSON);
              }
            }

            function getOrCreateFolderHierarchy(path) {
              var parts = path.split("/");
              var current = DriveApp.getRootFolder();
              for (var i = 0; i < parts.length; i++) {
                var name = parts[i].trim();
                if (name.length === 0) continue;
                var sub = current.getFoldersByName(name);
                if (sub.hasNext()) {
                  current = sub.next();
                } else {
                  current = current.createFolder(name);
                }
              }
              return current;
            }
        """.trimIndent()
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
