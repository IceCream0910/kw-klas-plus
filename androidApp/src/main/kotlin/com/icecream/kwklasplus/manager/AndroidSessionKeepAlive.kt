package com.icecream.kwklasplus.manager

import com.icecream.kwklasplus.core.session.SessionLeaseMaintainer
import com.icecream.kwklasplus.core.session.SessionLeaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidSessionKeepAlive(
    private val maintainer: SessionLeaseMaintainer,
    private val onExpired: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val stateLock = Any()
    private var loopJob: Job? = null
    private var isForeground = false
    private var hasSession = false
    private var initialDelayMillis = 0L

    fun onForeground() {
        synchronized(stateLock) {
            isForeground = true
            startLoopLocked()
        }
    }

    fun onBackground() {
        synchronized(stateLock) {
            isForeground = false
            loopJob?.cancel()
            loopJob = null
        }
    }

    fun onSessionAvailable(initialDelayMillis: Long = 0L) {
        synchronized(stateLock) {
            hasSession = true
            this.initialDelayMillis = initialDelayMillis
            startLoopLocked()
        }
    }

    fun onSessionCleared() {
        synchronized(stateLock) {
            hasSession = false
            loopJob?.cancel()
            loopJob = null
        }
    }

    private fun startLoopLocked() {
        if (!isForeground || !hasSession || loopJob?.isActive == true) return
        val delayBeforeFirstCheck = initialDelayMillis
        initialDelayMillis = 0L
        loopJob = scope.launch {
            if (delayBeforeFirstCheck > 0L) {
                wait(delayBeforeFirstCheck)
            }
            while (isActive) {
                when (val result = maintainer.maintain()) {
                    is SessionLeaseResult.Active -> wait(result.nextCheckAfterMillis)
                    SessionLeaseResult.Missing -> {
                        markSessionUnavailable()
                        return@launch
                    }
                    SessionLeaseResult.Expired -> {
                        markSessionUnavailable()
                        withContext(callbackDispatcher) { onExpired() }
                        return@launch
                    }
                    is SessionLeaseResult.Retry -> wait(result.retryAfterMillis)
                }
            }
        }
    }

    private fun markSessionUnavailable() {
        synchronized(stateLock) {
            hasSession = false
            loopJob = null
        }
    }
}
