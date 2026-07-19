package com.icecream.kwklasplus.core.migration

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.testing.InMemorySharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidLegacySecretSourceTest {
    @Test
    fun resolvesInjectedStoreAndRemovesMigratedValue() = runBlocking {
        val preferences = LegacyStoreId.entries.associateWith { InMemorySharedPreferences() }
        preferences.getValue(LegacyStoreId.ENCRYPTED_PREFERENCES).edit()
            .putString(LegacyPreferenceKeys.KW_PASSWORD, "encrypted")
            .commit()
        val source = AndroidLegacySecretSource(preferences::getValue)
        val reference = androidLoginCredentialMigrations.first().source

        assertEquals("encrypted", source.read(reference)?.reveal())
        source.remove(reference)
        assertFalse(preferences.getValue(reference.store).contains(reference.key))
    }

    @Test
    fun migrationTargetsKeepReleasedStorageContract() {
        assertEquals(
            listOf(SecureKey.ENCRYPTED_KLAS_PASSWORD, SecureKey.ENCRYPTED_KLAS_PASSWORD),
            androidLoginCredentialMigrations.map(SecretMigration::target),
        )
        assertEquals(
            setOf(SecureKey.SESSION_TOKEN, SecureKey.APP_LOCK_HASH, SecureKey.APP_LOCK_SALT),
            androidFixedSecretMigrations.drop(androidLoginCredentialMigrations.size)
                .map(SecretMigration::target)
                .toSet(),
        )
    }
}
