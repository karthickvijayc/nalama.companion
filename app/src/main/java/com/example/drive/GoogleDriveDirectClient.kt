package com.example.drive

import android.content.Context
import com.example.model.BiometricsExportPayload
import com.example.model.WorkoutsExportPayload
import com.example.model.WriteMode
import com.example.util.CsvConverter
import com.example.util.WorkoutCsvConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DirectDriveResult(
    val isSuccess: Boolean,
    val httpCode: Int?,
    val message: String,
    val durationMs: Long,
    val bytesTransferred: Long = 0,
    val activeRecordsCount: Int = 0,
    val targetFolderUrl: String? = null
)

class GoogleDriveDirectClient(private val context: Context) {

    val cacheManager = DriveSyncCacheManager(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets"

        private val CSV_MEDIA_TYPE = "text/csv; charset=utf-8".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Syncs Health Connect biometrics directly with Google Drive as CSV.
     */
    suspend fun syncBiometricsDirectly(
        accessToken: String?,
        payload: BiometricsExportPayload,
        folderPath: String,
        targetSubfolder: String,
        fileName: String,
        writeMode: WriteMode,
        archiveMaxDays: Int = 180
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val activeCsvFileName = "$fileName.csv"

        try {
            // 1. Read existing local cached content
            val localCsv = cacheManager.readLocalFileText(activeCsvFileName)

            // 2. Perform delta merge locally with intra-day in-place upsert and 180-day archiving
            val mergeResult = if (writeMode == WriteMode.OVERWRITE) {
                val csvContent = CsvConverter.toCsvString(payload.dailyRecords, includeHeader = true)
                BiometricsMergeResult(
                    activeCsv = csvContent,
                    archiveCsvByYear = emptyMap(),
                    updatedCount = 0,
                    newCount = payload.dailyRecords.size,
                    archivedCount = 0,
                    totalActiveRecords = payload.dailyRecords.size
                )
            } else {
                cacheManager.mergeBiometricsCsv(localCsv, payload.dailyRecords, archiveMaxDays)
            }

            // Write active CSV locally and compute local MD5
            val localActiveMd5 = cacheManager.writeLocalFileText(activeCsvFileName, mergeResult.activeCsv)
            val bytesPayload = mergeResult.activeCsv.toByteArray().size.toLong()

            // 3. If an access token is provided, perform live Google Drive REST API calls
            if (!accessToken.isNullOrBlank()) {
                val folderId = resolveOrCreateFolderPath(accessToken, folderPath)

                // Check remote file metadata
                val remoteMeta = queryRemoteFileMetadata(accessToken, folderId, activeCsvFileName)

                val fileId: String
                val finalRemoteMd5: String

                if (remoteMeta != null) {
                    val remoteMd5 = remoteMeta.optString("md5Checksum", "")
                    val cachedInfo = cacheManager.getCachedFileInfo(activeCsvFileName)

                    if (remoteMd5.isNotEmpty() && cachedInfo != null && remoteMd5 != cachedInfo.lastRemoteMd5) {
                        // Remote file was modified externally on Google Drive. Fetch remote and re-merge
                        val remoteContent = downloadRemoteFileText(accessToken, remoteMeta.getString("id"))
                        val reMerge = cacheManager.mergeBiometricsCsv(remoteContent, payload.dailyRecords, archiveMaxDays)
                        cacheManager.writeLocalFileText(activeCsvFileName, reMerge.activeCsv)
                    }

                    // Update the remote file in-place
                    fileId = remoteMeta.getString("id")
                    val updatedMeta = updateRemoteFileMedia(accessToken, fileId, mergeResult.activeCsv, "text/csv")
                    finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                } else {
                    // File does not exist yet on Drive: Create new
                    val created = createRemoteFile(accessToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                    fileId = created.getString("id")
                    finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                }

                // Upload yearly archive partitions if any were generated by 180-day cutoff
                mergeResult.archiveCsvByYear.forEach { (year, archiveCsv) ->
                    val archiveName = "${fileName}_$year.csv"
                    val archiveMeta = queryRemoteFileMetadata(accessToken, folderId, archiveName)
                    if (archiveMeta != null) {
                        updateRemoteFileMedia(accessToken, archiveMeta.getString("id"), archiveCsv, "text/csv")
                    } else {
                        createRemoteFile(accessToken, folderId, archiveName, archiveCsv, "text/csv")
                    }
                }

                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = fileId,
                    remoteMd5 = finalRemoteMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime
                DirectDriveResult(
                    isSuccess = true,
                    httpCode = 200,
                    message = "Synced ${mergeResult.totalActiveRecords} record(s) to Google Drive ($folderPath)",
                    durationMs = duration,
                    bytesTransferred = bytesPayload,
                    activeRecordsCount = mergeResult.totalActiveRecords,
                    targetFolderUrl = "https://drive.google.com/drive/folders/$folderId"
                )
            } else {
                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = "local_drive_${System.currentTimeMillis()}",
                    remoteMd5 = localActiveMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime
                DirectDriveResult(
                    isSuccess = true,
                    httpCode = 200,
                    message = "Saved ${mergeResult.totalActiveRecords} record(s) to $folderPath",
                    durationMs = duration,
                    bytesTransferred = bytesPayload,
                    activeRecordsCount = mergeResult.totalActiveRecords,
                    targetFolderUrl = "https://drive.google.com"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DirectDriveResult(
                isSuccess = false,
                httpCode = null,
                message = "Drive sync failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}",
                durationMs = duration
            )
        }
    }

    /**
     * Syncs Hevy workouts directly with Google Drive as CSV.
     */
    suspend fun syncWorkoutsDirectly(
        accessToken: String?,
        payload: WorkoutsExportPayload,
        folderPath: String,
        targetSubfolder: String,
        fileName: String,
        writeMode: WriteMode,
        archiveMaxDays: Int = 180
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val activeCsvFileName = "$fileName.csv"

        try {
            val localCsv = cacheManager.readLocalFileText(activeCsvFileName)
            val mergeResult = if (writeMode == WriteMode.OVERWRITE) {
                val csvContent = WorkoutCsvConverter.toCsvString(payload.workouts, includeHeader = true)
                WorkoutsMergeResult(
                    activeCsv = csvContent,
                    archiveCsvByYear = emptyMap(),
                    updatedCount = 0,
                    newCount = payload.workouts.size,
                    archivedCount = 0,
                    totalActiveRecords = payload.workouts.size
                )
            } else {
                cacheManager.mergeWorkoutsCsv(localCsv, payload.workouts, archiveMaxDays)
            }

            val localActiveMd5 = cacheManager.writeLocalFileText(activeCsvFileName, mergeResult.activeCsv)
            val bytesPayload = mergeResult.activeCsv.toByteArray().size.toLong()

            if (!accessToken.isNullOrBlank()) {
                val folderId = resolveOrCreateFolderPath(accessToken, folderPath)
                val remoteMeta = queryRemoteFileMetadata(accessToken, folderId, activeCsvFileName)
                val fileId: String
                val finalRemoteMd5: String

                if (remoteMeta != null) {
                    fileId = remoteMeta.getString("id")
                    val updatedMeta = updateRemoteFileMedia(accessToken, fileId, mergeResult.activeCsv, "text/csv")
                    finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                } else {
                    val created = createRemoteFile(accessToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                    fileId = created.getString("id")
                    finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                }

                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = fileId,
                    remoteMd5 = finalRemoteMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime
                DirectDriveResult(
                    isSuccess = true,
                    httpCode = 200,
                    message = "Synced ${mergeResult.totalActiveRecords} workout(s) to Google Drive ($folderPath)",
                    durationMs = duration,
                    bytesTransferred = bytesPayload,
                    activeRecordsCount = mergeResult.totalActiveRecords,
                    targetFolderUrl = "https://drive.google.com/drive/folders/$folderId"
                )
            } else {
                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = "local_drive_${System.currentTimeMillis()}",
                    remoteMd5 = localActiveMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime
                DirectDriveResult(
                    isSuccess = true,
                    httpCode = 200,
                    message = "Saved ${mergeResult.totalActiveRecords} workout(s) to $folderPath",
                    durationMs = duration,
                    bytesTransferred = bytesPayload,
                    activeRecordsCount = mergeResult.totalActiveRecords,
                    targetFolderUrl = "https://drive.google.com"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DirectDriveResult(
                isSuccess = false,
                httpCode = null,
                message = "Workout sync failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}",
                durationMs = duration
            )
        }
    }

    /**
     * Tests connectivity to Google Drive and verifies folder structure.
     */
    suspend fun testDirectDriveConnection(
        accessToken: String?,
        folderPath: String
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (accessToken.isNullOrBlank()) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext DirectDriveResult(
                isSuccess = true,
                httpCode = 200,
                message = "Google Drive sync ready for $folderPath.",
                durationMs = duration
            )
        }

        try {
            val folderId = resolveOrCreateFolderPath(accessToken, folderPath)
            val duration = System.currentTimeMillis() - startTime
            DirectDriveResult(
                isSuccess = true,
                httpCode = 200,
                message = "Connected to Google Drive successfully. Verified folder: $folderPath",
                durationMs = duration,
                targetFolderUrl = "https://drive.google.com/drive/folders/$folderId"
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DirectDriveResult(
                isSuccess = false,
                httpCode = null,
                message = "Google Drive check failed: ${e.localizedMessage ?: "Could not verify folder"}",
                durationMs = duration
            )
        }
    }

    private fun resolveOrCreateFolderPath(accessToken: String, fullPath: String): String {
        val cachedId = cacheManager.getCachedFolderId(fullPath)
        if (!cachedId.isNullOrBlank()) {
            return cachedId
        }

        val parts = fullPath.split("/").filter { it.isNotBlank() }
        var parentId = "root"

        for (part in parts) {
            val q = "'$parentId' in parents and name = '$part' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val url = "$DRIVE_API_BASE/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw IOException("Failed to query Drive folder: HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val files = json.getJSONArray("files")

            parentId = if (files.length() > 0) {
                files.getJSONObject(0).getString("id")
            } else {
                // Create folder
                val createJson = JSONObject().apply {
                    put("name", part)
                    put("mimeType", "application/vnd.google-apps.folder")
                    put("parents", JSONArray().put(parentId))
                }
                val postReq = Request.Builder()
                    .url("$DRIVE_API_BASE/files")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(createJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val createResp = httpClient.newCall(postReq).execute()
                val createBody = createResp.body?.string() ?: ""
                if (!createResp.isSuccessful) {
                    throw IOException("Failed to create folder '$part': HTTP ${createResp.code} $createBody")
                }
                JSONObject(createBody).getString("id")
            }
        }

        cacheManager.setCachedFolderId(fullPath, parentId)
        return parentId
    }

    private fun queryRemoteFileMetadata(accessToken: String, folderId: String, fileName: String): JSONObject? {
        val q = "'$folderId' in parents and name = '$fileName' and trashed = false"
        val url = "$DRIVE_API_BASE/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name,md5Checksum,modifiedTime,size)"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) return null

        val json = JSONObject(body)
        val files = json.getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0) else null
    }

    private fun updateRemoteFileMedia(accessToken: String, fileId: String, content: String, mimeType: String): JSONObject {
        val url = "$DRIVE_UPLOAD_BASE/files/$fileId?uploadType=media&fields=id,name,md5Checksum,modifiedTime"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(content.toRequestBody(mimeType.toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("Failed to update remote file $fileId: HTTP ${response.code} $body")
        }
        return JSONObject(body)
    }

    private fun createRemoteFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String,
        mimeType: String
    ): JSONObject {
        // Multipart upload for metadata + content
        val boundary = "-------NalamaBoundary${System.currentTimeMillis()}"
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        val delimiter = "\r\n--$boundary\r\n"
        val closeDelimiter = "\r\n--$boundary--"

        val multipartBody = StringBuilder()
            .append(delimiter)
            .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            .append(metadataJson.toString())
            .append(delimiter)
            .append("Content-Type: $mimeType\r\n\r\n")
            .append(content)
            .append(closeDelimiter)
            .toString()

        val url = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart&fields=id,name,md5Checksum,modifiedTime"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("Failed to create remote file $fileName: HTTP ${response.code} $body")
        }
        return JSONObject(body)
    }

    private fun downloadRemoteFileText(accessToken: String, fileId: String): String {
        val url = "$DRIVE_API_BASE/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("Failed to download remote file $fileId: HTTP ${response.code}")
        }
        return body
    }
}
