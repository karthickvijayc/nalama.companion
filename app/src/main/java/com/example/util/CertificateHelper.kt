package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Utility to inspect the runtime APK package name and signing certificate fingerprints.
 * Essential for verifying and configuring Google Cloud Console OAuth 2.0 Client IDs.
 */
object CertificateHelper {

    fun getPackageName(context: Context): String = context.packageName

    fun getSigningSha1(context: Context): String {
        return getCertificateFingerprint(context, "SHA-1")
    }

    fun getSigningSha256(context: Context): String {
        return getCertificateFingerprint(context, "SHA-256")
    }

    private fun getCertificateFingerprint(context: Context, algorithm: String): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val cert = signatures?.firstOrNull() ?: return "No certificate found"
            val md = MessageDigest.getInstance(algorithm)
            val digest = md.digest(cert.toByteArray())
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Unavailable: ${e.message}"
        }
    }
}
