package com.icecream.kwklasplus.core.platform

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.session.runSuspendTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosUserDefaultsPreferencesStoreTest {
    private val suiteName = "com.icecream.kwklasplus.test.prefs.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun yearHakgiUsesLegacyKey() = runSuspendTest {
        val store = IosUserDefaultsPreferencesStore(defaults)
        store.write(PreferenceKey.YEAR_SEMESTER, "2026,1")
        assertEquals("2026,1", defaults.stringForKey(LegacyPreferenceKeys.YEAR_HAKGI))
        assertEquals("2026,1", store.read(PreferenceKey.YEAR_SEMESTER))
        store.remove(PreferenceKey.YEAR_SEMESTER)
        assertNull(store.read(PreferenceKey.YEAR_SEMESTER))
    }
}
