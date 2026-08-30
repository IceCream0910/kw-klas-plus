package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.SessionExtensionResult
import com.icecream.kwklasplus.core.session.SessionInfoResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KlasSessionLeaseHttpGatewayTest {
    private val token = SecretValue.of("session-token")
    private val userAgent = KlasUserAgent.fromPlatform("Android Agent")

    @Test
    fun usesSessionOnlyHeadersAndGetEndpoints() = runBlocking {
        val requests = mutableListOf<CapturedRequest>()
        val bodies = ArrayDeque(
            listOf(
                "{\"logoutCountDownSec\":300,\"sessionNotiSec\":6900,\"remainingTime\":7200}",
                "{}",
            ),
        )
        val client = createKlasHttpClient(
            MockEngine { request ->
                requests += CapturedRequest(
                    url = request.url.toString(),
                    method = request.method.value,
                    cookie = request.headers[HttpHeaders.Cookie],
                    referer = request.headers[HttpHeaders.Referrer],
                    userAgent = request.headers[HttpHeaders.UserAgent],
                    requestedWith = request.headers["X-Requested-With"],
                )
                respond(bodies.removeFirst())
            },
        )
        val gateway = KlasSessionLeaseHttpGateway(client)

        val info = gateway.fetchInfo(token, userAgent)
        val extension = gateway.extend(token, userAgent)

        val success = assertIs<SessionInfoResult.Success>(info)
        assertEquals(7_200L, success.info.remainingSeconds)
        assertEquals(SessionExtensionResult.Success, extension)
        assertEquals(listOf(KlasUrls.KLAS_SESSION_INFO, KlasUrls.KLAS_SESSION_UPDATE), requests.map { it.url })
        assertTrue(requests.all { it.method == "GET" })
        assertTrue(requests.all { it.cookie == "SESSION=session-token" })
        assertTrue(requests.all { it.referer == KlasUrls.KLAS_FRAME })
        assertTrue(requests.all { it.userAgent == "Android Agent NuriwareApp" })
        assertTrue(requests.all { it.requestedWith == "XMLHttpRequest" })
        client.close()
    }

    @Test
    fun mapsExpiredHtmlAndAuthenticationStatus() = runBlocking {
        assertEquals(
            SessionInfoResult.SessionExpired,
            infoResult("{}"),
        )
        assertEquals(
            SessionInfoResult.SessionExpired,
            infoResult("<!DOCTYPE html><html></html>"),
        )
        assertEquals(
            SessionInfoResult.SessionExpired,
            infoResult("unauthorized", HttpStatusCode.Unauthorized),
        )
        assertEquals(
            SessionExtensionResult.SessionExpired,
            extensionResult("<html></html>"),
        )
    }

    @Test
    fun rejectsMalformedOrInvalidLeaseResponses() = runBlocking {
        assertEquals(
            SessionInfoResult.MalformedResponse,
            infoResult("{\"unexpected\":true}"),
        )
        assertEquals(
            SessionInfoResult.MalformedResponse,
            infoResult("{\"logoutCountDownSec\":300,\"sessionNotiSec\":6900,\"remainingTime\":-1}"),
        )
        assertEquals(SessionExtensionResult.MalformedResponse, extensionResult(""))
        assertEquals(SessionExtensionResult.MalformedResponse, extensionResult("[]"))
    }

    @Test
    fun keepsTimeoutDistinctFromNetworkFailure() = runBlocking {
        val timeoutClient = createKlasHttpClient(
            MockEngine { request -> throw HttpRequestTimeoutException(request) },
        )
        val networkClient = createKlasHttpClient(MockEngine { error("network") })

        assertEquals(
            SessionInfoResult.Timeout,
            KlasSessionLeaseHttpGateway(timeoutClient).fetchInfo(token, userAgent),
        )
        assertEquals(
            SessionExtensionResult.NetworkFailure,
            KlasSessionLeaseHttpGateway(networkClient).extend(token, userAgent),
        )
        timeoutClient.close()
        networkClient.close()
    }

    private suspend fun infoResult(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): SessionInfoResult {
        val client = createKlasHttpClient(MockEngine { respond(body, status) })
        return KlasSessionLeaseHttpGateway(client).fetchInfo(token, userAgent).also { client.close() }
    }

    private suspend fun extensionResult(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): SessionExtensionResult {
        val client = createKlasHttpClient(MockEngine { respond(body, status) })
        return KlasSessionLeaseHttpGateway(client).extend(token, userAgent).also { client.close() }
    }

    private data class CapturedRequest(
        val url: String,
        val method: String,
        val cookie: String?,
        val referer: String?,
        val userAgent: String?,
        val requestedWith: String?,
    )
}
