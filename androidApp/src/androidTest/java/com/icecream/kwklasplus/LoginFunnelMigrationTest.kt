package com.icecream.kwklasplus

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginFunnelMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migrationSeparatesNewInstallFromExistingAccount() {
        val preferences = context.getSharedPreferences("login_funnel_migration_test", Context.MODE_PRIVATE)
        try {
            preferences.edit().clear().commit()
            assertTrue(LoginFunnelStatus.migrateLegacyInstall(preferences))
            assertEquals(LoginFunnelStatus.NOT_STARTED, preferences.getString(LoginFunnelStatus.KEY, null))

            preferences.edit().clear().putString(AppPrefs.KW_ID, "2020123456").commit()
            assertTrue(LoginFunnelStatus.migrateLegacyInstall(preferences))
            assertEquals(LoginFunnelStatus.COMPLETE, preferences.getString(LoginFunnelStatus.KEY, null))

            preferences.edit().putString(LoginFunnelStatus.KEY, LoginFunnelStatus.SETUP).commit()
            assertTrue(LoginFunnelStatus.migrateLegacyInstall(preferences))
            assertEquals(LoginFunnelStatus.SETUP, preferences.getString(LoginFunnelStatus.KEY, null))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun qrWidgetOpensLoginBeforeOnboardingStarts() {
        val preferences = context.appPreferences
        val previousStatus = preferences.getString(LoginFunnelStatus.KEY, null)
        val loginMonitor = instrumentation.addMonitor(LoginActivity::class.java.name, null, false)
        val homeMonitor = instrumentation.addMonitor(HomeActivity::class.java.name, null, false)
        try {
            preferences.edit().putString(LoginFunnelStatus.KEY, LoginFunnelStatus.NOT_STARTED).commit()
            context.startActivity(Intent(context, LibraryQRWidgetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val login = instrumentation.waitForMonitorWithTimeout(loginMonitor, 5000)
            assertNotNull(login)
            assertFalse(homeMonitor.hits > 0)
            instrumentation.runOnMainSync { login?.finish() }
        } finally {
            instrumentation.removeMonitor(loginMonitor)
            instrumentation.removeMonitor(homeMonitor)
            preferences.edit().apply {
                if (previousStatus == null) remove(LoginFunnelStatus.KEY)
                else putString(LoginFunnelStatus.KEY, previousStatus)
            }.commit()
        }
    }
}
