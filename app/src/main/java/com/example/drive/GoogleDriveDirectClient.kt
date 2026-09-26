package com.example.drive

import android.content.Context
import com.example.HealthSyncApplication
import com.example.model.BiometricsExportPayload
import com.example.model.WorkoutsExportPayload
import com.example.model.WriteMode
import com.example.util.AppLogger
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

class DriveHttpException(val statusCode: Int, message: String) : IOException(message)

data class DirectDriveResult(
    val isSuccess: Boolean,
    val httpCode: Int?,
    val message: String,
    val durationMs: Long,
    val bytesTransferred: Long = 0,
    val activeRecordsCount: Int = 0,
    val targetFolderUrl: String? = null,
    val isLocalOnlyFallback: Boolean = false,
    val userEmail: String? = null,
    val displayName: String? = null
)

data class DriveDiagnosticTestResult(
    val isSuccess: Boolean,
    val userEmail: String? = null,
    val folderId: String? = null,
    val canWrite: Boolean = false,
    val httpCode: Int? = null,
    val message: String,
    val durationMs: Long
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

        private val CSV_MEDIA_TYPE = "text/csv; charset=utf-8".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
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
        archiveMaxDays: Int = 180,
        isDemoMode: Boolean = false,
        userEmail: String? = null
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val activeCsvFileName = "$fileName.csv"
        AppLogger.i("DRIVE", "Starting biometrics sync (${payload.dailyRecords.size} records) to '$folderPath/$activeCsvFileName' [WriteMode: ${writeMode.name}]")

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
            AppLogger.d("CACHE", "Local baseline prepared: ${mergeResult.totalActiveRecords} records, ${bytesPayload}B, MD5: $localActiveMd5")

            // 3. Resolve active OAuth token (provided or via Google Play Services)
            var activeToken = accessToken
            if (activeToken.isNullOrBlank()) {
                try {
                    activeToken = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
                } catch (e: Exception) {
                    AppLogger.d("AUTH", "Automatic token fetch returned null: ${e.message}")
                }
            }

            if (!activeToken.isNullOrBlank()) {
                var fileId: String
                var finalRemoteMd5: String
                var folderId: String

                try {
                    AppLogger.d("DRIVE", "Live Google Drive sync active. Resolving folder structure '$folderPath'...")
                    folderId = resolveOrCreateFolderPath(activeToken, folderPath)

                    // Check remote file metadata
                    val remoteMeta = queryRemoteFileMetadata(activeToken, folderId, activeCsvFileName)

                    if (remoteMeta != null) {
                        val remoteMd5 = remoteMeta.optString("md5Checksum", "")
                        val cachedInfo = cacheManager.getCachedFileInfo(activeCsvFileName)

                        if (remoteMd5.isNotEmpty() && cachedInfo != null && remoteMd5 != cachedInfo.lastRemoteMd5) {
                            AppLogger.i("DRIVE", "Remote file was modified externally on Google Drive. Fetching and re-merging...")
                            val remoteContent = downloadRemoteFileText(activeToken, remoteMeta.getString("id"))
                            val reMerge = cacheManager.mergeBiometricsCsv(remoteContent, payload.dailyRecords, archiveMaxDays)
                            cacheManager.writeLocalFileText(activeCsvFileName, reMerge.activeCsv)
                        }

                        // Update the remote file in-place
                        fileId = remoteMeta.getString("id")
                        AppLogger.d("DRIVE", "Updating existing Google Drive file ID: $fileId...")
                        val updatedMeta = updateRemoteFileMedia(activeToken, fileId, mergeResult.activeCsv, "text/csv")
                        finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                        AppLogger.s("DRIVE", "Successfully updated file $activeCsvFileName on Google Drive (ID: $fileId, MD5: $finalRemoteMd5)")
                    } else {
                        // File does not exist yet on Drive (or was deleted by user on Drive)
                        AppLogger.i("DRIVE", "Remote file $activeCsvFileName not found on Google Drive. Creating new file...")
                        val created = createRemoteFile(activeToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                        fileId = created.getString("id")
                        finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                        AppLogger.s("DRIVE", "Successfully created file $activeCsvFileName on Google Drive (ID: $fileId, MD5: $finalRemoteMd5)")
                    }

                    // Upload yearly archive partitions if any were generated by 180-day cutoff
                    mergeResult.archiveCsvByYear.forEach { (year, archiveCsv) ->
                        val archiveName = "${fileName}_$year.csv"
                        AppLogger.d("DRIVE", "Checking archive file partition '$archiveName' on Drive...")
                        val archiveMeta = queryRemoteFileMetadata(activeToken, folderId, archiveName)
                        if (archiveMeta != null) {
                            val existingArchiveCsv = try {
                                downloadRemoteFileText(activeToken, archiveMeta.getString("id"))
                            } catch (_: Exception) { null }

                            val finalArchiveCsv = if (!existingArchiveCsv.isNullOrBlank()) {
                                val incomingYearRecords = mergeResult.archivedRecordsByYear[year] ?: emptyList()
                                cacheManager.mergeBiometricsCsv(existingArchiveCsv, incomingYearRecords, 0).activeCsv
                            } else {
                                archiveCsv
                            }
                            updateRemoteFileMedia(activeToken, archiveMeta.getString("id"), finalArchiveCsv, "text/csv")
                        } else {
                            createRemoteFile(activeToken, folderId, archiveName, archiveCsv, "text/csv")
                        }
                    }
                } catch (authExp: DriveHttpException) {
                    if (authExp.statusCode == 401) {
                        AppLogger.w("DRIVE", "Drive token expired (HTTP 401). Refreshing token and retrying sync...")
                        com.example.auth.GoogleAuthHelper.clearToken(context, activeToken)
                        val app = context.applicationContext as? HealthSyncApplication
                        app?.preferencesManager?.updateGoogleOAuthAccessToken("")
                        val freshToken = try {
                            com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
                        } catch (_: Exception) { null }

                        if (!freshToken.isNullOrBlank()) {
                            app?.preferencesManager?.updateGoogleOAuthAccessToken(freshToken)
                            activeToken = freshToken
                            folderId = resolveOrCreateFolderPath(activeToken, folderPath)
                            val remoteMeta = queryRemoteFileMetadata(activeToken, folderId, activeCsvFileName)
                            if (remoteMeta != null) {
                                fileId = remoteMeta.getString("id")
                                val updatedMeta = updateRemoteFileMedia(activeToken, fileId, mergeResult.activeCsv, "text/csv")
                                finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                            } else {
                                val created = createRemoteFile(activeToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                                fileId = created.getString("id")
                                finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                            }
                        } else {
                            throw authExp
                        }
                    } else {
                        throw authExp
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
                    targetFolderUrl = "https://drive.google.com/drive/folders/$folderId",
                    isLocalOnlyFallback = false
                )
            } else {
                // NO ACCESS TOKEN PROVIDED
                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = "local_cache_${System.currentTimeMillis()}",
                    remoteMd5 = localActiveMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime

                if (isDemoMode) {
                    AppLogger.i("DEMO", "Demo mode: Saved ${mergeResult.totalActiveRecords} records to local cache.")
                    DirectDriveResult(
                        isSuccess = true,
                        httpCode = 200,
                        message = "Demo Mode: Saved ${mergeResult.totalActiveRecords} record(s) locally on device.",
                        durationMs = duration,
                        bytesTransferred = bytesPayload,
                        activeRecordsCount = mergeResult.totalActiveRecords,
                        targetFolderUrl = null,
                        isLocalOnlyFallback = true
                    )
                } else {
                    AppLogger.w("DRIVE", "Google Drive export skipped: No Google Drive authorization token found. Saved locally only.")
                    DirectDriveResult(
                        isSuccess = false,
                        httpCode = 401,
                        message = "Google Drive authorization required. Please tap 'Authorize Google Drive' to upload CSV files.",
                        durationMs = duration,
                        bytesTransferred = bytesPayload,
                        activeRecordsCount = mergeResult.totalActiveRecords,
                        targetFolderUrl = null,
                        isLocalOnlyFallback = true
                    )
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.e("DRIVE", "Biometrics sync failed: ${e.localizedMessage ?: e.message}", e)
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
        archiveMaxDays: Int = 180,
        isDemoMode: Boolean = false,
        userEmail: String? = null
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val activeCsvFileName = "$fileName.csv"
        AppLogger.i("DRIVE", "Starting workouts sync (${payload.workouts.size} workouts) to '$folderPath/$activeCsvFileName' [WriteMode: ${writeMode.name}]")

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
            AppLogger.d("CACHE", "Local workouts baseline prepared: ${mergeResult.totalActiveRecords} workouts, ${bytesPayload}B, MD5: $localActiveMd5")

            var activeToken = accessToken
            if (activeToken.isNullOrBlank()) {
                try {
                    activeToken = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
                } catch (e: Exception) {
                    AppLogger.d("AUTH", "Automatic token fetch for workouts returned null: ${e.message}")
                }
            }

            if (!activeToken.isNullOrBlank()) {
                var fileId: String
                var finalRemoteMd5: String
                var folderId: String

                try {
                    AppLogger.d("DRIVE", "Live Google Drive sync active for workouts. Resolving folder structure '$folderPath'...")
                    folderId = resolveOrCreateFolderPath(activeToken, folderPath)
                    val remoteMeta = queryRemoteFileMetadata(activeToken, folderId, activeCsvFileName)

                    if (remoteMeta != null) {
                        fileId = remoteMeta.getString("id")
                        AppLogger.d("DRIVE", "Updating existing workouts file on Google Drive (ID: $fileId)...")
                        val updatedMeta = updateRemoteFileMedia(activeToken, fileId, mergeResult.activeCsv, "text/csv")
                        finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                        AppLogger.s("DRIVE", "Successfully updated $activeCsvFileName on Google Drive (ID: $fileId, MD5: $finalRemoteMd5)")
                    } else {
                        AppLogger.i("DRIVE", "Workouts file $activeCsvFileName not found on Google Drive. Creating new file...")
                        val created = createRemoteFile(activeToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                        fileId = created.getString("id")
                        finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                        AppLogger.s("DRIVE", "Successfully created $activeCsvFileName on Google Drive (ID: $fileId, MD5: $finalRemoteMd5)")
                    }

                    // Upload yearly archive partitions if any were generated by cutoff
                    mergeResult.archiveCsvByYear.forEach { (year, archiveCsv) ->
                        val archiveName = "${fileName}_$year.csv"
                        AppLogger.d("DRIVE", "Checking archive workouts partition '$archiveName' on Drive...")
                        val archiveMeta = queryRemoteFileMetadata(activeToken, folderId, archiveName)
                        if (archiveMeta != null) {
                            val existingArchiveCsv = try {
                                downloadRemoteFileText(activeToken, archiveMeta.getString("id"))
                            } catch (_: Exception) { null }

                            val finalArchiveCsv = if (!existingArchiveCsv.isNullOrBlank()) {
                                val incomingYearWorkouts = mergeResult.archivedWorkoutsByYear[year] ?: emptyList()
                                cacheManager.mergeWorkoutsCsv(existingArchiveCsv, incomingYearWorkouts, 0).activeCsv
                            } else {
                                archiveCsv
                            }
                            updateRemoteFileMedia(activeToken, archiveMeta.getString("id"), finalArchiveCsv, "text/csv")
                        } else {
                            createRemoteFile(activeToken, folderId, archiveName, archiveCsv, "text/csv")
                        }
                    }
                } catch (authExp: DriveHttpException) {
                    if (authExp.statusCode == 401) {
                        AppLogger.w("DRIVE", "Drive token expired during workout sync (HTTP 401). Refreshing token and retrying...")
                        com.example.auth.GoogleAuthHelper.clearToken(context, activeToken)
                        val app = context.applicationContext as? HealthSyncApplication
                        app?.preferencesManager?.updateGoogleOAuthAccessToken("")
                        val freshToken = try {
                            com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
                        } catch (_: Exception) { null }

                        if (!freshToken.isNullOrBlank()) {
                            app?.preferencesManager?.updateGoogleOAuthAccessToken(freshToken)
                            activeToken = freshToken
                            folderId = resolveOrCreateFolderPath(activeToken, folderPath)
                            val remoteMeta = queryRemoteFileMetadata(activeToken, folderId, activeCsvFileName)
                            if (remoteMeta != null) {
                                fileId = remoteMeta.getString("id")
                                val updatedMeta = updateRemoteFileMedia(activeToken, fileId, mergeResult.activeCsv, "text/csv")
                                finalRemoteMd5 = updatedMeta.optString("md5Checksum", localActiveMd5)
                            } else {
                                val created = createRemoteFile(activeToken, folderId, activeCsvFileName, mergeResult.activeCsv, "text/csv")
                                fileId = created.getString("id")
                                finalRemoteMd5 = created.optString("md5Checksum", localActiveMd5)
                            }

                            // Upload yearly archive partitions on retry
                            mergeResult.archiveCsvByYear.forEach { (year, archiveCsv) ->
                                val archiveName = "${fileName}_$year.csv"
                                val archiveMeta = queryRemoteFileMetadata(activeToken, folderId, archiveName)
                                if (archiveMeta != null) {
                                    val existingArchiveCsv = try {
                                        downloadRemoteFileText(activeToken, archiveMeta.getString("id"))
                                    } catch (_: Exception) { null }

                                    val finalArchiveCsv = if (!existingArchiveCsv.isNullOrBlank()) {
                                        val incomingYearWorkouts = mergeResult.archivedWorkoutsByYear[year] ?: emptyList()
                                        cacheManager.mergeWorkoutsCsv(existingArchiveCsv, incomingYearWorkouts, 0).activeCsv
                                    } else {
                                        archiveCsv
                                    }
                                    updateRemoteFileMedia(activeToken, archiveMeta.getString("id"), finalArchiveCsv, "text/csv")
                                } else {
                                    createRemoteFile(activeToken, folderId, archiveName, archiveCsv, "text/csv")
                                }
                            }
                        } else {
                            throw authExp
                        }
                    } else {
                        throw authExp
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
                    message = "Synced ${mergeResult.totalActiveRecords} workout(s) to Google Drive ($folderPath)",
                    durationMs = duration,
                    bytesTransferred = bytesPayload,
                    activeRecordsCount = mergeResult.totalActiveRecords,
                    targetFolderUrl = "https://drive.google.com/drive/folders/$folderId",
                    isLocalOnlyFallback = false
                )
            } else {
                cacheManager.updateCachedFile(
                    fileName = activeCsvFileName,
                    fileId = "local_cache_${System.currentTimeMillis()}",
                    remoteMd5 = localActiveMd5,
                    localMd5 = localActiveMd5,
                    rowCount = mergeResult.totalActiveRecords
                )

                val duration = System.currentTimeMillis() - startTime

                if (isDemoMode) {
                    AppLogger.i("DEMO", "Demo mode: Saved ${mergeResult.totalActiveRecords} workouts to local cache.")
                    DirectDriveResult(
                        isSuccess = true,
                        httpCode = 200,
                        message = "Demo Mode: Saved ${mergeResult.totalActiveRecords} workout(s) locally on device.",
                        durationMs = duration,
                        bytesTransferred = bytesPayload,
                        activeRecordsCount = mergeResult.totalActiveRecords,
                        targetFolderUrl = null,
                        isLocalOnlyFallback = true
                    )
                } else {
                    AppLogger.w("DRIVE", "Workout export skipped: No Google Drive authorization token found. Saved locally only.")
                    DirectDriveResult(
                        isSuccess = false,
                        httpCode = 401,
                        message = "Google Drive authorization required. Please tap 'Authorize Google Drive' to upload CSV files.",
                        durationMs = duration,
                        bytesTransferred = bytesPayload,
                        activeRecordsCount = mergeResult.totalActiveRecords,
                        targetFolderUrl = null,
                        isLocalOnlyFallback = true
                    )
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.e("DRIVE", "Workouts sync failed: ${e.localizedMessage ?: e.message}", e)
            DirectDriveResult(
                isSuccess = false,
                httpCode = null,
                message = "Workout sync failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}",
                durationMs = duration
            )
        }
    }

    /**
     * Tests connectivity to Google Drive, validates token with /about, and verifies folder structure.
     */
    suspend fun testDirectDriveConnection(
        accessToken: String?,
        folderPath: String,
        userEmail: String? = null
    ): DirectDriveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AppLogger.i("DRIVE", "Testing Google Drive connection for folder '$folderPath' (Email: $userEmail)...")

        var validToken = accessToken
        if (validToken.isNullOrBlank()) {
            try {
                validToken = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
            } catch (e: Exception) {
                AppLogger.d("AUTH", "Automatic token fetch in test returned: ${e.message}")
            }
        }

        if (validToken.isNullOrBlank()) {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.w("DRIVE", "Drive connection test: Google Drive is not authorized yet.")
            return@withContext DirectDriveResult(
                isSuccess = false,
                httpCode = 401,
                message = "Google Drive authorization required. Please tap 'Authorize Google Drive' to connect.",
                durationMs = duration
            )
        }

        try {
            // 1. Verify token by querying user identity
            val aboutReq = Request.Builder()
                .url("$DRIVE_API_BASE/about?fields=user(displayName,emailAddress)")
                .addHeader("Authorization", "Bearer $validToken")
                .get()
                .build()

            val aboutResp = httpClient.newCall(aboutReq).execute()
            val aboutBody = aboutResp.body?.string() ?: ""

            if (!aboutResp.isSuccessful) {
                if (aboutResp.code == 401) {
                    com.example.auth.GoogleAuthHelper.clearToken(context, validToken)
                }
                val duration = System.currentTimeMillis() - startTime
                AppLogger.e("DRIVE", "Drive token verification failed: HTTP ${aboutResp.code} $aboutBody")
                return@withContext DirectDriveResult(
                    isSuccess = false,
                    httpCode = aboutResp.code,
                    message = "Google Drive authorization failed (HTTP ${aboutResp.code}). Please re-authorize Google Drive.",
                    durationMs = duration
                )
            }

            val aboutJson = JSONObject(aboutBody)
            val userObj = aboutJson.optJSONObject("user")
            val email = userObj?.optString("emailAddress", "Google Account") ?: "Google Account"
            val name = userObj?.optString("displayName", "") ?: ""

            // 2. Resolve or verify folder
            val folderId = resolveOrCreateFolderPath(validToken, folderPath)
            val duration = System.currentTimeMillis() - startTime

            val displayIdentity = if (name.isNotBlank()) "$name ($email)" else email
            AppLogger.s("DRIVE", "Drive verification succeeded for $displayIdentity. Folder: $folderPath (ID: $folderId)")

            DirectDriveResult(
                isSuccess = true,
                httpCode = 200,
                message = "Connected to Google Drive ($displayIdentity). Verified folder: $folderPath",
                durationMs = duration,
                targetFolderUrl = "https://drive.google.com/drive/folders/$folderId",
                userEmail = email,
                displayName = name
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.e("DRIVE", "Drive connection check exception: ${e.localizedMessage ?: e.message}", e)
            DirectDriveResult(
                isSuccess = false,
                httpCode = null,
                message = "Google Drive check failed: ${e.localizedMessage ?: "Could not verify connection"}",
                durationMs = duration
            )
        }
    }

    /**
     * Runs a comprehensive live diagnostic:
     * 1. Token validation (/about)
     * 2. Folder resolution (/files)
     * 3. Temporary write test (.nalama_ping.txt) and cleanup
     */
    suspend fun runFullDriveDiagnostic(
        accessToken: String?,
        folderPath: String,
        userEmail: String? = null
    ): DriveDiagnosticTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        var activeToken = accessToken
        if (activeToken.isNullOrBlank() && !userEmail.isNullOrBlank()) {
            try {
                activeToken = com.example.auth.GoogleAuthHelper.fetchDriveAccessToken(context, userEmail)
            } catch (e: Exception) {
                AppLogger.d("AUTH", "Diagnostic token resolution failed: ${e.message}")
            }
        }

        if (activeToken.isNullOrBlank()) {
            return@withContext DriveDiagnosticTestResult(
                isSuccess = false,
                message = "No Google Drive access token found. Please tap 'Authorize Google Drive' or enter a token in Settings.",
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            AppLogger.i("DRIVE", "Running full diagnostic test against Google Drive API...")
            // Step 1: Query User
            val aboutReq = Request.Builder()
                .url("$DRIVE_API_BASE/about?fields=user(displayName,emailAddress)")
                .addHeader("Authorization", "Bearer $activeToken")
                .get()
                .build()

            val aboutResp = httpClient.newCall(aboutReq).execute()
            val aboutBody = aboutResp.body?.string() ?: ""

            if (!aboutResp.isSuccessful) {
                if (aboutResp.code == 401) {
                    com.example.auth.GoogleAuthHelper.clearToken(context, activeToken)
                    val app = context.applicationContext as? HealthSyncApplication
                    app?.preferencesManager?.updateGoogleOAuthAccessToken("")
                }
                AppLogger.e("DRIVE", "Diagnostic Step 1 Failed: HTTP ${aboutResp.code} $aboutBody")
                return@withContext DriveDiagnosticTestResult(
                    isSuccess = false,
                    httpCode = aboutResp.code,
                    message = "Drive token invalid or expired (HTTP ${aboutResp.code})",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            val aboutJson = JSONObject(aboutBody)
            val detectedEmail = aboutJson.optJSONObject("user")?.optString("emailAddress", "Unknown")

            // Step 2: Resolve folder
            val folderId = resolveOrCreateFolderPath(activeToken, folderPath)

            // Step 3: Write ping test file
            val pingFileName = ".nalama_diagnostic_ping_${System.currentTimeMillis()}.txt"
            val pingContent = "Nalama Diagnostic Ping at ${System.currentTimeMillis()}"
            val pingCreated = createRemoteFile(activeToken, folderId, pingFileName, pingContent, "text/plain")
            val pingFileId = pingCreated.getString("id")

            // Cleanup ping file
            deleteRemoteFile(activeToken, pingFileId)
            AppLogger.s("DRIVE", "Diagnostic write test succeeded. Created and deleted ping file '$pingFileName' in folder '$folderId'.")

            DriveDiagnosticTestResult(
                isSuccess = true,
                userEmail = detectedEmail,
                folderId = folderId,
                canWrite = true,
                httpCode = 200,
                message = "Full Google Drive diagnostic passed! Account: $detectedEmail, Folder ID: $folderId, Write & Read verified.",
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            AppLogger.e("DRIVE", "Diagnostic test failed with exception: ${e.localizedMessage ?: e.message}", e)
            DriveDiagnosticTestResult(
                isSuccess = false,
                message = "Diagnostic error: ${e.localizedMessage ?: e.message}",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun verifyFolderExists(accessToken: String, folderId: String): Boolean {
        if (folderId.isBlank()) return false
        if (folderId == "root") return true
        return try {
            val url = "$DRIVE_API_BASE/files/$folderId?fields=id,trashed"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                !json.optBoolean("trashed", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveOrCreateFolderPath(accessToken: String, fullPath: String): String {
        val cachedId = cacheManager.getCachedFolderId(fullPath)
        if (!cachedId.isNullOrBlank()) {
            if (verifyFolderExists(accessToken, cachedId)) {
                AppLogger.d("DRIVE", "Verified cached folder ID for '$fullPath': $cachedId")
                return cachedId
            } else {
                AppLogger.w("DRIVE", "Cached folder ID '$cachedId' for '$fullPath' was deleted or trashed on Drive. Re-resolving...")
                cacheManager.removeCachedFolderId(fullPath)
            }
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
                AppLogger.e("DRIVE", "Query folder '$part' failed: HTTP ${response.code} $body")
                throw DriveHttpException(response.code, "Failed to query Drive folder '$part': HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val files = json.getJSONArray("files")

            parentId = if (files.length() > 0) {
                val foundId = files.getJSONObject(0).getString("id")
                AppLogger.d("DRIVE", "Found folder '$part': $foundId")
                foundId
            } else {
                // Create folder
                AppLogger.i("DRIVE", "Folder '$part' not found on Drive. Creating under parent '$parentId'...")
                val createJson = JSONObject().apply {
                    put("name", part)
                    put("mimeType", "application/vnd.google-apps.folder")
                    put("parents", JSONArray().put(parentId))
                }
                val postReq = Request.Builder()
                    .url("$DRIVE_API_BASE/files?fields=id,name")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(createJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val createResp = httpClient.newCall(postReq).execute()
                val createBody = createResp.body?.string() ?: ""
                if (!createResp.isSuccessful) {
                    AppLogger.e("DRIVE", "Failed to create folder '$part': HTTP ${createResp.code} $createBody")
                    throw DriveHttpException(createResp.code, "Failed to create folder '$part': HTTP ${createResp.code} $createBody")
                }
                val newId = JSONObject(createBody).getString("id")
                AppLogger.s("DRIVE", "Created folder '$part' on Drive: $newId")
                newId
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
        if (!response.isSuccessful) {
            if (response.code == 401) {
                throw DriveHttpException(401, "Drive token expired during metadata query")
            }
            AppLogger.w("DRIVE", "Query remote file '$fileName' returned HTTP ${response.code}")
            return null
        }

        val json = JSONObject(body)
        val files = json.getJSONArray("files")
        return if (files.length() > 0) {
            val fileObj = files.getJSONObject(0)
            AppLogger.d("DRIVE", "Found remote file '$fileName' on Drive (ID: ${fileObj.optString("id")})")
            fileObj
        } else {
            AppLogger.d("DRIVE", "Remote file '$fileName' does not exist on Drive.")
            cacheManager.removeCachedFileInfo(fileName)
            null
        }
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
            AppLogger.e("DRIVE", "Failed to update remote file $fileId: HTTP ${response.code} $body")
            throw DriveHttpException(response.code, "Failed to update remote file $fileId: HTTP ${response.code} $body")
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
        // Multipart upload for metadata + content according to RFC 2387
        val boundary = "-------NalamaBoundary${System.currentTimeMillis()}"
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        val multipartBody = StringBuilder()
            .append("--$boundary\r\n")
            .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            .append(metadataJson.toString())
            .append("\r\n--$boundary\r\n")
            .append("Content-Type: $mimeType\r\n\r\n")
            .append(content)
            .append("\r\n--$boundary--")
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
            AppLogger.e("DRIVE", "Failed to create remote file $fileName: HTTP ${response.code} $body")
            throw DriveHttpException(response.code, "Failed to create remote file $fileName: HTTP ${response.code} $body")
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
            throw DriveHttpException(response.code, "Failed to download remote file $fileId: HTTP ${response.code}")
        }
        return body
    }

    private fun deleteRemoteFile(accessToken: String, fileId: String) {
        val url = "$DRIVE_API_BASE/files/$fileId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful && response.code != 404) {
            throw DriveHttpException(response.code, "Failed to delete remote file $fileId: HTTP ${response.code}")
        }
    }
}
