package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.academic.*
import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.get
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DemoSessionTest {
    @Test fun lectureHomeContainsTheWebAttendanceContractAndLockCanBeConfigured() = runBlocking {
        begin()
        val home = Json.parseToJsonElement(DemoFixtures.respond("/LctrumHomeStdInfo.do").body).jsonObject
        assertEquals(3, home.getValue("atendSubList").jsonArray.size)
        assertTrue(home.getValue("noticeList").jsonArray.isNotEmpty())
        DemoFixtures.respond("/DemoLockSave.do", "{}")
        assertTrue(DemoSession.appLockEnabled)
        DemoSession.end()
        assertFalse(DemoSession.appLockEnabled)
    }
    @Test fun qrAttendanceRunsTheRealPreparationAndSubmissionRepositoryWithoutHttp() = runBlocking {
        begin()
        val client = createKlasHttpClient(MockEngine { error("School HTTP must not run") })
        try {
            val repository = com.icecream.kwklasplus.core.attendance.AttendanceRepository(KlasSessionHttpClient(client))
            val agent = KlasUserAgent.fromPlatform("test")
            val prepared = assertIs<com.icecream.kwklasplus.core.attendance.QrPreparationResult.Success>(repository.prepareCheckIn(
                DemoSession.token, agent, com.icecream.kwklasplus.core.attendance.QrPreparationRequest("2026", "2", "DEMO001", "컴퓨터프로그래밍")))
            assertIs<com.icecream.kwklasplus.core.attendance.QrCheckInResult.Rejected>(repository.checkIn(DemoSession.token, agent, prepared.payload, SecretValue.of("invalid")))
            assertEquals(com.icecream.kwklasplus.core.attendance.QrCheckInResult.Success, repository.checkIn(DemoSession.token, agent, prepared.payload, SecretValue.of("KLASPLUS-DEMO-ATTENDANCE")))
        } finally { client.close() }
    }

    @Test fun lecturePlanAndWidgetFixturesSatisfyTheDisplayContracts() = runBlocking {
        begin()
        val plan = Json.parseToJsonElement(DemoFixtures.respond("/LectrePlanData.do").body).jsonArray.single().jsonObject
        assertEquals("컴퓨터프로그래밍", plan.getValue("gwamokKname").jsonPrimitive.content)
        for (endpoint in listOf("LectreTimeInfo.do", "LectreAstnt.do", "LectreTeam.do", "LectrePlanInputTabSixInfo.do")) {
            assertIs<JsonArray>(Json.parseToJsonElement(DemoFixtures.respond("/"+endpoint).body))
        }
        val widget = DemoWidgetFixtures.snapshot("demo", 1_793_145_600_000)
        assertEquals("demo", widget.owner)
        assertEquals(3, widget.timetable?.size)
        assertTrue(widget.calendar.orEmpty().isNotEmpty())
        assertEquals(0, widget.timetable!!.first().day)
    }

    @Test fun demoReminderStateNeverCancelsOrdinaryNotifications() = runBlocking {
        begin()
        var ordinaryCancellations = 0
        val platform = object : com.icecream.kwklasplus.core.notification.ReminderPlatform {
            override suspend fun permission(kind: String) = "authorized"
            override fun hash(value: String) = value
            override suspend fun cancelAll() { ordinaryCancellations++ }
            override suspend fun cancel(id: String) { assertTrue(id.startsWith("demo:")) }
            override suspend fun postDetailed(id: String, kind: String, generation: Long, additional: Boolean, message: com.icecream.kwklasplus.core.notification.ReminderMessage) = false
        }
        val engine = DemoReminderFactory.create(platform, Clock { 1000 })
        assertTrue(engine.snapshot().ownerHash.startsWith("demo-"))
        engine.clear()
        assertEquals(0, ordinaryCancellations)
    }

    @Test fun demoPlaybackProgressResumesAndIsClearedOnExit() {
        begin()
        DemoPlaybackState.record(45.0, 120.0)
        assertEquals(37.5, DemoPlaybackState.progress)
        DemoPlaybackState.record(Double.NaN, 120.0)
        assertEquals(45.0, DemoPlaybackState.position)
        DemoSession.end()
        assertEquals(0.0, DemoPlaybackState.position)
        DemoPlaybackState.record(10.0, 120.0)
        assertEquals(0.0, DemoPlaybackState.position)
    }

    @AfterTest fun cleanup() { DemoSession.end() }
    private fun begin() = assertTrue(DemoSession.begin("0000000000", "KlasplusDemo!123"))

    @Test fun onlyExactCredentialsActivateDemo() {
        DemoSession.end()
        assertFalse(DemoSession.begin("2026000000", "KlasplusDemo!123"))
        assertFalse(DemoSession.begin("0000000000", "wrong"))
        assertFalse(DemoSession.active)
        begin()
        assertTrue(DemoSession.active)
    }

    @Test fun demoSessionNeverTouchesStoredIdentityOrCookies() = runBlocking {
        val coordinator = SessionCoordinator(object : SessionStore {
            override suspend fun load(): Session? = error("real session read")
            override suspend fun save(session: Session) = error("real session write")
            override suspend fun clear() = error("real session clear")
        }, object : WebCookieStore {
            override suspend fun setSessionCookie(token: SecretValue) = error("real cookie write")
            override suspend fun clearSessionCookie() = error("real cookie clear")
        }, Clock { 1234 })
        begin()
        assertEquals(DemoSession.token, assertIs<SessionResult.Active>(coordinator.restore()).session.token)
        assertEquals(SessionResult.Expired, coordinator.observe(SecretValue.of("late-real-token")))
        val lease = SessionLeaseManager(coordinator, object : SessionLeaseGateway {
            override suspend fun fetchInfo(session: SecretValue, userAgent: KlasUserAgent): SessionInfoResult = error("real lease request")
            override suspend fun extend(session: SecretValue, userAgent: KlasUserAgent): SessionExtensionResult = error("real extension request")
        }, KlasUserAgent.fromPlatform("test"))
        assertIs<SessionLeaseResult.Active>(lease.maintain())
        assertEquals(SessionResult.Expired, coordinator.expire())
        assertEquals(SessionResult.Missing, coordinator.restore())
        assertEquals(SessionLeaseResult.Missing, lease.maintain())
        Unit
    }

    @Test fun demoEngineServesRepositoriesAndRejectsUnknownEndpointsWithoutNetwork() = runBlocking {
        var networkCalls = 0
        val client = createKlasHttpClient(MockEngine { networkCalls++; error("real network") })
        begin()
        val transport = KlasSessionHttpClient(client)
        val agent = KlasUserAgent.fromPlatform("test")
        val terms = assertIs<AcademicTermsResult.Success>(AcademicRepository(transport).fetchTerms(DemoSession.token, agent))
        assertEquals(3, terms.terms.single().subjects.size)
        val timetable = assertIs<TimetableResult.Success>(TimetableRepository(transport).fetch(DemoSession.token, agent, "2026", "2"))
        assertEquals(3, timetable.entriesBySubject.size)
        assertEquals(501, client.get("https://klas.kw.ac.kr/unimplemented").status.value)
        assertEquals(0, networkCalls)
        client.close()
    }

    @Test fun normalEngineStillDelegatesAndExpiredDemoTokensNeverReachNetwork() = runBlocking {
        DemoSession.end()
        var networkCalls = 0
        val client = createKlasHttpClient(MockEngine { networkCalls++; respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) })
        assertEquals(200, client.get("https://klas.kw.ac.kr/normal").status.value)
        assertEquals(1, networkCalls)
        assertFailsWith<IllegalStateException> {
            client.get("https://klas.kw.ac.kr/stale") { headers.append(HttpHeaders.Cookie, "SESSION=klasplus-demo-1") }
        }
        assertEquals(1, networkCalls)
        client.close()
    }

    @Test fun mutationsSurviveRefreshAndAreDiscardedAtLogout() = runBlocking {
        begin()
        val event = """{"title":"스터디","sdate":"2026-11-07","edate":"2026-11-07","stimeHour":9,"stimeMin":0,"etimeHour":10,"etimeMin":0}"""
        assertEquals(200, DemoFixtures.respond("/MySchdulSave.do", event).status)
        fun rows(response: DemoResponse) = Json.parseToJsonElement(response.body).jsonArray
        var events = rows(DemoFixtures.respond("/MySchdulMonthTableList.do"))
        assertTrue(events.any { it.jsonObject["title"]?.jsonPrimitive?.content == "스터디" })
        val id = events.last().jsonObject.getValue("schdulId")
        DemoFixtures.respond("/MySchdulSave.do", Json.parseToJsonElement(event).jsonObject.let { JsonObject(it + mapOf("schdulId" to id, "title" to JsonPrimitive("수정"))) }.toString())
        events = rows(DemoFixtures.respond("/MySchdulMonthTableList.do"))
        assertEquals("수정", events.last().jsonObject["title"]?.jsonPrimitive?.content)
        DemoFixtures.respond("/MySchdulDelete.do", buildJsonObject { put("schdulId", id) }.toString())
        assertEquals(1, rows(DemoFixtures.respond("/MySchdulMonthTableList.do")).size)
        DemoFixtures.respond("/DemoTaskSave.do", """{"feature":"task","text":"예시 답안"}""")
        assertEquals("Y", rows(DemoFixtures.respond("/TaskStdList.do")).single().jsonObject["submityn"]?.jsonPrimitive?.content)
        DemoFixtures.respond("/DemoLectureProgress.do", """{"feature":"lecture","progress":100}""")
        assertEquals(100.0, rows(DemoFixtures.respond("/SelectOnlineCntntsStdList.do")).single().jsonObject.getValue("prog").jsonPrimitive.double)
        val token = DemoSession.token
        DemoSession.end()
        assertEquals(401, DemoFixtures.respond("/DemoState.do").status)
        begin()
        assertNotEquals(token, DemoSession.token)
        assertEquals("N", rows(DemoFixtures.respond("/TaskStdList.do")).single().jsonObject["submityn"]?.jsonPrimitive?.content)
        assertEquals(0.0, rows(DemoFixtures.respond("/SelectOnlineCntntsStdList.do")).single().jsonObject.getValue("prog").jsonPrimitive.double)
    }
}
