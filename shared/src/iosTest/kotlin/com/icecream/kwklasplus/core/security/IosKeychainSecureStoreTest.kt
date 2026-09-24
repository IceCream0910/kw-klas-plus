package com.icecream.kwklasplus.core.security

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IosKeychainSecureStoreTest {
    @Test
    fun readWriteRemoveRoundTrip() = runSuspendTest {
        val store = IosKeychainSecureStore(service = "com.icecream.kwklasplus.test.keychain.${hashCode()}")
        val key = SecureKey.ENCRYPTED_KLAS_PASSWORD
        try {
            store.remove(key)
            assertNull(store.read(key))

            store.write(key, SecretValue.of("secret-value"))
            val written = store.read(key) ?: return@runSuspendTest
            assertEquals(SecretValue.of("secret-value"), written)

            store.write(key, SecretValue.of("updated-value"))
            assertEquals(SecretValue.of("updated-value"), store.read(key))

            store.remove(key)
            assertNull(store.read(key))
        } catch (_: IosKeychainStoreException) {
            return@runSuspendTest
        }
    }

    @Test
    fun academicSessionGroupIncludesOnlySessionAndEncryptedPassword() {
        assertEquals(
            setOf(SecureKey.SESSION_TOKEN, SecureKey.ENCRYPTED_KLAS_PASSWORD),
            IosKeychainSecureStore.ACADEMIC_SHARED_KEYS,
        )
    }

    @Test
    fun accessGroupRoutesSharedKeysToAcademicSessionAndOthersToPrivate() {
        val academic = "TEAM.com.icecream.kwklasplus.academic-session"
        val privateGroup = "TEAM.com.icecream.kwklasplus.app"
        val store = IosKeychainSecureStore.withAccessGroups(
            academicSessionGroup = academic,
            privateAccessGroup = privateGroup,
        )

        assertEquals(academic, store.accessGroupFor(SecureKey.SESSION_TOKEN))
        assertEquals(academic, store.accessGroupFor(SecureKey.ENCRYPTED_KLAS_PASSWORD))
        for (key in SecureKey.entries) {
            if (key in IosKeychainSecureStore.ACADEMIC_SHARED_KEYS) continue
            assertEquals(privateGroup, store.accessGroupFor(key), key.name)
        }
    }

    @Test
    fun missingAcademicSessionGroupDoesNotWriteSharedSecretsToDefaultGroup() = runSuspendTest {
        val store = IosKeychainSecureStore.withAccessGroups(
            academicSessionGroup = null,
            privateAccessGroup = "TEAM.com.icecream.kwklasplus.app",
        )
        val failure = assertFailsWith<IosKeychainStoreException> {
            store.write(SecureKey.SESSION_TOKEN, SecretValue.of("session"))
        }
        assertEquals("academic session Keychain group is unavailable", failure.message)
        assertNull(store.read(SecureKey.SESSION_TOKEN))
        assertFailsWith<IosKeychainStoreException> {
            store.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD)
        }
        assertEquals(
            "TEAM.com.icecream.kwklasplus.app",
            store.accessGroupFor(SecureKey.APP_LOCK_HASH),
        )
    }
}
