package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.auth.*
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.*
import kotlinx.coroutines.*
import kotlin.test.*

class ReminderSessionRecoveryTest {
    private val ua=KlasUserAgent.fromPlatform("fixture")
    private val credential=StoredCredential("fixture",SecretValue.of("encrypted-fixture"))
    private class Fixture {
        var stored: Session?=null
        var cookie: SecretValue?=null
        var fail=false
        val sessions=SessionCoordinator(object: SessionStore {
            override suspend fun load()=stored
            override suspend fun save(session: Session) { check(!fail);stored=session }
            override suspend fun clear() { stored=null }
        },object: WebCookieStore {
            override suspend fun setSessionCookie(token: SecretValue) { cookie=token }
            override suspend fun clearSessionCookie() { cookie=null }
        },Clock { 1000 })
        fun recovery(auth: WebAuthDriver,info: SessionInfoResult=SessionInfoResult.Success(SessionLeaseInfo(60,300,1800)))=ReminderSessionRecovery(object: SessionLeaseGateway {
            override suspend fun fetchInfo(session: SecretValue,userAgent: KlasUserAgent)=info
            override suspend fun extend(session: SecretValue,userAgent: KlasUserAgent): SessionExtensionResult=error("not used")
        },auth,sessions)
    }
    @Test fun successfulLoginPromotesValidatedSessionWithoutCalendarRequest()=runBlocking {
        val f=Fixture();var calls=0
        val recovery=f.recovery(WebAuthDriver { calls++;WebAuthResult.SessionObserved(SecretValue.of("new-fixture")) })
        assertIs<ReminderSourceResult.Success<Unit>>(recovery.recover({credential},ua))
        assertEquals(1,calls);assertEquals("new-fixture",f.stored?.token?.reveal());assertEquals(f.stored?.token,f.cookie)
    }
    @Test fun loginRequiredAndRetryAreDistinctAndFailedStorageDoesNotSucceed()=runBlocking {
        val f=Fixture()
        for(failure in listOf(AuthFailure.CaptchaRequired,AuthFailure.TemporaryPasswordChangeRequired,AuthFailure.InvalidCredentials,AuthFailure.UserCancelled)) {
            assertEquals(ReminderSourceResult.NeedsLogin,f.recovery(WebAuthDriver { WebAuthResult.Failure(failure) }).recover({credential},ua))
        }
        assertEquals(ReminderSourceResult.NeedsLogin,f.recovery(WebAuthDriver { error("no credential") }).recover({null},ua))
        f.fail=true
        assertEquals(ReminderSourceResult.Retry,f.recovery(WebAuthDriver { WebAuthResult.SessionObserved(SecretValue.of("new")) }).recover({credential},ua))
        assertNull(f.cookie)
    }
    @Test fun logoutAfterCredentialReadCannotRestoreOldSession()=runBlocking {
        val f=Fixture();val read=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val recovery=f.recovery(WebAuthDriver { WebAuthResult.SessionObserved(SecretValue.of("old-fixture")) })
        val task=async { recovery.recover({ read.complete(Unit);finish.await();credential },ua) }
        read.await();f.sessions.expire();finish.complete(Unit)
        assertEquals(ReminderSourceResult.NeedsLogin,task.await());assertNull(f.stored);assertNull(f.cookie)
    }
}
