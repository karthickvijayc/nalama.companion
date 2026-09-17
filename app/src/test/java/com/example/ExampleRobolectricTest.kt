package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Health Data Companion for Nalama", appName)
    }

    @Test
    fun `preferences manager handles google account connection and disconnect`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        // Initially not connected
        assertFalse(prefs.settings.value.isGoogleConnected)
        assertFalse(prefs.settings.value.hasCompletedOnboarding)

        // Connect
        prefs.connectGoogleAccount("tester@example.com", "Tester User")
        assertTrue(prefs.settings.value.isGoogleConnected)
        assertTrue(prefs.settings.value.hasCompletedOnboarding)
        assertEquals("tester@example.com", prefs.settings.value.connectedEmail)
        assertEquals("Tester User", prefs.settings.value.connectedDisplayName)

        // Language selection
        prefs.updateSelectedLanguage("தமிழ் (Tamil)")
        assertEquals("தமிழ் (Tamil)", prefs.settings.value.selectedLanguage)

        // Disconnect
        prefs.disconnectGoogleAccount()
        assertFalse(prefs.settings.value.isGoogleConnected)
        assertFalse(prefs.settings.value.hasCompletedOnboarding)
        assertEquals("", prefs.settings.value.connectedEmail)
    }

    @Test
    fun `preferences manager handles demo mode onboarding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        prefs.completeOnboardingAsDemo()
        assertTrue(prefs.settings.value.demoModeEnabled)
        assertTrue(prefs.settings.value.hasCompletedOnboarding)
    }
}
