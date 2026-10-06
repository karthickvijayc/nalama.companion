package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val isNewer: Boolean,
    val publishedAt: String = ""
)

class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val GITHUB_REPO_OWNER = "karthickvijayc"
        const val GITHUB_REPO_NAME = "nalama.companion"
        const val GITHUB_API_LATEST_RELEASE_URL =
            "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        const val FALLBACK_APK_DOWNLOAD_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest/download/NalamaCompanionHealthData.APK"
        const val GITHUB_RELEASES_PAGE_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

        const val NOTIFICATION_CHANNEL_ID = "app_updates_channel"
        const val NOTIFICATION_CHANNEL_NAME = "App Updates"
        const val NOTIFICATION_ID = 2001

        /**
         * Compares semantic versions (e.g. "v1.1.4" vs "1.1.3", "v1.2.0" vs "v1.1.3").
         * Returns true if latestVersion is strictly newer than currentVersion.
         */
        fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
            val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val latestParts = cleanLatest.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

            val maxLength = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }

    /**
     * Queries GitHub API for the latest published release.
     */
    suspend fun checkForLatestRelease(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NalamaCompanion-Android/$currentVersion")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                val msg = "GitHub API Error (${response.code}): ${response.message.ifBlank { "Failed to query latest release" }}"
                AppLogger.w("UPDATE", msg)
                return@withContext Result.failure(Exception(msg))
            }

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").ifBlank { "v$currentVersion" }
            val releaseTitle = json.optString("name", "Nalama Companion $tagName")
            val releaseNotes = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE_URL)
            val publishedAt = json.optString("published_at", "")

            // Locate the APK download URL from assets if present, otherwise fallback to the canonical latest APK link
            var apkDownloadUrl = FALLBACK_APK_DOWNLOAD_URL
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null && assetsArray.length() > 0) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    val assetDownload = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) && assetDownload.isNotBlank()) {
                        apkDownloadUrl = assetDownload
                        break
                    }
                }
            }

            val isNewer = isNewerVersion(tagName, currentVersion)
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")

            AppLogger.i(
                "UPDATE",
                "Release check complete: Current=$currentVersion, Latest=$tagName, IsNewer=$isNewer"
            )

            Result.success(
                AppReleaseInfo(
                    tagName = tagName,
                    versionName = cleanVersion,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    htmlUrl = htmlUrl,
                    isNewer = isNewer,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            AppLogger.w("UPDATE", "Exception checking for updates: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Shows a system notification alerting the user to download the newer APK.
     */
    fun showUpdateNotification(context: Context, releaseInfo: AppReleaseInfo) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when a newer version of Nalama Companion is available"
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Intent to open browser and download the APK directly
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingDownloadIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            // Intent to view the release page on GitHub
            val viewReleaseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingViewReleaseIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID + 1,
                viewReleaseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notesSnippet = if (releaseInfo.releaseNotes.isNotBlank()) {
                "\n\n" + releaseInfo.releaseNotes.take(250) + if (releaseInfo.releaseNotes.length > 250) "..." else ""
            } else ""

            val contentText = "Nalama Companion ${releaseInfo.tagName} is available. Tap to download."
            val bigText = "A newer version (${releaseInfo.tagName}) of Nalama Companion is available.$notesSnippet"

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("New Version Available: ${releaseInfo.tagName}")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingDownloadIntent)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.stat_sys_download,
                    "Download",
                    pendingDownloadIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_info_details,
                    "View Notes",
                    pendingViewReleaseIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            AppLogger.s("UPDATE", "Posted update notification for ${releaseInfo.tagName}")
        } catch (e: Exception) {
            AppLogger.w("UPDATE", "Failed to show update notification: ${e.message}")
        }
    }
}
