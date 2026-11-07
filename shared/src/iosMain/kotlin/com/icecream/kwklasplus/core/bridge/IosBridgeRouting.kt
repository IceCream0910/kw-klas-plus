package com.icecream.kwklasplus.core.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

object IosBridgeRouting {
    private val dependencies by lazy { com.icecream.kwklasplus.core.IosSharedDependencies() }
    private val codec = BridgeJsonCodec()

    fun createRouter(
        handler: BridgeCommandHandler,
        synchronousHandler: SynchronousBridgeCommandHandler? = null,
        presentDemoScript: (String) -> Unit = {},
    ): JsonBridgeRouter = JsonBridgeRouter(
        BridgeRouter(com.icecream.kwklasplus.core.demo.DemoBridgeCommandHandler(KlasApiBridgeHandler(dependencies.klasApiCore, handler), presentDemoScript), synchronousHandler),
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

    fun reset() { scope.coroutineContext.cancelChildren() }

    fun dispose() {
        scope.cancel()
    }
}
