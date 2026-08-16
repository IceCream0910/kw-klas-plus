package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosSessionStoresTest {
    @Test
    fun setGetClearSessionCookieRoundTrip() = runSuspendTest {
        val store = IosWebCookieStore.createForTests(InMemoryHttpCookieStoreOps())
        val token = SecretValue.of("ios-session-token")

        store.setSessionCookie(token)
        assertEquals("ios-session-token", store.readSessionCookieValue())

        store.clearSessionCookie()
        assertNull(store.readSessionCookieValue())
    }

    @Test
    fun setSessionCookieUsesExpectedDomainAndPath() = runSuspendTest {
        val store = IosWebCookieStore.createForTests(InMemoryHttpCookieStoreOps())
        store.setSessionCookie(SecretValue.of("domain-check-token"))

        val cookies = store.sessionCookies()
        assertTrue(cookies.isNotEmpty())
        val cookie = cookies.first()
        assertEquals("SESSION", cookie.name)
        assertTrue(
            cookie.domain == ".kw.ac.kr" || cookie.domain == "kw.ac.kr",
            "domain=${cookie.domain}",
        )
        assertEquals("/", cookie.path)
        assertTrue(cookie.isSecure())
        assertTrue(cookie.isHTTPOnly())

        store.clearSessionCookie()
    }

    @Test
    fun sessionCookieFactoryMatchesAndroidSetCookieContract() {
        val cookie = SessionCookieFactory.create("factory-token")
        assertEquals("SESSION", cookie.name)
        assertEquals("factory-token", cookie.value)
        assertTrue(
            cookie.domain == ".kw.ac.kr" || cookie.domain == "kw.ac.kr",
            "domain=${cookie.domain}",
        )
        assertEquals("/", cookie.path)
        assertTrue(cookie.isSecure())
        assertTrue(cookie.isHTTPOnly())

        val expired = SessionCookieFactory.createExpired()
        assertEquals("SESSION", expired.name)
        assertTrue(expired.value.isEmpty())
    }
}
