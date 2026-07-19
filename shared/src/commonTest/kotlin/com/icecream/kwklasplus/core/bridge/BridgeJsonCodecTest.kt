package com.icecream.kwklasplus.core.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BridgeJsonCodecTest {
    private val codec = BridgeJsonCodec()

    @Test
    fun decodesStrictRequestAndMeasuresUtf8Payload() {
        val payload = """{"version":1,"id":"요청-1","method":"changeTab","arguments":["피드",true,null]}"""

        val decoded = assertIs<BridgeDecodeResult.Success>(codec.decodeRequest(payload))

        assertEquals("요청-1", decoded.request.id)
        assertEquals(
            listOf(
                BridgeValue.Text("피드"),
                BridgeValue.BooleanValue(true),
                BridgeValue.Null,
            ),
            decoded.request.arguments,
        )
        assertEquals(payload.encodeToByteArray().size, decoded.payloadSizeBytes)
    }

    @Test
    fun rejectsMissingUnknownAndWrongTypedEnvelopeFields() {
        assertEquals(
            BridgeDecodeResult.Malformed,
            codec.decodeRequest("""{"version":1,"id":"id","method":"reload"}"""),
        )
        assertEquals(
            BridgeDecodeResult.Malformed,
            codec.decodeRequest("""{"version":1,"id":"id","method":"reload","arguments":[],"extra":1}"""),
        )
        assertEquals(
            BridgeDecodeResult.Malformed,
            codec.decodeRequest("""{"version":"1","id":1,"method":"reload","arguments":[]}"""),
        )
    }

    @Test
    fun responseEnvelopeContainsNoExceptionMessage() {
        val encoded = codec.encodeResponse(
            BridgeResponse.Failure(1, "request-1", BridgeErrorCode.HANDLER_FAILURE),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(false, root.getValue("ok").jsonPrimitive.content.toBoolean())
        assertEquals(
            "HANDLER_FAILURE",
            root.getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
        )
        assertTrue(!encoded.contains("message"))
    }

    @Test
    fun eventEnvelopeSerializesTypedEventAndJsonSafePayload() {
        val encoded = codec.encodeEvent(
            BridgeEvent(
                id = "event-1",
                event = BridgeEventId.ID_CARD_QR_VALUE,
                payload = BridgeValue.ObjectValue(
                    mapOf("value" to BridgeValue.Text("'\"한글\\n")),
                ),
            ),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("profile.idCardQr", root.getValue("event").jsonPrimitive.content)
        assertEquals(
            "'\"한글\\n",
            root.getValue("payload").jsonObject.getValue("value").jsonPrimitive.content,
        )
    }
}
