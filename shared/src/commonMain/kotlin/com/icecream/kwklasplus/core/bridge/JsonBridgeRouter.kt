package com.icecream.kwklasplus.core.bridge

class JsonBridgeRouter(
    private val router: BridgeRouter,
    private val codec: BridgeJsonCodec = BridgeJsonCodec(),
) {
    fun preflight(payload: String, context: BridgeContext): String? {
        router.validateContext(context.copy(payloadSizeBytes = 0))?.let { return codec.encodeResponse(it) }
        router.validateContext(context.copy(payloadSizeBytes = router.measurePayload(payload)))
            ?.let { return codec.encodeResponse(it) }
        return null
    }

    suspend fun route(payload: String, context: BridgeContext): String {
        preflight(payload, context)?.let { return it }
        val decoded = codec.decodeRequest(payload)
        if (decoded !is BridgeDecodeResult.Success) return codec.malformedResponse()
        val measuredContext = context.copy(payloadSizeBytes = decoded.payloadSizeBytes)
        return codec.encodeResponse(router.route(decoded.request, measuredContext))
    }

    fun routeSynchronously(payload: String, context: BridgeContext): String {
        preflight(payload, context)?.let { return it }
        val decoded = codec.decodeRequest(payload)
        if (decoded !is BridgeDecodeResult.Success) return codec.malformedResponse()
        val measuredContext = context.copy(payloadSizeBytes = decoded.payloadSizeBytes)
        return codec.encodeResponse(router.routeSynchronously(decoded.request, measuredContext))
    }
}
