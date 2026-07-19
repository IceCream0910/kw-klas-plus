package com.icecream.kwklasplus.core.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface BridgeDecodeResult {
    data class Success(val request: BridgeRequest, val payloadSizeBytes: Int) : BridgeDecodeResult
    data object Malformed : BridgeDecodeResult
}

class BridgeJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val maximumNestingDepth: Int = 16,
) {
    fun decodeRequest(payload: String): BridgeDecodeResult = try {
        val root = json.parseToJsonElement(payload).jsonObject
        if (root.keys.any { it !in REQUEST_FIELDS }) return BridgeDecodeResult.Malformed
        val version = root.requiredNumber("version").intOrNull
            ?: return BridgeDecodeResult.Malformed
        val id = root.requiredString("id")
        val method = root.requiredString("method")
        val arguments = root["arguments"]?.jsonArray
            ?.map { decodeValue(it, 0) ?: return BridgeDecodeResult.Malformed }
            ?: return BridgeDecodeResult.Malformed
        BridgeDecodeResult.Success(
            BridgeRequest(version, id, method, arguments),
            payload.encodeToByteArray().size,
        )
    } catch (_: Throwable) {
        BridgeDecodeResult.Malformed
    }

    fun encodeResponse(response: BridgeResponse): String = json.encodeToString(
        JsonElement.serializer(),
        when (response) {
            is BridgeResponse.Success -> buildJsonObject {
                put("version", response.version)
                put("id", response.id)
                put("ok", true)
                put("result", encodeValue(response.result))
            }
            is BridgeResponse.Failure -> buildJsonObject {
                put("version", response.version)
                if (response.id == null) put("id", JsonNull) else put("id", response.id)
                put("ok", false)
                put("error", buildJsonObject { put("code", response.error.name) })
            }
        },
    )

    fun encodeEvent(event: BridgeEvent): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("version", event.version)
            put("id", event.id)
            put("event", event.event.wireName)
            put("payload", encodeValue(event.payload))
        },
    )

    fun malformedResponse(): String = encodeResponse(
        BridgeResponse.Failure(
            BridgeValidator.CURRENT_VERSION,
            null,
            BridgeErrorCode.MALFORMED_REQUEST,
        ),
    )

    private fun decodeValue(element: JsonElement, depth: Int): BridgeValue? {
        if (depth > maximumNestingDepth) return null
        return when (element) {
            JsonNull -> BridgeValue.Null
            is JsonPrimitive -> when {
                element.isString -> BridgeValue.Text(element.content)
                element.booleanOrNull != null -> BridgeValue.BooleanValue(element.booleanOrNull!!)
                element.doubleOrNull != null -> BridgeValue.NumberValue(element.doubleOrNull!!)
                else -> null
            }
            is JsonObject -> BridgeValue.ObjectValue(
                element.mapValues { decodeValue(it.value, depth + 1) ?: return null },
            )
            is JsonArray -> BridgeValue.ListValue(
                element.map { decodeValue(it, depth + 1) ?: return null },
            )
        }
    }

    private fun encodeValue(value: BridgeValue): JsonElement = when (value) {
        is BridgeValue.Text -> JsonPrimitive(value.value)
        is BridgeValue.BooleanValue -> JsonPrimitive(value.value)
        is BridgeValue.NumberValue -> JsonPrimitive(value.value)
        is BridgeValue.ObjectValue -> JsonObject(value.value.mapValues { encodeValue(it.value) })
        is BridgeValue.ListValue -> JsonArray(value.value.map(::encodeValue))
        BridgeValue.Null -> JsonNull
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)?.jsonPrimitive ?: error("missing field")
        if (!value.isString) error("invalid string field")
        return value.content
    }

    private fun JsonObject.requiredNumber(name: String): JsonPrimitive {
        val value = get(name)?.jsonPrimitive ?: error("missing field")
        if (value.isString) error("invalid number field")
        return value
    }

    private companion object {
        val REQUEST_FIELDS = setOf("version", "id", "method", "arguments")
    }
}
