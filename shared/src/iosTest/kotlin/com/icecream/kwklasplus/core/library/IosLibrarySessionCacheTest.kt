package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.session.InMemorySecureStore
import com.icecream.kwklasplus.core.session.runSuspendTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosLibrarySessionCacheTest {
    private val suiteName = "com.icecream.kwklasplus.test.library.cache.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
    private val identity = LibraryCacheIdentity("02020123456", "123")

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun writesSecretAndExpiresAfterTtl() = runSuspendTest {
        val secrets = InMemoryLibraryAccountSecrets()
        val cache = IosLibrarySessionCache(secrets, defaults, Clock { 1_000L })
        cache.writeSecret(identity, SecretValue.of("secret"))

        assertEquals("secret", cache.readSecret(identity)?.reveal())
        assertEquals(
            "secret_${identity.realId}_${identity.userInfoHash}",
            defaults.stringForKey(IosLibrarySessionCache.TRACKED_SECRET),
        )

        val expired = IosLibrarySessionCache(
            secrets,
            defaults,
            Clock { 31L * 24L * 60L * 60L * 1_000L },
        )
        assertNull(expired.readSecret(identity))
        assertNull(secrets.readAccount("secret_${identity.realId}_${identity.userInfoHash}"))
    }

    @Test
    fun identityMissDoesNotReturnOtherAccount() = runSuspendTest {
        val secrets = InMemoryLibraryAccountSecrets()
        val cache = IosLibrarySessionCache(secrets, defaults, Clock { 1_000L })
        cache.writeAuthKey(identity, SecretValue.of("auth"))

        val other = LibraryCacheIdentity("09999999999", "999")
        assertNull(cache.readAuthKey(other))
        assertEquals("auth", cache.readAuthKey(identity)?.reveal())
    }

    @Test
    fun clearTrackedRemovesLastWrittenKeys() = runSuspendTest {
        val secrets = InMemoryLibraryAccountSecrets()
        val cache = IosLibrarySessionCache(secrets, defaults, Clock { 1_000L })
        cache.writeSecret(identity, SecretValue.of("secret"))
        cache.writeAuthKey(identity, SecretValue.of("auth"))
        cache.clearTracked()

        assertNull(cache.readSecret(identity))
        assertNull(cache.readAuthKey(identity))
        assertNull(defaults.stringForKey(IosLibrarySessionCache.TRACKED_SECRET))
        assertNull(defaults.stringForKey(IosLibrarySessionCache.TRACKED_AUTH))
    }
}

class IosLibraryServiceClearTest {
    private val suiteName = "com.icecream.kwklasplus.test.library.service.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun clearAllRemovesCredentialsAndSessionCache() = runSuspendTest {
        val secrets = InMemoryLibraryAccountSecrets()
        val secureStore = InMemorySecureStore()
        val cache = IosLibrarySessionCache(secrets, defaults, Clock { 1_000L })
        val service = IosLibraryService(
            gateway = RejectingLibraryGateway(),
            defaults = defaults,
            secureStore = secureStore,
            cache = cache,
        )
        service.saveCredentials("2020123456", "01012345678", "lib-pass") {}
        secureStore.write(SecureKey.LIBRARY_PASSWORD, SecretValue.of("lib-pass"))
        defaults.setObject("2020123456", LegacyPreferenceKeys.LIBRARY_STD_NUMBER)
        defaults.setObject("01012345678", LegacyPreferenceKeys.LIBRARY_PHONE)
        cache.writeSecret(
            LibraryCacheIdentity.from(
                LibraryCredentials("2020123456", "01012345678", SecretValue.of("lib-pass")),
            ),
            SecretValue.of("secret"),
        )

        service.clearAll()

        assertFalse(service.hasConfiguredCredentials())
        assertNull(defaults.stringForKey(LegacyPreferenceKeys.LIBRARY_STD_NUMBER))
        assertNull(defaults.stringForKey(LegacyPreferenceKeys.LIBRARY_PHONE))
        assertNull(secureStore.read(SecureKey.LIBRARY_PASSWORD))
        assertTrue(service.settingsStudentNumber().isEmpty() || service.settingsStudentNumber() == defaults.stringForKey(LegacyPreferenceKeys.KW_ID).orEmpty())
    }

    @Test
    fun saveCredentialsClearsPreviousIdentitySessionCache() = runSuspendTest {
        val secrets = InMemoryLibraryAccountSecrets()
        val secureStore = InMemorySecureStore()
        val cache = IosLibrarySessionCache(secrets, defaults, Clock { 1_000L })
        val service = IosLibraryService(
            gateway = RejectingLibraryGateway(),
            defaults = defaults,
            secureStore = secureStore,
            cache = cache,
        )
        val oldCredentials = LibraryCredentials("2020123456", "01012345678", SecretValue.of("old-pass"))
        val oldIdentity = LibraryCacheIdentity.from(oldCredentials)
        service.saveCredentials("2020123456", "01012345678", "old-pass")
        cache.writeSecret(oldIdentity, SecretValue.of("old-secret"))
        cache.writeAuthKey(oldIdentity, SecretValue.of("old-auth"))

        assertEquals("old-secret", cache.readSecret(oldIdentity)?.reveal())
        assertEquals("old-auth", cache.readAuthKey(oldIdentity)?.reveal())

        service.saveCredentials("2020123456", "01099998888", "new-pass")

        assertNull(cache.readSecret(oldIdentity))
        assertNull(cache.readAuthKey(oldIdentity))
        assertNull(secrets.readAccount("secret_${oldIdentity.realId}_${oldIdentity.userInfoHash}"))
        assertNull(secrets.readAccount("authKey_${oldIdentity.realId}_${oldIdentity.userInfoHash}"))
    }
}

private class InMemoryLibraryAccountSecrets : LibraryAccountSecretStore {
    private val values = mutableMapOf<String, SecretValue>()

    override fun readAccount(account: String) = values[account]

    override fun writeAccount(account: String, value: SecretValue) {
        values[account] = value
    }

    override fun removeAccount(account: String) {
        values.remove(account)
    }
}

private class RejectingLibraryGateway : LibraryGateway {
    override suspend fun requestSecret(encodedRealId: String) = LibraryGatewayResult.NetworkFailure

    override suspend fun login(
        encodedRealId: String,
        encodedStudentNumber: String,
        phoneNumber: String,
        encryptedPassword: String,
        deviceCode: String,
    ) = LibraryGatewayResult.NetworkFailure

    override suspend fun requestQr(
        encodedRealId: String,
        authKey: String,
    ) = LibraryGatewayResult.NetworkFailure
}
