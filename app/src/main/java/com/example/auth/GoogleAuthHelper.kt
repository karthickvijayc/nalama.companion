package com.example.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.HealthSyncApplication
import com.example.util.AppLogger
import com.example.util.CertificateHelper
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class GoogleDriveAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnregisteredOnApiConsoleException(
    val packageName: String,
    val sha1: String,
    message: String,
    cause: Throwable? = null
) : GoogleDriveAuthException(message, cause)

/**
 * Handles real Google Sign-In and OAuth token generation for Google Drive access
 * using Google Play Services (play-services-auth).
 */
object GoogleAuthHelper {

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val OAUTH_SCOPE_STRING = "oauth2:$DRIVE_FILE_SCOPE"

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
    }

    fun getClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    /**
     * Resolves an OAuth bearer token for Google Drive API operations.
     * Tries:
     * 1. Explicit accountEmail parameter
     * 2. Saved connectedEmail in PreferencesManager
     * 3. Active GoogleSignInAccount on device
     *
     * Throws UserRecoverableAuthException if user consent UI is required.
     * Throws UnregisteredOnApiConsoleException if Google Cloud Console lacks matching package/SHA-1 client ID.
     */
    suspend fun fetchDriveAccessToken(
        context: Context,
        accountEmail: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val resolvedEmail = if (!accountEmail.isNullOrBlank()) {
            accountEmail.trim()
        } else {
            val app = context.applicationContext as? HealthSyncApplication
            app?.preferencesManager?.settings?.value?.connectedEmail?.trim()?.takeIf { it.isNotBlank() }
        }

        val targetAccount: Account? = when {
            !resolvedEmail.isNullOrBlank() -> Account(resolvedEmail, "com.google")
            else -> GoogleSignIn.getLastSignedInAccount(context)?.account
        }

        if (targetAccount == null) {
            AppLogger.w("AUTH", "No Google account found to retrieve token.")
            return@withContext null
        }

        try {
            AppLogger.i("AUTH", "Requesting Google Drive OAuth token for account: ${targetAccount.name}")
            val token = GoogleAuthUtil.getToken(context, targetAccount, OAUTH_SCOPE_STRING)
            AppLogger.s("AUTH", "Successfully obtained Google Drive OAuth token.")
            token
        } catch (recoverable: UserRecoverableAuthException) {
            AppLogger.w("AUTH", "User recoverable consent required for ${targetAccount.name}")
            throw recoverable
        } catch (authEx: GoogleAuthException) {
            val msg = authEx.message ?: ""
            if (msg.contains("UnregisteredOnApiConsole", ignoreCase = true)) {
                val pkg = CertificateHelper.getPackageName(context)
                val sha1 = CertificateHelper.getSigningSha1(context)
                val detailedMsg = "Google API Console registration required. In Google Cloud Console, register an Android OAuth Client ID with Package: '$pkg' and SHA-1: '$sha1'."
                AppLogger.e("AUTH", "UnregisteredOnApiConsole: $detailedMsg", authEx)
                throw UnregisteredOnApiConsoleException(pkg, sha1, detailedMsg, authEx)
            }
            AppLogger.e("AUTH", "Failed to retrieve Google Drive OAuth token: ${authEx.message}", authEx)
            throw GoogleDriveAuthException("Google Auth failed: ${authEx.message}", authEx)
        } catch (e: Exception) {
            AppLogger.e("AUTH", "Failed to retrieve Google Drive OAuth token: ${e.message}", e)
            throw GoogleDriveAuthException("Token fetch failed: ${e.message}", e)
        }
    }

    /**
     * Invalidates an expired token so Google Play Services can fetch a fresh one on next call.
     */
    suspend fun clearToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            AppLogger.d("AUTH", "Cleared expired Google OAuth token from local cache.")
        } catch (e: Exception) {
            AppLogger.w("AUTH", "Failed to clear token: ${e.message}")
        }
    }
}
