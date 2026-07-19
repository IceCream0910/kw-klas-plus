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

class KlasSessionHttpClientTest {
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
