package com.icecream.kwklasplus.core.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object IosBridgeRouting {
    private val codec = BridgeJsonCodec()

    fun createRouter(
        handler: BridgeCommandHandler,
        synchronousHandler: SynchronousBridgeCommandHandler? = null,
    ): JsonBridgeRouter = JsonBridgeRouter(
        BridgeRouter(handler, synchronousHandler),
        codec,
    )

    fun malformedResponse(): String = codec.malformedResponse()

    fun createRouteScope(): IosBridgeRouteScope = IosBridgeRouteScope()
}

class IosBridgeRouteScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun route(
        router: JsonBridgeRouter,
        payload: String,
        context: BridgeContext,
        callback: (String) -> Unit,
    ) {
        scope.launch {
            callback(router.route(payload, context))
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
