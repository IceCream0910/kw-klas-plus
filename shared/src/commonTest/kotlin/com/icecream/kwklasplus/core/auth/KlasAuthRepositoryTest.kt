package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.network.createKlasHttpClient
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class KlasAuthRepositoryTest {
    @Test
    fun encryptsPasswordUsingLegacyEndpointAndResponseField() = runBlocking {
        var requestedUrl: String? = null
        val client = createKlasHttpClient(
            MockEngine { request ->
                requestedUrl = request.url.toString()
                respond(
                    content = "{\"loginPwd\":\"encrypted-password\"}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val result = KlasAuthRepository(client).encrypt(
            "2026000000",
            PlainPassword.of("plain-password"),
        )

        assertEquals(KlasUrls.KLAS_PASSWORD_ENCRYPT, requestedUrl)
        assertEquals(
            PasswordEncryptionResult.Success(SecretValue.of("encrypted-password")),
            result,
        )
        client.close()
    }

    @Test
    fun malformedAndBlankResponsesAreRejected() = runBlocking {
        suspend fun resultFor(body: String): PasswordEncryptionResult {
            val client = createKlasHttpClient(
                MockEngine {
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            )
            return KlasAuthRepository(client).encrypt("2026000000", PlainPassword.of("password"))
                .also { client.close() }
        }

        assertEquals(
            PasswordEncryptionResult.Failure(AuthFailure.MalformedResponse),
            resultFor("{}"),
        )
        assertEquals(
            PasswordEncryptionResult.Failure(AuthFailure.MalformedResponse),
            resultFor("{\"loginPwd\":\"\"}"),
        )
    }

    @Test
    fun nonSuccessfulHttpStatusIsNetworkFailure() = runBlocking {
        val client = createKlasHttpClient(
            MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) },
        )

        val result = KlasAuthRepository(client).encrypt(
            "2026000000",
            PlainPassword.of("password"),
        )

        assertEquals(PasswordEncryptionResult.Failure(AuthFailure.Network), result)
        client.close()
    }
}
