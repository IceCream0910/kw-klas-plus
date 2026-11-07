package com.icecream.kwklasplus

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icecream.kwklasplus.core.academic.AndroidAcademicWidgetSnapshotStore
import com.icecream.kwklasplus.core.academic.TimetableEntry
import com.icecream.kwklasplus.core.auth.StoredCredential
import com.icecream.kwklasplus.core.demo.DemoSession
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus
import com.icecream.kwklasplus.widget.AcademicWidgetRuntime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DemoPreferenceLifetimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = IsolatedPreferencesContext(instrumentation.targetContext)

    @After fun cleanup() {
        DemoSession.end()
        context.clearTestData()
    }

    @Test fun restoredDemoDependenciesSaveNormalCredentialsToTheAccountStoreAfterLogout() = runBlocking {
        context.demoPreferences.edit().putBoolean("demo_session_active", true)
            .putString(AppPrefs.KW_ID, DemoSession.ACCOUNT_ID).commit()
        DemoSession.restore()
        assertSame(context.demoPreferences, context.appPreferences)
        val dependencies = AndroidAppDependencies(context)
        val credentialStore = dependencies.credentialStore
        context.demoPreferences.edit().clear().commit()
        DemoSession.end()

        val credential = StoredCredential("2099123456", SecretValue.of("demo-test-encrypted-token"))
        credentialStore.save(credential)
        context.appPreferences.edit().putString(AppPrefs.YEAR_HAKGI, "2099,2").commit()
        assertSame(context.accountPreferences, context.appPreferences)
        assertEquals(credential.accountId, context.accountPreferences.getString(AppPrefs.KW_ID, null))
        assertEquals("2099,2", context.accountPreferences.getString(AppPrefs.YEAR_HAKGI, null))
        assertFalse(context.demoPreferences.contains(AppPrefs.KW_ID))
        assertFalse(context.demoPreferences.contains(AppPrefs.YEAR_HAKGI))
        val restarted = AndroidAppDependencies(context.recreated())
        assertEquals(credential, restarted.credentialStore.load())
    }

    @Test fun widgetObservesAccountChangesAfterStartingInARestoredDemo() = runBlocking {
        val account = context.accountPreferences as TrackedPreferences
        val demo = context.demoPreferences as TrackedPreferences
        account.edit().putString(AppPrefs.KW_ID, "2099123456")
            .putString(AppPrefs.YEAR_HAKGI, "2099,1")
            .putString(LoginFunnelStatus.KEY, LoginFunnelStatus.COMPLETE).commit()
        DemoSession.restore()
        val runtime = AcademicWidgetRuntime(context)
        instrumentation.runOnMainSync { runtime.start(); runtime.start() }
        assertEquals(1, account.registrations)
        assertEquals(0, demo.registrations)
        instrumentation.runOnMainSync { DemoSession.end() }
        runtime.recordTimetable("2099,1", listOf(TimetableEntry("데모 테스트", 1, "09:00", "10:00", "", "SUBJ")))
        val store = AndroidAcademicWidgetSnapshotStore(context)
        assertEquals("2099,1", store.read()?.term)
        instrumentation.runOnMainSync { account.edit().putString(AppPrefs.YEAR_HAKGI, "2099,2").commit() }
        awaitCondition { store.read()?.term == "2099,2" }
        val previousOwner = store.read()?.owner
        instrumentation.runOnMainSync { account.edit().putString(AppPrefs.KW_ID, "2099654321").commit() }
        awaitCondition { store.read()?.let { it.owner != previousOwner && it.term == "2099,2" && it.timetable == null } == true }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("일반 계정 Preferences 변경이 위젯 캐시에 반영되지 않음")
    }
}

private class IsolatedPreferencesContext(
    base: Context,
    private val prefix: String = "demo-lifetime-${UUID.randomUUID()}-",
) : ContextWrapper(base) {
    private val stores = mutableMapOf<String, TrackedPreferences>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = stores.getOrPut(name) {
        TrackedPreferences(baseContext.getSharedPreferences(prefix + name, mode))
    }
    override fun getFilesDir(): File = File(baseContext.filesDir, prefix).apply { mkdirs() }
    override fun getNoBackupFilesDir(): File = File(baseContext.noBackupFilesDir, prefix).apply { mkdirs() }
    fun recreated() = IsolatedPreferencesContext(baseContext, prefix)
    fun clearTestData() {
        stores.keys.toList().forEach { baseContext.deleteSharedPreferences(prefix + it) }
        filesDir.deleteRecursively()
        noBackupFilesDir.deleteRecursively()
    }
}

private class TrackedPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
    var registrations = 0
        private set
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        registrations++
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }
}
