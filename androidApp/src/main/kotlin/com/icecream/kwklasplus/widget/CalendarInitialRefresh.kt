package com.icecream.kwklasplus.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class CalendarInitialRefresh(private val timeoutMillis: Long = 8_000) {
    suspend fun run(refresh: suspend () -> Boolean, scheduleRetry: () -> Unit, finish: () -> Unit) {
        try {
            if (withTimeoutOrNull(timeoutMillis) { refresh() } != false) scheduleRetry()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            scheduleRetry()
        } finally {
            finish()
        }
    }
}
