package com.example.network

import com.example.model.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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

        val MY_FUNCTION_APPS_SCRIPT_CONTENT: String
            get() = AppsScriptTemplates.MY_FUNCTION_APPS_SCRIPT_CONTENT

        val FULL_APPS_SCRIPT_CODE: String
            get() = AppsScriptTemplates.FULL_APPS_SCRIPT_CODE

        val SAMPLE_APPS_SCRIPT_CODE: String
            get() = AppsScriptTemplates.SAMPLE_APPS_SCRIPT_CODE
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
        writeMode: WriteMode,
        archiveMaxDays: Int = 180,
        isBulkExport: Boolean = false
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
                includeHeader = true
            )

            val sheetRowsArray = JSONArray().apply {
                CsvConverter.toRowList(payload.dailyRecords).forEach { rowList ->
                    val rowArr = JSONArray()
                    rowList.forEach { rowArr.put(it ?: "") }
                    put(rowArr)
                }
            }
            val sheetHeadersArray = JSONArray(CsvConverter.getHeaderList())

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
                put("sheetHeaders", sheetHeadersArray)
                put("sheetRows", sheetRowsArray)
                put("recordsCount", payload.dailyRecords.size)
                put("archiveMaxDays", archiveMaxDays)
                put("isBulkExport", isBulkExport)
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
     * Posts Hevy Gym workouts export payload to Google Apps Script Webhook.
     */
    suspend fun postWorkoutExport(
        webhookUrl: String,
        payload: WorkoutsExportPayload,
        folderPath: String,
        targetSubfolder: String,
        fileName: String,
        format: ExportFormat,
        writeMode: WriteMode,
        archiveMaxDays: Int = 180,
        isBulkExport: Boolean = false
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
                includeHeader = true
            )

            val sheetRowsArray = JSONArray().apply {
                WorkoutCsvConverter.toRowList(payload.workouts).forEach { rowList ->
                    val rowArr = JSONArray()
                    rowList.forEach { rowArr.put(it ?: "") }
                    put(rowArr)
                }
            }
            val sheetHeadersArray = JSONArray(WorkoutCsvConverter.getHeaderList())

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
                put("sheetHeaders", sheetHeadersArray)
                put("sheetRows", sheetRowsArray)
                put("recordsCount", payload.workouts.size)
                put("archiveMaxDays", archiveMaxDays)
                put("isBulkExport", isBulkExport)
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
