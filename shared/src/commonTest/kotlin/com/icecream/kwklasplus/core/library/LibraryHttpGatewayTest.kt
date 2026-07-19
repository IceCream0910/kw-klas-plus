package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.network.createKlasHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LibraryHttpGatewayTest {
    @Test
    fun sendsLegacyThreeStepFormContractAndParsesResponses() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val responses = ArrayDeque(
            listOf(
                "<response><sec_key><![CDATA[1234567890123456]]></sec_key></response>",
                "<response><auth_key>AUTH</auth_key></response>",
                "<response><qr_code>QR-VALUE</qr_code><user_name>학생</user_name></response>",
            ),
        )
        val gateway = LibraryHttpGateway(
            client = createKlasHttpClient(
                MockEngine { request ->
                    requests += request
                    respond(responses.removeFirst(), HttpStatusCode.OK)
                },
            ),
        )

        assertEquals(
            LibraryGatewayResult.Success("1234567890123456"),
            gateway.requestSecret("REAL"),
        )
        assertEquals(
            LibraryGatewayResult.Success("AUTH"),
            gateway.login("REAL", "STUDENT", "01012345678", "PASSWORD", "A"),
        )
        val qr = assertIs<LibraryGatewayResult.Success<LibraryQrData>>(
            gateway.requestQr("REAL", "AUTH"),
        )
        assertEquals("QR-VALUE", qr.value.values["qr_code"])

        assertEquals("/mobile/MA/xml_user_key.php", requests[0].url.encodedPath)
        assertEquals("REAL", requests[0].form()["user_id"])
        assertEquals("A", requests[1].form()["device_gb"])
        assertEquals("01012345678", requests[1].form()["tel_no"])
        assertEquals("Y", requests[2].form()["new_check"])
    }

    @Test
    fun mapsHttpAndMalformedResponsesWithoutLeakingPayloads() = runBlocking {
        val httpFailure = LibraryHttpGateway(
            createKlasHttpClient(MockEngine { respond("secret", HttpStatusCode.ServiceUnavailable) }),
        ).requestSecret("REAL")
        val malformed = LibraryHttpGateway(
            createKlasHttpClient(MockEngine { respond("<response>", HttpStatusCode.OK) }),
        ).requestSecret("REAL")

        assertEquals(LibraryGatewayResult.NetworkFailure, httpFailure)
        assertEquals(LibraryGatewayResult.InvalidResponse, malformed)
    }

    private fun HttpRequestData.form() =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            .split('&')
            .associate { field ->
                field.substringBefore('=') to field.substringAfter('=')
            }
}
