package com.icecream.kwklasplus.core.bridge

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContains

class JsonBridgeRouterTest {
    private val context = BridgeContext(
        BridgeSurface.HOME,
        "https://klasplus.yuntae.in",
        isMainFrame = true,
        payloadSizeBytes = 0,
    )

    @Test
    fun malformedPayloadReturnsStableErrorEnvelope() = runJsonBridgeRouterTest {
        val router = JsonBridgeRouter(
            BridgeRouter(BridgeCommandHandler { BridgeHandlerResult.Success() }),
        )

        val response = router.route("not-json", context)

        assertContains(response, "\"ok\":false")
        assertContains(response, "\"code\":\"MALFORMED_REQUEST\"")
    }

    @Test
    fun measuredUtf8PayloadOverridesCallerSuppliedSize() = runJsonBridgeRouterTest {
        val router = JsonBridgeRouter(
            BridgeRouter(
                BridgeCommandHandler { BridgeHandlerResult.Success() },
                validator = BridgeValidator(maximumPayloadSizeBytes = 100),
            ),
        )
        val payload = """{"version":1,"id":"request","method":"changeTab","arguments":["${"가".repeat(100)}"]}"""

        val response = router.route(payload, context.copy(payloadSizeBytes = 1))

        assertContains(response, "\"code\":\"PAYLOAD_TOO_LARGE\"")
    }
}

private fun <T> runJsonBridgeRouterTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
