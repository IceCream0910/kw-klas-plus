package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.migration.LegacySecretRef
import com.icecream.kwklasplus.core.migration.LegacySecretSource
import com.icecream.kwklasplus.core.migration.SecureStoreMigrator
import com.icecream.kwklasplus.core.migration.androidLoginCredentialMigrations
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.testing.InMemorySharedPreferences
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidCredentialStoreTest {
    @Test
    fun clearPasswordPreservesAccountIdAndRemovesLegacyFallbacks() = runCredentialStoreTest {
        val preferences = InMemorySharedPreferences().apply {
            edit()
                .putString(LegacyPreferenceKeys.KW_ID, "2020123456")
                .putString(LegacyPreferenceKeys.KW_PASSWORD, "legacy-password")
                .commit()
        }
        val secrets = FakeSecureStore().apply {
            write(SecureKey.ENCRYPTED_KLAS_PASSWORD, SecretValue.of("encrypted-password"))
        }
        val legacySource = FakeLegacySecretSource().apply {
            androidLoginCredentialMigrations.forEach { migration ->
                values[migration.source] = SecretValue.of("legacy-password")
            }
        }
        val store = AndroidCredentialStore(
            preferences,
            secrets,
            SecureStoreMigrator(legacySource, secrets),
            legacySource,
        )

        store.clearPassword()

        assertEquals("2020123456", store.loadAccountId())
        assertNull(store.load())
        assertNull(secrets.read(SecureKey.ENCRYPTED_KLAS_PASSWORD))
        assertNull(preferences.getString(LegacyPreferenceKeys.KW_PASSWORD, null))
        androidLoginCredentialMigrations.forEach { migration ->
            assertNull(legacySource.values[migration.source])
        }
    }

    private class FakeSecureStore : SecureStore {
        private val values = mutableMapOf<SecureKey, SecretValue>()

        override suspend fun read(key: SecureKey): SecretValue? = values[key]
        override suspend fun write(key: SecureKey, value: SecretValue) {
            values[key] = value
        }
        override suspend fun remove(key: SecureKey) {
            values.remove(key)
        }
    }

    private class FakeLegacySecretSource : LegacySecretSource {
        val values = mutableMapOf<LegacySecretRef, SecretValue>()

        override suspend fun read(reference: LegacySecretRef): SecretValue? = values[reference]
        override suspend fun remove(reference: LegacySecretRef) {
            values.remove(reference)
        }
    }
}

private fun <T> runCredentialStoreTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
