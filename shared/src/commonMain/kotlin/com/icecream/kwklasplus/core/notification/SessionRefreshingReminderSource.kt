package com.icecream.kwklasplus.core.notification

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRefreshingReminderSource(
    private val delegate: ReminderDataSource,
    private val recover: suspend () -> ReminderSourceResult<Unit>,
) : ReminderDataSource {
    private val mutex = Mutex()
    override suspend fun deadlines(term: String) = request { delegate.deadlines(term) }

    private suspend fun <T> request(action: suspend () -> ReminderSourceResult<T>): ReminderSourceResult<T> = mutex.withLock {
        val result = action()
        if (result != ReminderSourceResult.NeedsLogin) return@withLock result
        when (recover()) {
            is ReminderSourceResult.Success -> action()
            ReminderSourceResult.NeedsLogin -> ReminderSourceResult.NeedsLogin
            ReminderSourceResult.Retry -> ReminderSourceResult.Retry
            ReminderSourceResult.UnverifiedSource -> ReminderSourceResult.UnverifiedSource
        }
    }
}
