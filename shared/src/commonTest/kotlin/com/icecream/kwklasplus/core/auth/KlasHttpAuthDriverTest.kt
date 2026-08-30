package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.network.createKlasHttpClient
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KlasHttpAuthDriverTest {
    @Test
    fun performsMobileLoginSequenceAndReturnsAuthenticatedSession() = runBlocking {
        val requests = mutableListOf<CapturedRequest>()
        var encryptedPublicKey: String? = null
        var encryptedPayload: String? = null
        val responses = ArrayDeque(
            listOf(
                Response("<html></html>", sessionCookieHeaders()),
                Response("{\"publicKey\":\"public-key\"}"),
                Response("0"),
                Response(successConfirmation()),
            ),
        )
        val cookies = AcceptAllCookiesStorage()
        val client = createKlasHttpClient(
            engine = MockEngine { request ->
                requests += CapturedRequest(
                    url = request.url.toString(),
                    cookie = request.headers[HttpHeaders.Cookie],
                    origin = request.headers[HttpHeaders.Origin],
                    requestedWith = request.headers["X-Requested-With"],
                    body = request.body.toByteArray().decodeToString(),
                )
                val response = responses.removeFirst()
                respond(response.body, HttpStatusCode.OK, response.headers)
            },
            cookieStorage = cookies,
        )
        val driver = KlasHttpAuthDriver(
            client,
            cookies,
            LoginTokenEncryptor { publicKey, payload ->
                encryptedPublicKey = publicKey
                encryptedPayload = payload
                "login-token"
            },
        )

        val result = driver.authenticate(credential())

        assertEquals(WebAuthResult.SessionObserved(SecretValue.of("authenticated-session")), result)
        assertEquals(
            listOf(
                KlasUrls.KLAS_LOGIN,
                KlasUrls.KLAS_LOGIN_SECURITY,
                KlasUrls.KLAS_LOGIN_CAPTCHA,
                KlasUrls.KLAS_LOGIN_CONFIRM,
            ),
            requests.map(CapturedRequest::url),
        )
        assertTrue(requests.drop(1).all { it.cookie?.contains("SESSION=authenticated-session") == true })
        assertTrue(requests.drop(1).all { it.origin == KlasUrls.KLAS_BASE })
        assertTrue(requests.drop(1).all { it.requestedWith == "XMLHttpRequest" })
        assertEquals("{}", requests[1].body)
        assertEquals(
            setOf("loginToken", "captcha"),
            Json.parseToJsonElement(requests[2].body).jsonObject.keys,
        )
        assertEquals(
            setOf("loginToken", "captcha", "redirectUrl", "pushToken"),
            Json.parseToJsonElement(requests[3].body).jsonObject.keys,
        )
        assertEquals("public-key", encryptedPublicKey)
        val payload = Json.parseToJsonElement(requireNotNull(encryptedPayload)).jsonObject
        assertEquals("2026000000", payload.getValue("loginId").jsonPrimitive.content)
        assertEquals("stored-encrypted-password", payload.getValue("loginPwd").jsonPrimitive.content)
        assertEquals("MST", payload.getValue("loginTp").jsonPrimitive.content)
        client.close()
        cookies.close()
    }

    @Test
    fun captchaRequirementStopsBeforeConfirmation() = runBlocking {
        val result = resultFor(
            Response("<html></html>", sessionCookieHeaders()),
            Response("{\"publicKey\":\"public-key\"}"),
            Response("3"),
        )

        assertEquals(WebAuthResult.Failure(AuthFailure.CaptchaRequired), result)
    }

    @Test
    fun confirmationFailuresPreserveExistingUserActionSemantics() = runBlocking {
        assertEquals(
            WebAuthResult.Failure(AuthFailure.InvalidCredentials),
            confirmationResult("{\"errorCount\":1}"),
        )
        for (field in listOf("frstPwdAt", "certOpt", "twoFactorAt")) {
            assertEquals(
                WebAuthResult.Failure(AuthFailure.TemporaryPasswordChangeRequired),
                confirmationResult(successConfirmation("\"$field\":\"Y\"")),
            )
        }
        assertEquals(
            WebAuthResult.Failure(AuthFailure.InvalidCredentials),
            confirmationResult(successConfirmation("\"gradAt\":\"Y\"")),
        )
    }

    @Test
    fun malformedResponsesAndHttpFailuresAreDistinct() = runBlocking {
        assertEquals(
            WebAuthResult.Failure(AuthFailure.MalformedResponse),
            resultFor(
                Response("<html></html>", sessionCookieHeaders()),
                Response("{\"publicKey\":\"\"}"),
            ),
        )
        assertEquals(
            WebAuthResult.Failure(AuthFailure.MalformedResponse),
            confirmationResult("{\"errorCount\":0}"),
        )
        assertEquals(
            WebAuthResult.Failure(AuthFailure.MalformedResponse),
            confirmationResult("{}"),
        )
        assertEquals(
            WebAuthResult.Failure(AuthFailure.MalformedResponse),
            resultFor(
                Response("<html></html>"),
                Response("{\"publicKey\":\"public-key\"}"),
                Response("0"),
                Response(successConfirmation()),
            ),
        )
        assertEquals(
            WebAuthResult.Failure(AuthFailure.Network),
            resultFor(Response("unavailable", status = HttpStatusCode.ServiceUnavailable)),
        )
    }

    @Test
    fun timeoutIsNotCollapsedIntoGenericNetworkFailure() = runBlocking {
        val cookies = AcceptAllCookiesStorage()
        val client = createKlasHttpClient(
            engine = MockEngine { request -> throw HttpRequestTimeoutException(request) },
            cookieStorage = cookies,
        )

        val result = KlasHttpAuthDriver(
            client,
            cookies,
            LoginTokenEncryptor { _, _ -> "login-token" },
        ).authenticate(credential())

        assertEquals(WebAuthResult.Failure(AuthFailure.Timeout), result)
        client.close()
        cookies.close()
    }

    private suspend fun confirmationResult(confirmation: String): WebAuthResult = resultFor(
        Response("<html></html>", sessionCookieHeaders()),
        Response("{\"publicKey\":\"public-key\"}"),
        Response("0"),
        Response(confirmation),
    )

    private suspend fun resultFor(vararg queued: Response): WebAuthResult {
        val responses = ArrayDeque(queued.toList())
        val cookies = AcceptAllCookiesStorage()
        val client = createKlasHttpClient(
            engine = MockEngine {
                val response = responses.removeFirst()
                respond(response.body, response.status, response.headers)
            },
            cookieStorage = cookies,
        )
        return KlasHttpAuthDriver(
            client,
            cookies,
            LoginTokenEncryptor { _, _ -> "login-token" },
        ).authenticate(credential()).also {
            client.close()
            cookies.close()
        }
    }

    private fun credential() = StoredCredential(
        accountId = "2026000000",
        encryptedPassword = SecretValue.of("stored-encrypted-password"),
    )

    private fun successConfirmation(details: String = "") =
        "{\"errorCount\":0,\"response\":{${details}}}"

    private fun sessionCookieHeaders() = headersOf(
        HttpHeaders.SetCookie,
        "SESSION=authenticated-session; Domain=kw.ac.kr; Path=/; Secure; HttpOnly; SameSite=Lax",
    )

    private data class Response(
        val body: String,
        val headers: io.ktor.http.Headers = headersOf(),
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    private data class CapturedRequest(
        val url: String,
        val cookie: String?,
        val origin: String?,
        val requestedWith: String?,
        val body: String,
    )
}
