package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SessionCoordinatorTest {
    private val token = SecretValue.of("session-token")

    @Test
    fun restoresSessionWithinLegacyOneHourWindow() = runSuspend {
        val store = FakeSessionStore(Session(token, 1_000L))
        val cookies = FakeCookieStore()
        val coordinator = SessionCoordinator(store, cookies, Clock { 3_600_999L })

        val result = coordinator.restore()

        assertIs<SessionResult.Active>(result)
        assertEquals(token, cookies.token)
    }

    @Test
    fun expiresSessionAtExactlyOneHour() = runSuspend {
        val store = FakeSessionStore(Session(token, 1_000L))
        val cookies = FakeCookieStore(token)
        val coordinator = SessionCoordinator(store, cookies, Clock { 3_601_000L })

        val result = coordinator.restore()

        assertEquals(SessionResult.Expired, result)
        assertNull(store.session)
        assertNull(cookies.token)
    }

    @Test
    fun rejectsSessionTimestampFromFuture() = runSuspend {
        val store = FakeSessionStore(Session(token, 2_000L))
        val cookies = FakeCookieStore(token)
        val coordinator = SessionCoordinator(store, cookies, Clock { 1_000L })

        assertEquals(SessionResult.Expired, coordinator.restore())
    }

    @Test
    fun rejectsNegativeLegacyTimestamp() {
        assertEquals(false, SessionPolicy().isUsable(-1L, 1_000L))
    }

    @Test
    fun rollsBackStoredSessionWhenCookieSynchronizationFails() = runSuspend {
        val previousToken = SecretValue.of("previous-token")
        val previous = Session(previousToken, 500L)
        val store = FakeSessionStore(previous)
        val cookies = FakeCookieStore(previousToken, failNextSet = true)
        val coordinator = SessionCoordinator(store, cookies, Clock { 1_000L })

        val result = coordinator.observe(token)

        assertIs<SessionResult.Failed>(result)
        assertEquals(previous, store.session)
        assertEquals(previousToken, cookies.token)
    }

    @Test
    fun secretNeverAppearsInStringRepresentation() {
        assertEquals("[REDACTED]", token.toString())
        assertEquals("[REDACTED]", Session(token, 1L).token.toString())
    }

    private class FakeSessionStore(var session: Session? = null) : SessionStore {
        override suspend fun load(): Session? = session
        override suspend fun save(session: Session) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeCookieStore(
        var token: SecretValue? = null,
        private var failNextSet: Boolean = false,
    ) : WebCookieStore {
        override suspend fun setSessionCookie(token: SecretValue) {
            if (failNextSet) {
                failNextSet = false
                error("cookie synchronization failed")
            }
            this.token = token
        }

        override suspend fun clearSessionCookie() {
            token = null
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
