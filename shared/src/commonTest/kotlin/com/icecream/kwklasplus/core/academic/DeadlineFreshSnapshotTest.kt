package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.notification.ReminderTime
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DeadlineFreshSnapshotTest {
    @Test fun oneResponseCreatesLegacyFeedAndDetailedProjectionWithNoCachedFallback()=runBlocking {
        var calls=0;var round=0
        val now=ReminderTime.parse("2026-10-02 10:00:00")!!
        val repository=DeadlineRepository(KlasAuthenticatedTransport { endpoint,_,_,_ ->
            calls++
            if(round==2)KlasAuthenticatedResult.NetworkFailure
            else KlasAuthenticatedResult.Success(if(endpoint==AuthenticatedKlasEndpoint.TASK_DEADLINES)Json.parseToJsonElement("""[{"taskNo":"${if(round==0)"A" else "B"}","submityn":"N","startdate":"202610010900","expiredate":"202610021200"}]""") else JsonArray(emptyList()))
        },Clock { now },DeadlineDateParser(ReminderTime::parse),DeadlineDateParser(ReminderTime::parse))
        suspend fun fetch()=repository.fetch(SecretValue.of("synthetic"),KlasUserAgent.fromPlatform("fixture"),"2026,2",listOf(AcademicSubject("s","자료구조")))
        val first=assertIs<DeadlinesResult.Success>(fetch());assertEquals(3,calls)
        assertEquals(2,first.subjects.single().task.single().hourGap)
        assertEquals("자료구조",first.reminders!!.single().subjectName)
        assertEquals("task",first.reminders!!.single().kind)
        assertEquals(now,first.fetchedAt)
        val wire=DeadlinesWebCodec().encode(first.subjects)
        assertFalse(wire.contains("taskNo"));assertFalse(wire.contains("reminders"))
        round=1;val second=assertIs<DeadlinesResult.Success>(fetch());assertEquals(6,calls)
        assertNotEquals(first.reminders!!.single().key,second.reminders!!.single().key)
        round=2;assertEquals(DeadlinesResult.NetworkFailure,fetch())
    }
    @Test fun missingStableIdKeepsFreshFeedButBlocksNotificationProjection()=runBlocking {
        val now=ReminderTime.parse("2026-10-02 10:00:00")!!
        val repository=DeadlineRepository(KlasAuthenticatedTransport { endpoint,_,_,_ ->
            KlasAuthenticatedResult.Success(if(endpoint==AuthenticatedKlasEndpoint.TASK_DEADLINES)Json.parseToJsonElement("""[{"submityn":"N","startdate":"202610010900","expiredate":"202610021200"}]""") else JsonArray(emptyList()))
        },Clock { now },DeadlineDateParser(ReminderTime::parse),DeadlineDateParser(ReminderTime::parse))
        val result=assertIs<DeadlinesResult.Success>(repository.fetch(SecretValue.of("synthetic"),KlasUserAgent.fromPlatform("fixture"),"2026,2",listOf(AcademicSubject("s","자료구조"))))
        assertEquals(1,result.subjects.single().task.size);assertNull(result.reminders)
    }
}
