package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class CalendarRepositoryTest {
    private val normalizer = KlasCalendarEventNormalizer()

    @Test fun acceptsDateFormatsAcceptedByWebCalendarMomentParser() {
        for ((raw, expected) in listOf(
            "20260904" to "2026-09-04T00:00",
            "2026090412" to "2026-09-04T12:00",
            "202609041230" to "2026-09-04T12:30",
            "20260904123000" to "2026-09-04T12:30",
            "2026-09-04 12:30:00" to "2026-09-04T12:30",
            "2026-09-04T12:30:00" to "2026-09-04T12:30",
        )) {
            assertEquals(expected, normalizer.normalize(event(raw, "202609052359"))?.start)
        }
        assertEquals("2026-09-04T00:00", normalizer.normalize(event("", "20260904", "과제"))?.start)
    }

    @Test fun invalidDateInOneRowDoesNotDiscardOtherEvents() = runBlocking {
        val body = Json.parseToJsonElement("""[
            {"id":"valid","title":"[학사일정] 개강","started":"20260904","ended":"20260904","typeNm":"학사일정"},
            {"id":"invalid","title":"잘못된 날짜","started":"invalid","ended":"invalid"}
        ]""")
        val repo = CalendarRepository(KlasAuthenticatedTransport { _, _, _, _ -> KlasAuthenticatedResult.Success(body) }, normalizer)
        val result = assertIs<CalendarResult.Success>(repo.fetch(SecretValue.of("test"), KlasUserAgent.fromPlatform("test"), "2026-09-01", "2026-09-30"))
        assertEquals("valid", result.events.single().id)
    }

    @Test fun characterizesWebMonthEndpointAndFieldMapping() = runBlocking {
        val repo = CalendarRepository(KlasAuthenticatedTransport { endpoint, _, _, body ->
            assertEquals("https://klas.kw.ac.kr/std/ads/admst/MySchdulMonthTableList.do", endpoint.url)
            assertEquals("2026-09-01", body.jsonObject["start"]?.jsonPrimitive?.content)
            assertEquals("2026-09-30", body.jsonObject["end"]?.jsonPrimitive?.content)
            KlasAuthenticatedResult.Success(Json.parseToJsonElement("""[{"id":"1","title":"[개인일정] 회의","started":"202609041200","ended":"202609041300","typeNm":"개인일정","place":"연구관","schdulColor":"#ff0000"}]"""))
        }, normalizer)
        val result = assertIs<CalendarResult.Success>(repo.fetch(SecretValue.of("test"), KlasUserAgent.fromPlatform("test"), "2026-09-01", "2026-09-30"))
        assertEquals("회의", result.events.single().title)
        assertEquals("2026-09-04T12:00", result.events.single().start)
        assertEquals("연구관", result.events.single().place)
    }

    @Test fun normalizesAssignmentDeadlineAndInclusiveMidnight() {
        val task = normalizer.normalize(event("202609011100", "202609042359", "과제"))!!
        assertEquals("2026-09-04T00:00", task.start)
        assertEquals("2026-09-04T23:59", task.end)
        val edge = normalizer.normalize(event("202609042359", "202609050000"))!!
        assertEquals("2026-09-04T00:00", edge.start)
        assertEquals("2026-09-05T23:59", edge.end)
    }

    @Test fun correctsHistoricalAcademicYearAndRejectsInvalidDates() {
        assertEquals("2026-02-01T00:00", normalizer.normalize(event("202502010000", "202602052359", "학사일정"))?.start)
        assertNull(normalizer.normalize(event("202602290000", "202603010000")))
        assertNotNull(normalizer.normalize(event("202402290000", "202403010000")))
        assertNull(normalizer.normalize(event("202609052359", "202609040000")))
        assertNull(normalizer.normalize(event("garbage", "202609040000")))
    }

    @Test fun malformedAndAuthenticationResponsesAreNotEmptyCalendars() = runBlocking {
        for ((response, expected) in listOf(
            KlasAuthenticatedResult.Success(JsonArray(emptyList())) to CalendarResult.Success(emptyList()),
            KlasAuthenticatedResult.Success(JsonObject(emptyMap())) to CalendarResult.MalformedResponse,
            KlasAuthenticatedResult.SessionExpired to CalendarResult.SessionExpired,
            KlasAuthenticatedResult.Timeout to CalendarResult.Retry,
        )) {
            val repo = CalendarRepository(KlasAuthenticatedTransport { _, _, _, _ -> response }, normalizer)
            assertEquals(expected, repo.fetch(SecretValue.of("test"), KlasUserAgent.fromPlatform("test"), "2026-09-01", "2026-09-30"))
        }
    }

    private fun event(start: String, end: String, kind: String = "") = CalendarEvent("id", "event", start, end, kind, "", "")
}
