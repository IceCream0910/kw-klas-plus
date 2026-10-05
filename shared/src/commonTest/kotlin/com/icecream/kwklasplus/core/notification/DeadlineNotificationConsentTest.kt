package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.bridge.*
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DeadlineNotificationConsentTest {
    private class Fixture {
        var raw: String?=null
        var fail=false
        var permission="denied"
        var owner="fixture"
        var ready=true
        var canConfigure=true
        var fetches=0
        var cancelled=0
        var changed=0
        var attempt=0L
        val platform=object: ReminderPlatform {
            override suspend fun permission(kind: String)=permission
            override suspend fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,message: ReminderMessage)=true
            override suspend fun cancel(id: String) {}
            override suspend fun cancelAll() { cancelled++ }
            override fun hash(value: String)=value
        }
        val engine=ReminderEngine(object: ReminderStore {
            override suspend fun read()=raw
            override suspend fun write(value: String) { check(!fail);raw=value }
        },platform,ReminderIdentityProvider { ReminderIdentity(owner,"2026,2",ready,canConfigure) },object: ReminderDataSource {
            override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> { fetches++;return ReminderSourceResult.Success(emptyList()) }
        },Clock { 1000 })
        val consent=DeadlineNotificationConsent(engine) { changed++ }
        val ui=ReminderSettingsUi { attempt=it;true }
    }
    @Test fun completedAttemptIsIdempotentAcrossRecreationAndCannotReviveDisabledSetting()=runBlocking {
        val f=Fixture();f.permission="authorized";f.consent.request(true,f.ui)
        assertEquals("COMPLETED",f.consent.complete(f.attempt))
        assertEquals("COMPLETED",f.consent.complete(f.attempt));assertEquals(1,f.changed)
        f.consent.request(false,f.ui)
        assertEquals("CANCELLED",f.consent.complete(f.attempt));assertFalse(f.engine.snapshot().deadlineEnabled)
    }
    @Test fun setupCanSaveConsentButCannotRefreshUntilFunnelCompletes()=runBlocking {
        val f=Fixture();f.ready=false;f.permission="authorized"
        assertTrue(f.consent.request(true,f.ui).pending)
        assertEquals("COMPLETED",f.consent.complete(f.attempt))
        assertTrue(f.engine.snapshot().deadlineEnabled)
        f.engine.refresh();assertEquals(0,f.fetches)
        f.ready=true;f.engine.refresh();assertEquals(1,f.fetches)
    }
    @Test fun leavingAuthenticatedSetupCancelsPermissionAttempt()=runBlocking {
        val f=Fixture();f.ready=false;f.consent.request(true,f.ui)
        f.canConfigure=false;f.permission="authorized"
        assertEquals("CANCELLED",f.consent.complete(f.attempt))
        assertFalse(f.engine.snapshot().deadlineEnabled)
        assertFailsWith<IllegalStateException> { f.consent.request(true,f.ui) }
        Unit
    }
    @Test fun enablingOnlyOpensSheetAndNativeConfirmationNeedsPermissionAndSave()=runBlocking {
        val f=Fixture();val begun=f.consent.request(true,f.ui)
        assertTrue(begun.pending);assertFalse(begun.enabled);assertFalse(f.engine.snapshot().deadlineEnabled)
        assertEquals("PERMISSION_DENIED",f.consent.complete(f.attempt));assertFalse(f.engine.snapshot().deadlineEnabled)
        f.permission="authorized"
        assertEquals("COMPLETED",f.consent.complete(f.attempt));assertTrue(f.consent.state().enabled);assertFalse(f.consent.state().pending)
        assertEquals(1,f.changed)
        f.consent.request(false,f.ui);assertFalse(f.engine.snapshot().deadlineEnabled);assertTrue(f.cancelled>0)
    }
    @Test fun deniedCancelledAndFailedSheetRemainOff()=runBlocking {
        val f=Fixture();f.consent.request(true,f.ui);f.consent.cancel(f.attempt)
        f.permission="authorized";assertEquals("CANCELLED",f.consent.complete(f.attempt));assertFalse(f.consent.state().enabled)
        assertEquals("OPEN_FAILED",f.consent.request(true,ReminderSettingsUi { false }).status)
        assertFalse(f.consent.state().pending)
        assertEquals("OPEN_FAILED",f.consent.request(true,ReminderSettingsUi { error("launch failed") }).status)
        assertFalse(f.consent.state().pending)
    }
    @Test fun saveFailureAllowsRetryWithoutFalseSuccess()=runBlocking {
        val f=Fixture();f.consent.request(true,f.ui);f.permission="authorized";f.fail=true
        assertFailsWith<IllegalStateException> { f.consent.complete(f.attempt) }
        assertFalse(f.engine.snapshot().deadlineEnabled);assertTrue(f.consent.state().pending)
        f.fail=false;assertEquals("COMPLETED",f.consent.complete(f.attempt))
    }
    @Test fun oldSheetCannotCompleteAfterOffNewAttemptOrAccountChange()=runBlocking {
        val f=Fixture();f.consent.request(true,f.ui);val old=f.attempt
        f.consent.request(false,f.ui);f.consent.request(true,f.ui);f.permission="authorized"
        assertEquals("CANCELLED",f.consent.complete(old));assertFalse(f.engine.snapshot().deadlineEnabled)
        f.owner="other";assertEquals("CANCELLED",f.consent.complete(f.attempt));assertFalse(f.consent.state().pending)
    }
    @Test fun provisionalPermissionCanCompleteButRevokedPermissionReadsOff()=runBlocking {
        val f=Fixture();f.permission="provisional";f.consent.request(true,f.ui)
        assertFalse(f.consent.state().enabled);assertEquals("COMPLETED",f.consent.complete(f.attempt))
        f.permission="denied";assertFalse(f.consent.state().enabled)
    }
}
