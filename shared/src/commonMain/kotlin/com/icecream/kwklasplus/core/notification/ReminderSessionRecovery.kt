package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.auth.*
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.session.*

class ReminderSessionRecovery(private val leases: SessionLeaseGateway, private val auth: WebAuthDriver, private val sessions: SessionCoordinator) {
    suspend fun recover(loadCredential: suspend () -> StoredCredential?, ua: KlasUserAgent): ReminderSourceResult<Unit> {
        val checkpoint=sessions.checkpoint()
        val credential=loadCredential()
        if(credential==null)return ReminderSourceResult.NeedsLogin
        return when(val login=auth.authenticate(credential)) {
            is WebAuthResult.Failure -> when(login.failure) {
                AuthFailure.CaptchaRequired, AuthFailure.TemporaryPasswordChangeRequired,
                AuthFailure.InvalidCredentials, AuthFailure.UserCancelled -> ReminderSourceResult.NeedsLogin
                else -> ReminderSourceResult.Retry
            }
            is WebAuthResult.SessionObserved -> when(val info=leases.fetchInfo(login.token,ua)) {
                is SessionInfoResult.Success -> if(info.info.remainingSeconds<=0)ReminderSourceResult.NeedsLogin else when(sessions.observeIfCurrent(login.token,checkpoint)) {
                    is SessionResult.Active -> ReminderSourceResult.Success(Unit)
                    SessionResult.Expired -> ReminderSourceResult.NeedsLogin
                    else -> ReminderSourceResult.Retry
                }
                SessionInfoResult.SessionExpired -> ReminderSourceResult.NeedsLogin
                else -> ReminderSourceResult.Retry
            }
        }
    }
}
