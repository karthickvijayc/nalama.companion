package com.example.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.util.AppLogger
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            .build()
    }

    fun getClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    /**
     * Resolves an OAuth bearer token for Google Drive API operations.
     * Tries:
     * 1. Active GoogleSignInAccount
     * 2. Device Account by email string
     *
     * Throws UserRecoverableAuthException if the user must grant consent via an Intent.
     */
    suspend fun fetchDriveAccessToken(
        context: Context,
        accountEmail: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val targetAccount: Account? = when {
            !accountEmail.isNullOrBlank() -> Account(accountEmail, "com.google")
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
        } catch (e: Exception) {
            AppLogger.e("AUTH", "Failed to retrieve Google Drive OAuth token: ${e.message}", e)
            null
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
