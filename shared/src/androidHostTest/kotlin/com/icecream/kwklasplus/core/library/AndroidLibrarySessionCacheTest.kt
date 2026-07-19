package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.testing.InMemorySharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AndroidLibrarySessionCacheTest {
    private val identity = LibraryCacheIdentity("02020123456", "123")

    @Test
    fun migratesLegacyPlainCacheIntoEncryptedPreferences() = runBlocking {
        val legacy = InMemorySharedPreferences().apply {
            edit().putString(secretKey(), "secret").commit()
        }
        val encrypted = InMemorySharedPreferences()
        val cache = AndroidLibrarySessionCache(legacy, encrypted, Clock { 1_000L })

        assertEquals("secret", cache.readSecret(identity)?.reveal())
        assertFalse(legacy.contains(secretKey()))
        assertEquals("secret", encrypted.getString(secretKey(), null))
        assertEquals(1_000L, encrypted.getLong(timestampKey(), -1L))
    }

    @Test
    fun expiredSecretIsRemovedFromBothStores() = runBlocking {
        val legacy = InMemorySharedPreferences()
        val encrypted = InMemorySharedPreferences().apply {
            edit()
                .putString(secretKey(), "secret")
                .putLong(timestampKey(), 1L)
                .commit()
        }
        val now = 31L * 24L * 60L * 60L * 1_000L
        val cache = AndroidLibrarySessionCache(legacy, encrypted, Clock { now })

        assertNull(cache.readSecret(identity))
        assertFalse(encrypted.contains(secretKey()))
        assertFalse(encrypted.contains(timestampKey()))
    }

    private fun secretKey() = "secret_${identity.realId}_${identity.userInfoHash}"
    private fun timestampKey() = "${secretKey()}_savedAt"
}
