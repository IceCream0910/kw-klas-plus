package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHTTPCookie
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosSessionStoresTest {
    @Test
    fun setGetClearSessionCookieRoundTrip() = runSuspend {
        val store = IosWebCookieStore.createForTests(InMemoryHttpCookieStoreOps())
        val token = SecretValue.of("ios-session-token")

        store.setSessionCookie(token)
        assertEquals("ios-session-token", store.readSessionCookieValue())

        store.clearSessionCookie()
        assertNull(store.readSessionCookieValue())
    }

    @Test
    fun setSessionCookieUsesExpectedDomainAndPath() = runSuspend {
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

@OptIn(ExperimentalForeignApi::class)
private class InMemoryHttpCookieStoreOps : HttpCookieStoreOps {
    private val cookies = mutableListOf<NSHTTPCookie>()

    override fun setCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
        cookies.add(cookie)
        onComplete()
    }

    override fun deleteCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
        onComplete()
    }

    override fun getAllCookies(onComplete: (List<NSHTTPCookie>) -> Unit) {
        onComplete(cookies.toList())
    }
}

// InMemoryHttpCookieStoreOps는 동기 completion이라 startCoroutine만으로 충분하다.
private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return requireNotNull(outcome) { "suspend block did not complete synchronously" }.getOrThrow()
}
