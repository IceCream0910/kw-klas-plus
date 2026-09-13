package com.icecream.kwklasplus.widget

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CalendarInitialRefreshTest {
    @Test fun fetchesBeforeFinishingWithoutSchedulingOnSuccess() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarInitialRefresh().run(
            { calls += "fetch"; false }, { calls += "schedule" }, { calls += "finish" },
        )
        assertEquals(listOf("fetch", "finish"), calls)
    }

    @Test fun failedFetchSchedulesAnotherAttempt() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarInitialRefresh().run(
            { calls += "fetch"; true }, { calls += "schedule" }, { calls += "finish" },
        )
        assertEquals(listOf("fetch", "schedule", "finish"), calls)
    }

    @Test fun timeoutCancelsFetchThenSchedulesAndReleasesBroadcast() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarInitialRefresh(50).run(
            { try { awaitCancellation() } finally { calls += "cancel" } },
            { calls += "schedule" }, { calls += "finish" },
        )
        assertEquals(listOf("cancel", "schedule", "finish"), calls)
    }

    @Test fun unexpectedFailureStillReleasesBroadcast() = runBlocking {
        val calls = mutableListOf<String>()
        CalendarInitialRefresh().run(
            { error("fixture") }, { calls += "schedule" }, { calls += "finish" },
        )
        assertEquals(listOf("schedule", "finish"), calls)
    }

    @Test fun externalCancellationIsNotSwallowed() = runBlocking {
        val calls = mutableListOf<String>()
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            CalendarInitialRefresh().run(
                { entered.complete(Unit); awaitCancellation() },
                { calls += "schedule" }, { calls += "finish" },
            )
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf("finish"), calls)
    }
}
