package com.icecream.kwklasplus.core.platform

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import platform.Foundation.NSUserDefaults

class IosUserDefaultsPreferencesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PreferencesStore {
    override suspend fun read(key: PreferenceKey): String? =
        defaults.stringForKey(key.legacyName())?.takeIf(String::isNotBlank)

    override suspend fun write(key: PreferenceKey, value: String) {
        defaults.setObject(value, key.legacyName())
        defaults.synchronize()
    }

    override suspend fun remove(key: PreferenceKey) {
        defaults.removeObjectForKey(key.legacyName())
        defaults.synchronize()
    }

    private fun PreferenceKey.legacyName(): String = when (this) {
        PreferenceKey.ACCOUNT_ID -> LegacyPreferenceKeys.KW_ID
        PreferenceKey.APP_THEME -> LegacyPreferenceKeys.APP_THEME
        PreferenceKey.YEAR_SEMESTER -> LegacyPreferenceKeys.YEAR_HAKGI
        PreferenceKey.YEAR_SEMESTER_LIST -> LegacyPreferenceKeys.YEAR_HAKGI_LIST
        PreferenceKey.APP_LOCK_ENABLED -> "a_l_e"
        PreferenceKey.BIOMETRIC_ENABLED -> "b_m_e"
    }
}
