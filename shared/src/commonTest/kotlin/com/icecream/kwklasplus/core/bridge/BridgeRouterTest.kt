package com.icecream.kwklasplus.core.bridge

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BridgeRouterTest {
    private val context = BridgeContext(
        BridgeSurface.HOME,
        "https://klasplus.yuntae.in",
        isMainFrame = true,
        payloadSizeBytes = 0,
    )

    @Test
    fun routesOnlyValidatedCommands() = runBridgeRouterTest {
        var received: ValidatedBridgeCommand? = null
        val router = BridgeRouter(BridgeCommandHandler { command ->
            received = command
            BridgeHandlerResult.Success(BridgeValue.Text("done"))
        })
        val request = request("changeTab", BridgeValue.Text("feed"))

        val response = router.route(request, context)

        assertEquals("changeTab", received?.method?.name)
        assertEquals(BridgeMethodId.HOME_CHANGE_TAB, received?.methodId)
        assertEquals(BridgeSurface.HOME, received?.surface)
        assertEquals(
            BridgeResponse.Success(1, "request-1", BridgeValue.Text("done")),
            response,
        )
    }

    @Test
    fun rejectionDoesNotInvokeHandler() = runBridgeRouterTest {
        var invoked = false
        val router = BridgeRouter(BridgeCommandHandler {
            invoked = true
            BridgeHandlerResult.Success()
        })

        val response = router.route(request("unknown"), context)

        assertEquals(false, invoked)
        assertEquals(
            BridgeResponse.Failure(1, "request-1", BridgeErrorCode.UNKNOWN_METHOD),
            response,
        )
    }

    @Test
    fun handlerExceptionsReturnRedactedFailure() = runBridgeRouterTest {
        val router = BridgeRouter(BridgeCommandHandler { error("secret-token") })

        val response = router.route(request("completePageLoad"), context)

        assertEquals(
            BridgeResponse.Failure(1, "request-1", BridgeErrorCode.HANDLER_FAILURE),
            response,
        )
        assertEquals(false, response.toString().contains("secret-token"))
    }

    @Test
    fun synchronousRouteAcceptsOnlySynchronousCatalogMethods() {
        val router = BridgeRouter(
            BridgeCommandHandler { BridgeHandlerResult.Success() },
            SynchronousBridgeCommandHandler {
                BridgeHandlerResult.Success(BridgeValue.Text("settings"))
            },
        )
        val settingsContext = context.copy(surface = BridgeSurface.SETTINGS)

        assertEquals(
            BridgeResponse.Success(1, "request-1", BridgeValue.Text("settings")),
            router.routeSynchronously(request("getAppLockSettings"), settingsContext),
        )
        assertEquals(
            BridgeResponse.Failure(1, "request-1", BridgeErrorCode.ASYNC_METHOD_REQUIRED),
            router.routeSynchronously(request("completePageLoad"), settingsContext),
        )
    }

    @Test
    fun everyCatalogMethodBecomesAValidatedCommand() = runBridgeRouterTest {
        val received = mutableListOf<Pair<BridgeSurface, String>>()
        val router = BridgeRouter(BridgeCommandHandler { command ->
            received += command.surface to command.method.name
            BridgeHandlerResult.Success()
        })

        LegacyBridgeCatalog.methods.forEach { (surface, methods) ->
            methods.forEach { method ->
                router.route(
                    BridgeRequest(
                        BridgeValidator.CURRENT_VERSION,
                        "${surface.name}-${method.name}",
                        method.name,
                        method.arguments.map(::sampleValue),
                    ),
                    context.copy(surface = surface),
                )
            }
        }

        assertEquals(57, received.size)
        assertEquals(57, received.distinct().size)
        assertEquals(57, BridgeMethodId.entries.size)
    }

    private fun request(method: String, vararg arguments: BridgeValue) = BridgeRequest(
        BridgeValidator.CURRENT_VERSION,
        "request-1",
        method,
        arguments.toList(),
    )

    private fun sampleValue(type: BridgeArgumentType): BridgeValue = when (type) {
        BridgeArgumentType.STRING,
        BridgeArgumentType.NULLABLE_STRING,
        -> BridgeValue.Text("value")
        BridgeArgumentType.BOOLEAN -> BridgeValue.BooleanValue(true)
    }
}

private fun <T> runBridgeRouterTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
