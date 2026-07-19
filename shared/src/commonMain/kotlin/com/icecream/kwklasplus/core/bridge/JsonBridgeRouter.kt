package com.icecream.kwklasplus.core.bridge

class JsonBridgeRouter(
    private val router: BridgeRouter,
    private val codec: BridgeJsonCodec = BridgeJsonCodec(),
) {
    suspend fun route(payload: String, context: BridgeContext): String {
        val decoded = codec.decodeRequest(payload)
        if (decoded !is BridgeDecodeResult.Success) return codec.malformedResponse()
        val measuredContext = context.copy(payloadSizeBytes = decoded.payloadSizeBytes)
        return codec.encodeResponse(router.route(decoded.request, measuredContext))
    }

    fun routeSynchronously(payload: String, context: BridgeContext): String {
        val decoded = codec.decodeRequest(payload)
        if (decoded !is BridgeDecodeResult.Success) return codec.malformedResponse()
        val measuredContext = context.copy(payloadSizeBytes = decoded.payloadSizeBytes)
        return codec.encodeResponse(router.routeSynchronously(decoded.request, measuredContext))
    }
}
