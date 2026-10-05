package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlin.test.*

class ReminderEngineTest {
    private class Store : ReminderStore {
        var raw: String?=null
        var fail=false
        var onWrite: () -> Unit = {}
        var failRead=false
        override suspend fun read(): String? { check(!failRead) { "STORAGE_FAILED" };return raw }
        override suspend fun write(value: String) { check(!fail) { "STORAGE_FAILED" };raw=value;onWrite() }
    }
    private class Platform : ReminderPlatform {
        var allowed=true
        var onPermission: () -> Unit = {}
        var onPost: () -> Unit = {}
        var postSucceeds=true
        var postAttempts=0
        val posted=mutableListOf<Pair<String,Boolean>>()
        val cancelled=mutableSetOf<String>()
        var cancelledAll=false
        val messages=mutableListOf<ReminderMessage>()
        override fun hash(value: String)=value
        override suspend fun permission(kind: String): String { onPermission();return if(allowed)"authorized" else "denied" }
        suspend fun post(id: String,kind: String,generation: Long,additional: Boolean): Boolean { posted+=id to additional;return true }
        override suspend fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,message: ReminderMessage): Boolean {
            postAttempts++;onPost()
            if(!postSucceeds)return false
            messages+=message;return post(id,kind,generation,additional)
        }
        override suspend fun cancel(id: String) { cancelled+=id }
        override suspend fun cancelAll() { cancelledAll=true }
    }
    private class Source : ReminderDataSource {
        var items=emptyList<ReminderDeadline>()
        var failure=false
        override suspend fun deadlines(term: String)=if(failure)ReminderSourceResult.Retry else ReminderSourceResult.Success(items)
    }
    private class Fixture {
        val store=Store();val platform=Platform();val source=Source()
        var now=ReminderTime.parse("2026-10-02 10:00:00")!!
        var owner="user"
        var term="2026,2"
        var ready=true
        var background=true
        fun engine()=ReminderEngine(store,platform,ReminderIdentityProvider { ReminderIdentity(owner,term,ready) },source,Clock { now },ReminderDeliveryGate { background })
        val engine=engine()
        suspend fun enable() { engine.setEnabled(true) }
        fun item(id: String)=ReminderDeadline(id,null,now+60*60*1000)
    }
    @Test fun failedOsPostDoesNotConsumeClaimAndNextRefreshRetries()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"))
        f.platform.postSucceeds=false;f.engine.refresh()
        assertEquals(1,f.platform.postAttempts)
        assertTrue(f.engine().snapshot().claims.isEmpty())
        assertEquals("SCHEDULE_FAILED",f.engine.snapshot().deadlineStatus)
        f.platform.postSucceeds=true;f.engine().refresh();f.engine().refresh()
        assertEquals(2,f.platform.postAttempts)
        assertEquals(listOf(false),f.platform.posted.map { it.second })
    }
    @Test fun staleResultDuringClaimSaveRestoresClaimsAndNextRefreshRetries()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.engine.refresh()
        val previous=f.engine.snapshot().claims
        f.source.items+=f.item("B")
        f.store.onWrite={ if(f.store.raw?.contains("POST_ATTEMPTED")==true)f.now+=60_001 }
        f.engine.refresh()
        assertEquals(1,f.platform.postAttempts)
        assertEquals(previous,f.engine().snapshot().claims)
        assertEquals("REFRESH_FAILED",f.engine.snapshot().deadlineStatus)
        f.store.onWrite={};f.engine().refresh();f.engine().refresh()
        assertEquals(listOf(false,true),f.platform.posted.map { it.second })
        assertEquals(setOf("user/B"),f.engine().snapshot().claims.last().keys)
    }
    @Test fun identityChangeDuringClaimSaveDoesNotConsumeUnpostedClaim()=runBlocking {
        for(change in listOf("term","owner","ready")) {
            val f=Fixture();f.enable();f.source.items=listOf(f.item("A"))
            f.store.onWrite={
                if(f.store.raw?.contains("POST_ATTEMPTED")==true)when(change) {
                    "term" -> f.term="2026,1"
                    "owner" -> f.owner="other"
                    else -> f.ready=false
                }
            }
            f.engine.refresh()
            assertEquals(0,f.platform.postAttempts)
            f.store.onWrite={};f.term="2026,2";f.owner="user";f.ready=true
            assertTrue(f.engine().snapshot().claims.isEmpty())
            f.engine().refresh();assertEquals(listOf(false),f.platform.posted.map { it.second })
        }
    }
    @Test fun itemsExpiringDuringClaimSaveDoNotConsumeAnyUnpostedClaims()=runBlocking {
        for(includeLater in listOf(false,true)) {
            val f=Fixture();f.enable()
            f.source.items=listOf(ReminderDeadline("A",null,f.now+1))+
                if(includeLater)listOf(f.item("B")) else emptyList()
            f.store.onWrite={ if(f.store.raw?.contains("POST_ATTEMPTED")==true)f.now+=2 }
            f.engine.refresh()
            assertEquals(0,f.platform.postAttempts)
            assertTrue(f.engine().snapshot().claims.isEmpty())
            f.store.onWrite={};f.source.items=listOf(f.item("A"))+
                if(includeLater)listOf(f.item("B")) else emptyList()
            f.engine().refresh()
            assertEquals(listOf(false),f.platform.posted.map { it.second })
            assertEquals(if(includeLater)setOf("user/A","user/B") else setOf("user/A"),f.engine().snapshot().claims.single().keys)
        }
    }
    @Test fun refusedOsPostRestoresPreviousClaimsAndRecordsSuppressionReason()=runBlocking {
        for(reason in listOf("FOREGROUND_SUPPRESSED","PERMISSION_BLOCKED","QUIET_HOURS")) {
            val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.engine.refresh()
            val previous=f.engine.snapshot().claims
            f.source.items+=f.item("B");f.platform.postSucceeds=false
            f.platform.onPost={
                when(reason) {
                    "FOREGROUND_SUPPRESSED" -> f.background=false
                    "PERMISSION_BLOCKED" -> f.platform.allowed=false
                    else -> f.now=ReminderTime.parse("2026-10-03 00:00:00")!!
                }
            }
            f.engine.refresh()
            assertEquals(previous,f.engine().snapshot().claims)
            assertEquals(reason,f.engine.snapshot().deadlineStatus)
            f.platform.onPost={};f.platform.postSucceeds=true;f.platform.allowed=true;f.background=true
            f.now=ReminderTime.parse("2026-10-02 10:00:00")!!
            f.engine().refresh()
            assertEquals(listOf(false,true),f.platform.posted.map { it.second })
            assertEquals(setOf("user/B"),f.engine().snapshot().claims.last().keys)
        }
    }
    @Test fun permissionReadFailureAfterRefusedPostStillRestoresClaim()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.platform.postSucceeds=false
        f.platform.onPost={ f.platform.onPermission={ error("permission unavailable") } }
        assertFailsWith<IllegalStateException> { f.engine.refresh() }
        assertTrue(f.engine().snapshot().claims.isEmpty())
        assertEquals("SCHEDULE_FAILED",f.engine.snapshot().deadlineStatus)
        f.platform.onPost={};f.platform.onPermission={};f.platform.postSucceeds=true
        f.engine().refresh();assertEquals(1,f.platform.posted.size)
    }
    @Test fun quietHoursDoNotConsumeClaimsAndEightAmRechecksLatestData()=runBlocking {
        val f=Fixture();f.enable()
        for(time in listOf("00:00:00","04:00:00","07:59:59")) {
            f.now=ReminderTime.parse("2026-10-03 $time")!!
            f.source.items=listOf(f.item("A"))
            f.engine.refresh()
            assertTrue(f.engine.snapshot().claims.isEmpty())
            assertEquals("QUIET_HOURS",f.engine.snapshot().deadlineStatus)
        }
        f.now=ReminderTime.parse("2026-10-03 08:00:00")!!
        f.source.items=listOf(f.item("B"));f.engine.refresh()
        assertEquals(1,f.platform.posted.size)
        assertEquals(setOf("user/B"),f.engine.snapshot().claims.single().keys)
    }
    @Test fun midnightDuringPermissionOrClaimSaveReleasesUnpostedClaim()=runBlocking {
        for(duringSave in listOf(false,true)) {
            val f=Fixture();f.enable();f.now=ReminderTime.parse("2026-10-02 23:59:59")!!
            f.source.items=listOf(f.item("A"))
            val midnight=ReminderTime.parse("2026-10-03 00:00:00")!!
            if(duringSave) f.store.onWrite={ if(f.store.raw?.contains("POST_ATTEMPTED")==true)f.now=midnight }
            else f.platform.onPermission={ f.now=midnight }
            f.engine.refresh()
            assertTrue(f.platform.posted.isEmpty());assertTrue(f.engine.snapshot().claims.isEmpty())
            assertEquals("QUIET_HOURS",f.engine.snapshot().deadlineStatus)
        }
    }
    @Test fun quietHoursUseKstRegardlessOfTimestampOffset() {
        assertFalse(ReminderTime.isQuietHours(ReminderTime.parse("2026-10-02T14:59:59Z")!!))
        assertTrue(ReminderTime.isQuietHours(ReminderTime.parse("2026-10-02T15:00:00Z")!!))
        assertTrue(ReminderTime.isQuietHours(ReminderTime.parse("2026-10-02T22:59:59Z")!!))
        assertFalse(ReminderTime.isQuietHours(ReminderTime.parse("2026-10-02T23:00:00Z")!!))
    }
    @Test fun unreadableStorePreservesDeliveredNotificationsButMalformedContentCancelsThem()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.engine.refresh()
        f.platform.cancelledAll=false;f.store.failRead=true
        assertFailsWith<IllegalStateException> { f.engine().snapshot() }
        assertFalse(f.platform.cancelledAll)
        f.store.failRead=false;f.store.raw="not-json"
        assertFailsWith<IllegalStateException> { f.engine().snapshot() }
        assertTrue(f.platform.cancelledAll)
    }
    @Test fun startingHomeQueryDoesNotInvalidateBackgroundButNewerHomeSuccessDoes()=runBlocking {
        for(homeSucceeds in listOf(false,true)) {
            val f=Fixture();f.enable()
            val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
            val source=object: ReminderDataSource {
                override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> {
                    started.complete(Unit);finish.await();return ReminderSourceResult.Success(listOf(f.item("A")))
                }
            }
            val engine=ReminderEngine(f.store,f.platform,ReminderIdentityProvider { ReminderIdentity(f.owner,"2026,2",true) },source,Clock { f.now })
            val refresh=launch { engine.refresh() };started.await()
            val home=engine.beginHomeDeadlineRefresh()
            if(homeSucceeds)engine.acceptHomeDeadlines(home,com.icecream.kwklasplus.core.academic.DeadlinesResult.Success(emptyList(),emptyList(),f.now,f.now))
            finish.complete(Unit);refresh.join()
            assertEquals(if(homeSucceeds)0 else 1,f.platform.posted.size)
        }
    }

    @Test fun sameDayAdditionalAlertIncludesOnlyPreviouslyUnannouncedItems()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"),f.item("B"))
        f.engine.refresh();f.engine.refresh()
        assertEquals(1,f.platform.posted.size)
        f.source.items+=f.item("C");f.engine.refresh();f.engine.refresh()
        assertEquals(listOf(false,true),f.platform.posted.map { it.second })
        assertEquals(setOf("user/C"),f.engine.snapshot().claims.last().keys)
        f.engine().refresh();assertEquals(2,f.platform.posted.size)
    }
    @Test fun homeSnapshotNeverPostsOrClaimsAndBackgroundRefreshStillAnnouncesItems()=runBlocking {
        val f=Fixture();f.enable()
        val a=f.item("A").copy(subjectId="s1",subjectName="자료구조",kind="task")
        val b=f.item("B").copy(subjectId="s2",subjectName="운영체제",kind="onlineLecture")
        suspend fun accept(items: List<ReminderDeadline>) {
            val ticket=f.engine.beginHomeDeadlineRefresh()
            f.engine.acceptHomeDeadlines(ticket,com.icecream.kwklasplus.core.academic.DeadlinesResult.Success(emptyList(),items,f.now,f.now))
        }
        accept(listOf(a,b))
        assertTrue(f.platform.messages.isEmpty());assertTrue(f.engine.snapshot().claims.isEmpty())
        assertEquals("FOREGROUND_SUPPRESSED",f.engine.snapshot().deadlineStatus)
        f.source.items=listOf(a,b);f.engine.refresh()
        assertEquals("하루 안에 마감되는 할 일이 2건 있어요",f.platform.messages.single().title)
        assertTrue(f.platform.messages.single().body.contains("자료구조 과제 1건"))
        val c=f.item("C").copy(subjectId="s3",subjectName="컴퓨터구조",kind="teamTask")
        accept(listOf(a,b,c));assertEquals(1,f.platform.messages.size)
        f.source.items=listOf(a,b,c);f.engine.refresh()
        assertEquals("곧 마감되는 할 일이 1건 더 생겼어요",f.platform.messages.last().title)
        assertEquals("컴퓨터구조 팀프로젝트 1건이 있어요. 약 1시간 뒤 마감돼요.",f.platform.messages.last().body)
        f.engine.refresh();assertEquals(2,f.platform.messages.size)
    }
    @Test fun backgroundJobWhileAppIsVisibleDoesNotConsumeDailyClaim()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.background=false
        f.engine.refresh();f.engine().refresh()
        assertTrue(f.platform.messages.isEmpty());assertTrue(f.engine.snapshot().claims.isEmpty())
        f.background=true;f.engine().refresh()
        assertEquals(1,f.platform.messages.size)
    }
    @Test fun foregroundTransitionDuringPermissionCheckDoesNotClaim()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"))
        f.platform.onPermission={ f.background=false }
        f.engine.refresh()
        assertTrue(f.platform.messages.isEmpty());assertTrue(f.engine.snapshot().claims.isEmpty())
        f.platform.onPermission={};f.background=true;f.engine.refresh()
        assertEquals(1,f.platform.messages.size)
    }
    @Test fun foregroundTransitionDuringClaimPersistenceReleasesUnpostedClaim()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"))
        f.store.onWrite={ if(f.store.raw?.contains("POST_ATTEMPTED")==true)f.background=false }
        f.engine.refresh()
        assertTrue(f.platform.messages.isEmpty());assertTrue(f.engine.snapshot().claims.isEmpty())
        assertEquals("FOREGROUND_SUPPRESSED",f.engine.snapshot().deadlineStatus)
        f.store.onWrite={};f.background=true;f.engine().refresh()
        assertEquals(1,f.platform.messages.size)
    }
    @Test fun staleOrSupersededHomeSnapshotDoesNotNotify()=runBlocking {
        val f=Fixture();f.enable()
        val first=f.engine.beginHomeDeadlineRefresh();val second=f.engine.beginHomeDeadlineRefresh()
        val snapshot=com.icecream.kwklasplus.core.academic.DeadlinesResult.Success(emptyList(),listOf(f.item("A")),f.now,f.now)
        f.engine.acceptHomeDeadlines(first,snapshot);assertTrue(f.platform.posted.isEmpty())
        f.now+=60_001;f.engine.acceptHomeDeadlines(second,snapshot);assertTrue(f.platform.posted.isEmpty())
    }

    @Test fun nextKstDayAllowsReminderAgainAndWindowIsStrict()=runBlocking {
        val f=Fixture();f.enable()
        f.source.items=listOf(ReminderDeadline("expired",null,f.now),ReminderDeadline("far",null,f.now+ReminderTime.DAY+1),ReminderDeadline("later",f.now+1,f.now+10),f.item("A"))
        f.engine.refresh();assertEquals(setOf("user/A"),f.engine.snapshot().claims.single().keys)
        f.now+=ReminderTime.DAY;f.source.items=listOf(f.item("A"));f.engine.refresh()
        assertEquals(2,f.platform.posted.size);assertFalse(f.platform.posted.last().second)
    }
    @Test fun failedRefreshOrDeniedPermissionDoesNotClaimItems()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.source.failure=true
        f.engine.refresh();assertTrue(f.engine.snapshot().claims.isEmpty())
        f.source.failure=false;f.platform.allowed=false;f.engine.refresh();assertTrue(f.engine.snapshot().claims.isEmpty())
        f.platform.allowed=true;f.engine.refresh();assertEquals(1,f.platform.posted.size)
    }
    @Test fun persistenceFailurePreventsPosting()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.store.fail=true
        assertFailsWith<IllegalStateException> { f.engine.refresh() };assertTrue(f.platform.posted.isEmpty())
    }
    @Test fun lateSuccessfulRefreshCannotPostAfterDisablingOrSwitchingAccount()=runBlocking {
        val store=Store();val platform=Platform();var owner="first"
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val now=ReminderTime.parse("2026-10-02 10:00:00")!!
        val source=object: ReminderDataSource {
            override suspend fun deadlines(term: String): ReminderSourceResult<List<ReminderDeadline>> {
                started.complete(Unit);finish.await();return ReminderSourceResult.Success(listOf(ReminderDeadline("A",null,now+60_000)))
            }
        }
        val engine=ReminderEngine(store,platform,ReminderIdentityProvider { ReminderIdentity(owner,"2026,2",true) },source,Clock { now })
        engine.setEnabled(true)
        val refresh=launch { engine.refresh() };started.await()
        engine.setEnabled(false);owner="second";engine.snapshot()
        finish.complete(Unit);refresh.join();assertTrue(platform.posted.isEmpty())
        assertTrue(engine.snapshot().claims.isEmpty())
    }

    @Test fun timeParsingRejectsInvalidDatesAndHonorsExplicitOffset() {
        assertNull(ReminderTime.parse("2026-02-29"));assertNull(ReminderTime.parse("2026-10-02 24:00"))
        assertEquals(ReminderTime.parse("2026-10-02T10:00:00+09:00"),ReminderTime.parse("20261002100000"))
        assertEquals(ReminderTime.parse("2026-10-02 10:00:00"),ReminderTime.parse("2026-10-02T01:00:00Z"))
        assertEquals("2026-10-02",ReminderTime.date(ReminderTime.parse("2026-10-01T15:00:00Z")!!))
    }
    @Test fun nativeToggleIsIdempotentAndFailedSaveKeepsPreviousState()=runBlocking {
        val f=Fixture();f.enable();val before=f.engine.snapshot()
        assertEquals(before.revision,f.engine.setEnabled(true).revision)
        f.store.fail=true
        assertFailsWith<IllegalStateException> { f.engine.setEnabled(false) }
        f.store.fail=false
        assertTrue(f.engine.settings().enabled)
    }
    @Test fun prototypeCalendarFieldsAreIgnoredAndRemovedOnSave()=runBlocking {
        val f=Fixture()
        f.store.raw="""{"schemaVersion":1,"ownerHash":"user","deadlineEnabled":true,"events":[{"unknown":true}],"rules":[],"plans":[],"operations":[]}"""
        assertTrue(f.engine.snapshot().deadlineEnabled)
        f.engine.setEnabled(false)
        assertFalse(f.store.raw!!.contains("events"))
        assertFalse(f.store.raw!!.contains("rules"))
    }
    @Test fun incompleteAuthenticationBlocksRefreshWithoutResettingPreferences()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.ready=false
        f.engine.refresh();assertTrue(f.platform.posted.isEmpty());assertTrue(f.engine.settings().enabled)
        assertFailsWith<IllegalArgumentException> { f.engine.setEnabled(false) }
        f.ready=true;f.engine.refresh();assertEquals(1,f.platform.posted.size)
    }

    @Test fun completedItemCancelsOldSummaryWithoutRepeatingRemainingItem()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"),f.item("B"));f.engine.refresh()
        val batch=f.engine.snapshot().claims.single().batchId
        f.source.items=listOf(f.item("B"));f.engine.refresh()
        assertTrue(batch in f.platform.cancelled);assertEquals(1,f.platform.posted.size)
        f.source.items+=f.item("A");f.engine.refresh();assertEquals(1,f.platform.posted.size)
        f.engine.setEnabled(false);assertTrue(f.platform.cancelledAll)
    }
    @Test fun accountChangeDisablesFeatureAndCancelsOldNotifications()=runBlocking {
        val f=Fixture();f.enable();f.source.items=listOf(f.item("A"));f.engine.refresh()
        f.owner="other";assertFalse(f.engine.settings().enabled);assertTrue(f.platform.cancelledAll)
        f.engine.refresh();assertEquals(1,f.platform.posted.size)
    }

}
