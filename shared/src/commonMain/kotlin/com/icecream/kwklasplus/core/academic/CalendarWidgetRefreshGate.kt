package com.icecream.kwklasplus.core.academic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class CalendarWidgetRefreshGate(private val timeoutMillis: Long = 8_000) {
    suspend fun run(refresh: suspend () -> Boolean, onTimeoutOrRetry: () -> Unit, finish: () -> Unit) {
        try {
            if (withTimeoutOrNull(timeoutMillis) { refresh() } != false) onTimeoutOrRetry()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            onTimeoutOrRetry()
        } finally {
            finish()
        }
    }
}
