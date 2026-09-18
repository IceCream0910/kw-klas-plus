package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import platform.Foundation.NSUserDefaults

class IosUserDefaultsSessionTimestampStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SessionTimestampStore {
    override suspend fun read(): Long? {
        val raw = defaults.stringForKey(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP) ?: return null
        return raw.toLongOrNull()
    }

    override suspend fun write(value: Long) {
        defaults.setObject(value.toString(), LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        defaults.synchronize()
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        defaults.synchronize()
    }
}
