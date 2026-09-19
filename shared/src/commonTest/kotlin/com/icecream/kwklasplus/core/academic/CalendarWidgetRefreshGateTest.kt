package com.icecream.kwklasplus.core.academic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarWidgetRefreshGateTest {
    @Test
    fun successDoesNotInvokeRetry() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarWidgetRefreshGate().run(
            refresh = { calls += "fetch"; false },
            onTimeoutOrRetry = { calls += "retry" },
            finish = { calls += "finish" },
        )
        assertEquals(listOf("fetch", "finish"), calls)
    }

    @Test
    fun retryFlagSchedulesFollowUp() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarWidgetRefreshGate().run(
            refresh = { calls += "fetch"; true },
            onTimeoutOrRetry = { calls += "retry" },
            finish = { calls += "finish" },
        )
        assertEquals(listOf("fetch", "retry", "finish"), calls)
    }

    @Test
    fun timeoutCancelsThenRetries() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarWidgetRefreshGate(50).run(
            refresh = { try { awaitCancellation() } finally { calls += "cancel" } },
            onTimeoutOrRetry = { calls += "retry" },
            finish = { calls += "finish" },
        )
        assertEquals(listOf("cancel", "retry", "finish"), calls)
    }

    @Test
    fun unexpectedFailureRetriesAndFinishes() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarWidgetRefreshGate().run(
            refresh = { error("fixture") },
            onTimeoutOrRetry = { calls += "retry" },
            finish = { calls += "finish" },
        )
        assertEquals(listOf("retry", "finish"), calls)
    }

    @Test
    fun externalCancellationIsNotSwallowed() = runBlocking {
        val calls = mutableListOf<String>()
        val entered = CompletableDeferred<Unit>()
        val job = async {
            CalendarWidgetRefreshGate().run(
                refresh = { entered.complete(Unit); awaitCancellation() },
                onTimeoutOrRetry = { calls += "retry" },
                finish = { calls += "finish" },
            )
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("finish"), calls)
    }
}
