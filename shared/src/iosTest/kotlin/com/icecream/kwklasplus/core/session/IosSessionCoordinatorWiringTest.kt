package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.SecretValue
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosSessionCoordinatorWiringTest {
    private val suiteName = "com.icecream.kwklasplus.test.session.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun observeRestoreExpireSyncsSecureStoreTimestampAndCookies() = runSuspendTest {
        val secrets = InMemorySecureStore()
        val cookies = IosWebCookieStore.createForTests(InMemoryHttpCookieStoreOps())
        val clock = Clock { 1_000_000L }
        val coordinator = SessionCoordinator(
            SecureSessionStore(secrets, IosUserDefaultsSessionTimestampStore(defaults)),
            cookies,
            clock,
        )

        val observed = coordinator.observe(SecretValue.of("session-token-1"))
        assertTrue(observed is SessionResult.Active)
        assertEquals("session-token-1", cookies.readSessionCookieValue())
        assertEquals(SecretValue.of("session-token-1"), secrets.read(SecureKey.SESSION_TOKEN))
        assertEquals("1000000", defaults.stringForKey("kwSESSION_timestamp"))
        assertNull(defaults.stringForKey("kwSESSION"))

        val restored = coordinator.restore()
        assertTrue(restored is SessionResult.Active)

        val expired = coordinator.expire()
        assertTrue(expired is SessionResult.Expired)
        assertNull(cookies.readSessionCookieValue())
        assertNull(secrets.read(SecureKey.SESSION_TOKEN))
        assertNull(defaults.stringForKey("kwSESSION_timestamp"))
    }

    @Test
    fun restoreExpiresStaleSession() = runSuspendTest {
        val secrets = InMemorySecureStore()
        val cookies = IosWebCookieStore.createForTests(InMemoryHttpCookieStoreOps())
        secrets.write(SecureKey.SESSION_TOKEN, SecretValue.of("stale"))
        defaults.setObject("1", "kwSESSION_timestamp")
        defaults.synchronize()

        val coordinator = SessionCoordinator(
            SecureSessionStore(secrets, IosUserDefaultsSessionTimestampStore(defaults)),
            cookies,
            Clock { SessionPolicy.LEGACY_SESSION_MAX_AGE_MILLIS + 2 },
        )
        val result = coordinator.restore()
        assertTrue(result is SessionResult.Expired)
        assertNull(secrets.read(SecureKey.SESSION_TOKEN))
    }
}
