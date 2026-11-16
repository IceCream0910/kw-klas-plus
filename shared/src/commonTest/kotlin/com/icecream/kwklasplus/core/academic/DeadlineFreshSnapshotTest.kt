package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.notification.*
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DeadlineFreshSnapshotTest {
    @Test fun homeAndBackgroundAcceptTheSameDateVariantsAndKeepVerifiedItems() = runBlocking {
        val now = ReminderTime.parse("2026-11-13 08:00:00")!!
        val lesson = """{"evltnSe":"lesson","prog":3.33,"lesson":"01","oid":"a","startDate":" 2026-11-13 8:00 ","endDate":"2026-11-13 9:05 "}"""
        val task = """{"submityn":"N","taskNo":"t","startdate":"","expiredate":"2026-11-13 10:00"}"""
        var lectureRows = "[$lesson]"
        val transport = KlasAuthenticatedTransport { endpoint, _, _, _ ->
            val body = when (endpoint) {
                AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS ->
                    """[{"value":"2026,2","subjList":[{"value":"s","name":"강의"}]}]"""
                AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES -> lectureRows
                AuthenticatedKlasEndpoint.TASK_DEADLINES -> "[$task]"
                else -> "[]"
            }
            KlasAuthenticatedResult.Success(Json.parseToJsonElement(body))
        }
        val session = SecretValue.of("fixture")
        val userAgent = KlasUserAgent.fromPlatform("fixture")
        val term = "2026,2"
        val repo = DeadlineRepository(transport, Clock { now }, DeadlineDateParser(ReminderTime::parse), DeadlineDateParser(ReminderTime::parse))
        val background = AcademicReminderSource(transport, { session }, { userAgent })
        suspend fun home() = repo.fetch(session, userAgent, term, listOf(AcademicSubject("s", "강의")))

        val ready = assertIs<DeadlinesResult.Success>(home())
        val reminders = assertNotNull(ready.reminders)
        assertEquals(ReminderTime.parse("2026-11-13 09:05:59"), reminders.first().dueAt)
        assertEquals(ReminderTime.parse("2026-11-13 08:00:00"), reminders.first().startsAt)
        val backgroundReady = assertIs<ReminderSourceResult.Success<List<ReminderDeadline>>>(background.deadlines(term))
        assertEquals(reminders, backgroundReady.value)
        assertTrue(backgroundReady.isComplete)

        for (bad in listOf(
            "null",
            lesson.replace(""""prog":3.33,""", "").replace("oid\":\"a", "oid\":\"bad"),
            lesson.replace("3.33", "\"NaN\"").replace("oid\":\"a", "oid\":\"bad"),
            lesson.replace("2026-11-13 9:05 ", "invalid").replace("oid\":\"a", "oid\":\"bad"),
        )) {
            lectureRows = "[$bad,$lesson]"
            val partial = assertIs<DeadlinesResult.PartialSuccess>(home()).subjects.single()
            assertEquals(ready.subjects.single(), partial)
            val projection = DeadlineNotificationProjection.parse(term, "s", "강의", "onlineLecture", Json.parseToJsonElement(lectureRows).jsonArray)
            assertEquals(listOf(reminders.first()), projection.items)
            assertFalse(projection.isComplete)
            val backgroundPartial = assertIs<ReminderSourceResult.Success<List<ReminderDeadline>>>(background.deadlines(term))
            assertEquals(reminders, backgroundPartial.value)
            assertFalse(backgroundPartial.isComplete)
        }
    }

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
    @Test fun fractionalLectureProgressKeepsFeedAndNotificationProjection()=runBlocking {
        val now=ReminderTime.parse("2026-10-04 21:41:00")!!
        val repository=DeadlineRepository(KlasAuthenticatedTransport { endpoint,_,_,_ ->
            KlasAuthenticatedResult.Success(if(endpoint==AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES)Json.parseToJsonElement("""[{"evltnSe":"lesson","prog":3.33,"lesson":"003","oid":"lecture","startDate":"2026-09-08 00:00","endDate":"2026-12-12 23:59"},{"evltnSe":"lesson","prog":100,"lesson":"001","oid":"done","startDate":"2026-09-01 00:00","endDate":"2026-09-18 23:59"},{"evltnSe":"proj","startDate":"2026-09-18 00:00","endDate":"2026-10-10 23:59"}]""") else JsonArray(emptyList()))
        },Clock { now },DeadlineDateParser(ReminderTime::parse),DeadlineDateParser(ReminderTime::parse))
        val result=assertIs<DeadlinesResult.Success>(repository.fetch(SecretValue.of("synthetic"),KlasUserAgent.fromPlatform("fixture"),"2026,2",listOf(AcademicSubject("s","운영체제"))))
        val reminder=assertNotNull(result.reminders).single()
        assertEquals("2026-12-12 23:59",result.subjects.single().onlineLecture.single().endDate)
        assertEquals("onlineLecture",reminder.kind)
        assertEquals("2026,2/s/onlineLecture/003/lecture",reminder.key)
    }
}
