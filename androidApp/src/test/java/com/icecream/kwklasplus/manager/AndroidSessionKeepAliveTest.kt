package com.icecream.kwklasplus.manager

import com.icecream.kwklasplus.core.session.SessionLeaseMaintainer
import com.icecream.kwklasplus.core.session.SessionLeaseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSessionKeepAliveTest {
    @Test
    fun startsOnlyWhenForegroundAndSessionAreBothAvailable() = runBlocking {
        var calls = 0
        val expired = CompletableDeferred<Unit>()
        val keepAlive = AndroidSessionKeepAlive(
            maintainer = SessionLeaseMaintainer {
                calls += 1
                SessionLeaseResult.Expired
            },
            onExpired = { expired.complete(Unit) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            callbackDispatcher = Dispatchers.Unconfined,
        )

        keepAlive.onForeground()
        assertEquals(0, calls)
        keepAlive.onSessionAvailable()
        withTimeout(1_000L) { expired.await() }

        assertEquals(1, calls)
    }

    @Test
    fun clearedSessionDoesNotRestartWhileAppRemainsForeground() = runBlocking {
        var calls = 0
        val firstCheck = CompletableDeferred<Unit>()
        val keepAlive = AndroidSessionKeepAlive(
            maintainer = SessionLeaseMaintainer {
                calls += 1
                SessionLeaseResult.Active(
                    info = com.icecream.kwklasplus.core.session.SessionLeaseInfo(300L, 6_900L, 7_200L),
                    nextCheckAfterMillis = 1_000L,
                    extended = false,
                )
            },
            onExpired = {},
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            wait = { firstCheck.complete(Unit); CompletableDeferred<Unit>().await() },
            callbackDispatcher = Dispatchers.Unconfined,
        )

        keepAlive.onForeground()
        keepAlive.onSessionAvailable()
        withTimeout(1_000L) { firstCheck.await() }
        keepAlive.onSessionCleared()
        keepAlive.onForeground()

        assertEquals(1, calls)
    }

    @Test
    fun delaysFirstCheckWhenStartupAlreadyValidatedSession() = runBlocking {
        var calls = 0
        val requestedDelay = CompletableDeferred<Long>()
        val keepAlive = AndroidSessionKeepAlive(
            maintainer = SessionLeaseMaintainer {
                calls += 1
                SessionLeaseResult.Missing
            },
            onExpired = {},
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            wait = { requestedDelay.complete(it); CompletableDeferred<Unit>().await() },
            callbackDispatcher = Dispatchers.Unconfined,
        )

        keepAlive.onForeground()
        keepAlive.onSessionAvailable(initialDelayMillis = 30_000L)

        assertEquals(30_000L, withTimeout(1_000L) { requestedDelay.await() })
        assertEquals(0, calls)
        keepAlive.onSessionCleared()
    }
}
