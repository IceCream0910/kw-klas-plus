package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import com.icecream.kwklasplus.core.academic.CalendarRepository
import com.icecream.kwklasplus.core.academic.CalendarResult
import com.icecream.kwklasplus.core.academic.KlasCalendarEventNormalizer
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json

class KlasSessionHttpClientTest {
    @Test
    fun calendarMatchesWebProxyRequestAndParsesDateOnlyResponse() = runBlocking {
        val client = createKlasHttpClient(MockEngine { request ->
            assertEquals("https://klas.kw.ac.kr/std/ads/admst/MySchdulMonthTableList.do", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("SESSION=fixture-token;", request.headers[HttpHeaders.Cookie])
            assertEquals("application/json, text/plain, */*", request.headers[HttpHeaders.Accept])
            assertFalse(request.headers[HttpHeaders.UserAgent].orEmpty().contains("NuriwareApp"))
            val content = assertIs<TextContent>(request.body)
            assertEquals(ContentType.Application.Json, content.contentType.withoutParameters())
            assertEquals(Json.parseToJsonElement("""{"start":"2026-09-01","end":"2026-09-30"}"""), Json.parseToJsonElement(content.text))
            respond("""[{"id":"fixture","title":"[학사일정] 개강","started":"20260904","ended":"20260904","typeNm":"학사일정"}]""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = CalendarRepository(KlasSessionHttpClient(client), KlasCalendarEventNormalizer())
                .fetch(SecretValue.of("fixture-token"), KlasUserAgent.fromPlatform("Android UA"), "2026-09-01", "2026-09-30")
            val event = assertIs<CalendarResult.Success>(result).events.single()
            assertEquals("개강", event.title)
            assertEquals("2026-09-04T00:00", event.start)
            assertEquals("2026-09-04T23:59", event.end)
        } finally { client.close() }
    }

    @Test
    fun preservesLegacySessionAndUserAgentHeaders() = runBlocking {
        var requestedUrl: String? = null
        var cookie: String? = null
        var userAgent: String? = null
        val client = createKlasHttpClient(
            MockEngine { request ->
                requestedUrl = request.url.toString()
                cookie = request.headers[HttpHeaders.Cookie]
                userAgent = request.headers[HttpHeaders.UserAgent]
                respond(
                    content = "{\"fieldErrors\":[]}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val result = KlasSessionHttpClient(client).postJson(
            AuthenticatedKlasEndpoint.QR_CHECKIN,
            SecretValue.of("session-token"),
            KlasUserAgent.fromPlatform("Android WebView UA"),
            buildJsonObject { put("encrypt", "qr-value") },
        )

        assertEquals(KlasUrls.KLAS_QR_CHECKIN, requestedUrl)
        assertEquals("SESSION=session-token", cookie)
        assertEquals("Android WebView UA NuriwareApp", userAgent)
        assertEquals(true, result is KlasAuthenticatedResult.Success)
        client.close()
    }

    @Test
    fun mapsHtmlAndAuthenticationStatusToExpiredSession() = runBlocking {
        suspend fun result(status: HttpStatusCode, body: String): KlasAuthenticatedResult {
            val client = createKlasHttpClient(MockEngine { respond(body, status) })
            return KlasSessionHttpClient(client).postJson(
                AuthenticatedKlasEndpoint.ATTENDANCE_LIST,
                SecretValue.of("session-token"),
                KlasUserAgent.fromPlatform("UA"),
                buildJsonObject {},
            ).also { client.close() }
        }

        assertEquals(
            KlasAuthenticatedResult.SessionExpired,
            result(HttpStatusCode.OK, "<!DOCTYPE html><html></html>"),
        )
        assertEquals(
            KlasAuthenticatedResult.SessionExpired,
            result(HttpStatusCode.Unauthorized, "unauthorized"),
        )
    }

    @Test
    fun distinguishesEmptyMalformedAndHttpFailure() = runBlocking {
        suspend fun result(status: HttpStatusCode, body: String): KlasAuthenticatedResult {
            val client = createKlasHttpClient(MockEngine { respond(body, status) })
            return KlasSessionHttpClient(client).postJson(
                AuthenticatedKlasEndpoint.ATTENDANCE_SUBJECTS,
                SecretValue.of("session-token"),
                KlasUserAgent.fromPlatform("UA"),
                buildJsonObject {},
            ).also { client.close() }
        }

        assertEquals(KlasAuthenticatedResult.EmptyResponse, result(HttpStatusCode.OK, ""))
        assertEquals(KlasAuthenticatedResult.MalformedResponse, result(HttpStatusCode.OK, "not-json"))
        assertEquals(
            KlasAuthenticatedResult.HttpFailure(503),
            result(HttpStatusCode.ServiceUnavailable, "unavailable"),
        )
    }
}
