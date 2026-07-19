package com.icecream.kwklasplus.core.profile

import com.icecream.kwklasplus.core.network.createKlasHttpClient
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class IdCardQrRepositoryTest {
    @Test
    fun preservesCookieAndParsesEscapedJavascriptValue() = runBlocking {
        var cookie: String? = null
        val client = createKlasHttpClient(
            MockEngine { request ->
                cookie = request.headers[HttpHeaders.Cookie]
                respond("{ text: \"QR-\\u0031\" }", HttpStatusCode.OK)
            },
        )

        val result = IdCardQrRepository(client).fetch(
            IdCardQrRequest(
                "https://klas.kw.ac.kr/path/myidv2_main.php?menu=qid",
                SecretValue.of("SESSION=secret"),
            ),
        )

        assertEquals(IdCardQrResult.Success("QR-1"), result)
        assertEquals("SESSION=secret", cookie)
        client.close()
    }

    @Test
    fun rejectsUntrustedAndMalformedResponses() = runBlocking {
        val client = createKlasHttpClient(MockEngine { respond("no value", HttpStatusCode.OK) })
        val repository = IdCardQrRepository(client)

        assertEquals(
            IdCardQrResult.UntrustedUrl,
            repository.fetch(IdCardQrRequest("https://evil.example/qid", SecretValue.of("cookie"))),
        )
        assertEquals(
            IdCardQrResult.InvalidResponse,
            repository.fetch(IdCardQrRequest("https://klas.kw.ac.kr/qid", SecretValue.of("cookie"))),
        )
        client.close()
    }
}
