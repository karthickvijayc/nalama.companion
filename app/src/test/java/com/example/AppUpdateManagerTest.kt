package com.example

import com.example.update.AppUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersionWithStandardVersions() {
        // Newer patch version
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.4", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("1.1.4", "1.1.3"))

        // Newer minor version
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0", "1.1.3"))

        // Newer major version
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.1.3"))

        // Double-digit version comparisons (1.10.0 > 1.9.0)
        assertTrue(AppUpdateManager.isNewerVersion("v1.10.0", "1.9.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.10.2", "1.10.1"))
    }

    @Test
    fun testIsNewerVersionWithEqualOrOlderVersions() {
        // Same version
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.3", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.3", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("V1.1.3", "1.1.3"))

        // Older patch version
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.2", "1.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.2", "1.1.3"))

        // Older minor version
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.9", "1.1.3"))

        // Older major version
        assertFalse(AppUpdateManager.isNewerVersion("v0.9.5", "1.1.3"))
    }

    @Test
    fun testIsNewerVersionWithPreReleaseSuffixes() {
        // Newer version with tag suffix (e.g. -beta, -rc)
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.4-beta", "1.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.0-rc1", "1.1.3"))

        // Same base version with suffix
        assertFalse(AppUpdateManager.isNewerVersion("v1.1.3-rc2", "1.1.3"))
    }
}
