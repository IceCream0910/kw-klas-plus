package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.InMemorySecureStore
import com.icecream.kwklasplus.core.session.runSuspendTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosCredentialStoreTest {
    private val suiteName = "com.icecream.kwklasplus.test.credentials.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun saveLoadClearRoundTrip() = runSuspendTest {
        val secrets = InMemorySecureStore()
        val store = IosCredentialStore(secrets, defaults)
        val credential = StoredCredential("2020123456", SecretValue.of("encrypted-pw"))

        store.save(credential)
        assertEquals(credential, store.load())
        assertEquals("2020123456", defaults.stringForKey("kwID"))
        assertNull(defaults.stringForKey("kwPWD"))
        assertEquals(SecretValue.of("encrypted-pw"), secrets.read(SecureKey.ENCRYPTED_KLAS_PASSWORD))

        store.clear()
        assertNull(store.load())
        assertNull(defaults.stringForKey("kwID"))
        assertNull(secrets.read(SecureKey.ENCRYPTED_KLAS_PASSWORD))
    }
}
