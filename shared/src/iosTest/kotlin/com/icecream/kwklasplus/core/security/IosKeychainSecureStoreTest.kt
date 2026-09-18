package com.icecream.kwklasplus.core.security

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosKeychainSecureStoreTest {
    @Test
    fun readWriteRemoveRoundTrip() = runSuspendTest {
        val store = IosKeychainSecureStore(service = "com.icecream.kwklasplus.test.keychain.${hashCode()}")
        val key = SecureKey.ENCRYPTED_KLAS_PASSWORD

        store.remove(key)
        assertNull(store.read(key))

        store.write(key, SecretValue.of("secret-value"))
        val written = store.read(key) ?: return@runSuspendTest
        assertEquals(SecretValue.of("secret-value"), written)

        store.write(key, SecretValue.of("updated-value"))
        assertEquals(SecretValue.of("updated-value"), store.read(key))

        store.remove(key)
        assertNull(store.read(key))
    }

    @Test
    fun academicSessionGroupIncludesOnlySessionAndEncryptedPassword() {
        assertEquals(
            setOf(SecureKey.SESSION_TOKEN, SecureKey.ENCRYPTED_KLAS_PASSWORD),
            IosKeychainSecureStore.ACADEMIC_SHARED_KEYS,
        )
    }
}
