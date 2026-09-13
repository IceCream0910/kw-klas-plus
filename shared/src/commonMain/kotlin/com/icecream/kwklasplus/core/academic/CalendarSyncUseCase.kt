package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.auth.*
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.*

sealed interface CalendarSyncResult {
    data class Success(val events: List<CalendarEvent>) : CalendarSyncResult
    data object NeedsLogin : CalendarSyncResult
    data object Retry : CalendarSyncResult
}

class CalendarSyncUseCase(
    private val leases: SessionLeaseGateway,
    private val auth: WebAuthDriver,
    private val repository: CalendarRepository,
    private val sessionCoordinator: SessionCoordinator,
) {
    suspend fun sync(credential: StoredCredential?, token: SecretValue?, ua: KlasUserAgent,
                     start: String, end: String): CalendarSyncResult {
        if (token != null) {
            when (val result = repository.fetch(token, ua, start, end)) {
                is CalendarResult.Success -> return CalendarSyncResult.Success(result.events)
                CalendarResult.SessionExpired -> Unit
                else -> return CalendarSyncResult.Retry
            }
        }
        if (credential == null) return CalendarSyncResult.NeedsLogin
        return when (val login = auth.authenticate(credential)) {
            is WebAuthResult.Failure -> when (login.failure) {
                AuthFailure.CaptchaRequired, AuthFailure.TemporaryPasswordChangeRequired,
                AuthFailure.InvalidCredentials, AuthFailure.UserCancelled -> CalendarSyncResult.NeedsLogin
                else -> CalendarSyncResult.Retry
            }
            is WebAuthResult.SessionObserved -> when (val info = leases.fetchInfo(login.token, ua)) {
                is SessionInfoResult.Success -> if (info.info.remainingSeconds > 0) {
                    when (val session = sessionCoordinator.observe(login.token)) {
                        is SessionResult.Active -> when (val result = repository.fetch(session.session.token, ua, start, end)) {
                            is CalendarResult.Success -> CalendarSyncResult.Success(result.events)
                            CalendarResult.SessionExpired -> CalendarSyncResult.NeedsLogin
                            else -> CalendarSyncResult.Retry
                        }
                        else -> CalendarSyncResult.Retry
                    }
                } else CalendarSyncResult.NeedsLogin
                SessionInfoResult.SessionExpired -> CalendarSyncResult.NeedsLogin
                else -> CalendarSyncResult.Retry
            }
        }
    }
}
