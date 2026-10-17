package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.bridge.*
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class KlasApiCoreTest {
    private val endpoint = "/std/cps/inqire/AtnlcScreHakjukInfo.do"
    private val token = SecretValue.of("native-session")
    private fun api(engine: MockEngine, session: suspend () -> SecretValue? = { token }) =
        KlasApiCore(HttpClient(engine), session, { KlasUserAgent.fromPlatform("fixture") })

    @Test fun unregisteredHostsPathsAndNestedPayloadsNeverReachNetwork() = runBlocking {
        var requests = 0
        val core = api(MockEngine { requests++; respond("{}") })
        for (path in listOf("https://evil.test$endpoint", "https://klas.kw.ac.kr.evil.test$endpoint", "https://klas.kw.ac.kr:444$endpoint", "/unknown.do", "$endpoint?next=evil", "//$endpoint", "/std/../$endpoint")) {
            assertEquals("INVALID_ENDPOINT", assertIs<KlasApiResult.Failure>(core.request(path, "{}")).code)
        }
        assertEquals("INVALID_PAYLOAD", assertIs<KlasApiResult.Failure>(core.request(endpoint, "[1]")).code)
        assertEquals("INVALID_PAYLOAD", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{\"x\":".repeat(100) + "0" + "}".repeat(100))).code)
        assertEquals(0, requests)
    }

    @Test fun nativeSessionIsUsedAndResponseShapeIsNotProjected() = runBlocking {
        val json = "{\"number\":9007199254740993,\"nullable\":null,\"html\":\"<html>게시글</html>\",\"rows\":[]}"
        val core = api(MockEngine { request ->
            assertEquals("klas.kw.ac.kr", request.url.host)
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("SESSION=native-session;", request.headers[HttpHeaders.Cookie])
            assertEquals("fixture NuriwareApp", request.headers[HttpHeaders.UserAgent])
            respond(json)
        })
        assertEquals(json, assertIs<KlasApiResult.Success>(core.request(endpoint, "{}")).body)
    }

    @Test fun redirectsAreNotFollowedAndForbiddenDoesNotExpireSession() = runBlocking {
        var requests = 0
        val core = api(MockEngine { requests++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.test/")) })
        assertEquals("AUTH_ACTION_REQUIRED", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{}")).code)
        assertEquals(1, requests)
        val forbidden = api(MockEngine { respond("", HttpStatusCode.Forbidden) })
        assertEquals("ACCESS_DENIED", assertIs<KlasApiResult.Failure>(forbidden.request(endpoint, "{}")).code)
        val expired = api(MockEngine { respond("", HttpStatusCode.Unauthorized) })
        assertEquals("SESSION_EXPIRED", assertIs<KlasApiResult.Failure>(expired.request(endpoint, "{}")).code)
    }

    @Test fun missingAndChangedSessionsCannotReturnProtectedData() = runBlocking {
        var current: SecretValue? = null
        var requests = 0
        val core = api(MockEngine { requests++; current = null; respond("{}") }) { current }
        assertEquals("SESSION_REQUIRED", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{}")).code)
        assertEquals(0, requests)
        current = token
        assertEquals("STALE_SESSION", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{}")).code)
    }

    @Test fun oversizedBodyStopsAtTheResponseLimit() = runBlocking {
        val core = api(MockEngine { respond(" ".repeat(KlasApiContract.MAXIMUM_RESPONSE_BYTES + 10)) })
        assertEquals("RESPONSE_TOO_LARGE", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{}")).code)
    }

    @Test fun mutationTimeoutIsNotRetriedAndDoesNotClaimFailure() = runBlocking {
        var count = 0
        val core = api(MockEngine { request -> count++; throw HttpRequestTimeoutException(request) })
        val result = core.request("/std/ads/admst/MySchdulSave.do", "{}")
        assertEquals("OUTCOME_UNKNOWN", assertIs<KlasApiResult.Failure>(result).code)
        assertEquals(1, count)
    }

    @Test fun chunkedResponsesAreBoundToSessionAndResetWithTheDocument() = runBlocking {
        var current: SecretValue? = token
        val handler = KlasApiBridgeHandler(api(MockEngine { respond("{\"value\":\"${"한".repeat(10000)}\"}") }) { current }, BridgeCommandHandler { error("unexpected fallback") })
        fun command(method: String, vararg args: String) = ValidatedBridgeCommand("bridge-id", BridgeSurface.HOME,
            requireNotNull(BridgeMethodId.from(BridgeSurface.HOME, method)), requireNotNull(LegacyBridgeCatalog.find(BridgeSurface.HOME, method)), args.map(BridgeValue::Text))
        val descriptor = assertIs<BridgeValue.ObjectValue>(assertIs<BridgeHandlerResult.Success>(handler.handle(command("requestKlasApi", "request-1", endpoint, "{}"))).value)
        assertTrue(assertIs<BridgeValue.NumberValue>(descriptor.value["chunkCount"]).value > 1)
        val chunk = assertIs<BridgeValue.Text>(assertIs<BridgeHandlerResult.Success>(handler.handle(command("readKlasApiChunk", "request-1", "0"))).value).value
        assertTrue(chunk.length <= 6144)
        current = null
        val stale = assertIs<BridgeValue.ObjectValue>(assertIs<BridgeHandlerResult.Success>(handler.handle(command("readKlasApiChunk", "request-1", "1"))).value)
        assertEquals(BridgeValue.Text("STALE_SESSION"), stale.value["error"])
        current = token
        handler.handle(command("requestKlasApi", "request-2", endpoint, "{}"))
        handler.reset()
        val removed = assertIs<BridgeValue.ObjectValue>(assertIs<BridgeHandlerResult.Success>(handler.handle(command("readKlasApiChunk", "request-2", "0"))).value)
        assertEquals(BridgeValue.Text("RESPONSE_EXPIRED"), removed.value["error"])
    }

    @Test fun pageReaderUsesGetOnlyForRegisteredKlasPages() = runBlocking {
        var count = 0
        val html = "<main>학적 정보</main>"
        val core = api(MockEngine { request ->
            count++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("klas.kw.ac.kr", request.url.host)
            respond(html)
        })
        val result = assertIs<KlasApiResult.Success>(core.request("page:/std/cps/inqire/AtnlcScreStdPage.do", "null"))
        assertEquals(html, kotlinx.serialization.json.Json.parseToJsonElement(result.body).let { (it as kotlinx.serialization.json.JsonPrimitive).content })
        assertEquals("INVALID_ENDPOINT", assertIs<KlasApiResult.Failure>(core.request("page://evil.test/", "null")).code)
        assertEquals(1, count)
    }

    @Test fun changedSemesterDiscardsDataWithoutClearingTheSession() = runBlocking {
        var term = "2026,1"
        val core = KlasApiCore(HttpClient(MockEngine { term = "2026,2"; respond("{}") }), { token }, { KlasUserAgent.fromPlatform("fixture") }, { term })
        assertEquals("STALE_CONTEXT", assertIs<KlasApiResult.Failure>(core.request(endpoint, "{}")).code)
        assertEquals(token, core.currentSession())
    }

    @Test fun queuedRequestsCannotExecuteOnAnotherAccount() = runBlocking {
        var current = token
        var count = 0
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val core = api(MockEngine { count++; if (count == 4) started.complete(Unit); release.await(); respond("{}") }) { current }
        val requests = List(4) { async { core.request(endpoint, "{}") } }
        started.await()
        val queued = async { core.request(endpoint, "{}") }
        yield()
        current = SecretValue.of("other-account")
        release.complete(Unit)
        for (request in requests + queued) assertEquals("STALE_SESSION", assertIs<KlasApiResult.Failure>(request.await()).code)
        assertEquals(4, count)
    }

    @Test fun apiMethodsRejectSchoolOriginAndChildFramesOnEverySurface() {
        val validator = BridgeValidator()
        for (surface in BridgeSurface.entries) {
            val request = BridgeRequest(1, "id", "requestKlasApi", listOf("request", endpoint, "{}").map(BridgeValue::Text))
            assertIs<BridgeValidationResult.Accepted>(validator.validate(request, BridgeContext(surface, "https://klasplus.yuntae.in", true, 100)))
            assertIs<BridgeValidationResult.Rejected>(validator.validate(request, BridgeContext(surface, "https://klas.kw.ac.kr", true, 100)))
            assertIs<BridgeValidationResult.Rejected>(validator.validate(request, BridgeContext(surface, "https://klasplus.yuntae.in", false, 100)))
        }
    }
}
