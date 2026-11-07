package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.academic.*
import com.icecream.kwklasplus.core.network.*
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DemoDeadlineDatesTest {
    @AfterTest fun cleanup() { DemoSession.end() }

    @Test fun deadlinesRollForwardOnEveryReadEvenInTheSameDemoSession() = runBlocking {
        DemoSession.begin("0000000000", "KlasplusDemo!123")
        for (now in listOf(1_735_689_600_000L, 1_893_456_000_000L, 4_102_444_800_000L)) {
            val clock = Clock { now }
            val lecture = row("SelectOnlineCntntsStdList.do", clock)
            assertEquals(date(now, -1) + " 00:00", lecture.getValue("startDate").jsonPrimitive.content)
            assertEquals(date(now, 1) + " 23:59", lecture.getValue("endDate").jsonPrimitive.content)
            for ((endpoint, days) in listOf("TaskStdList.do" to 2, "PrjctStdList.do" to 3)) {
                val task = row(endpoint, clock)
                assertEquals(date(now, -1) + " 00:00:00", task.getValue("startdate").jsonPrimitive.content)
                assertEquals(date(now, days) + " 23:59:59", task.getValue("expiredate").jsonPrimitive.content)
            }
            val home = Json.parseToJsonElement(DemoFixtures.respond("/LctrumHomeStdInfo.do", clock = clock).body).jsonObject
            assertEquals(lecture, home.getValue("cntntList").jsonArray.single())
            assertEquals(row("TaskStdList.do", clock), home.getValue("taskTop").jsonArray.single())
        }
    }

    @Test fun futureReviewDatesStillProduceVisibleDeadlinesThroughTheRepository() = runBlocking {
        DemoSession.begin("0000000000", "KlasplusDemo!123")
        for (now in listOf(1_893_456_000_000L, 4_102_444_800_000L)) {
            val clock = Clock { now }
            val transport = KlasAuthenticatedTransport { endpoint, _, _, payload ->
                KlasAuthenticatedResult.Success(Json.parseToJsonElement(
                    DemoFixtures.respond(endpoint.url, payload.toString(), clock).body,
                ))
            }
            val ends = (1..3).associate { days ->
                date(now, days) + " 23:59:59" to now + (days + 1) * 86_400_000L - 1_000L
            }
            val parser = DeadlineDateParser { ends[it] }
            val repository = DeadlineRepository(transport, clock, parser, parser)
            val result = assertIs<DeadlinesResult.Success>(repository.fetch(
                DemoSession.token, KlasUserAgent.fromPlatform("demo-test"), "${GMTDate(now).year},1",
                listOf(AcademicSubject("DEMO001", "컴퓨터프로그래밍")),
            ))
            val subject = result.subjects.single()
            assertTrue(subject.onlineLecture.single().hourGap > 0)
            assertTrue(subject.task.single().hourGap > 0)
            assertTrue(subject.teamTask.single().hourGap > 0)
        }
    }

    private suspend fun row(endpoint: String, clock: Clock) =
        Json.parseToJsonElement(DemoFixtures.respond("/$endpoint", clock = clock).body).jsonArray.single().jsonObject

    private fun date(now: Long, days: Int): String = GMTDate(now + days * 86_400_000L).let {
        "${it.year}-${(it.month.ordinal + 1).toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}"
    }
}
