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
    fun hostileInputsAreRejectedBeforeJsonParsingInBothRoutes() = runJsonBridgeRouterTest {
        var calls = 0
        val router = JsonBridgeRouter(BridgeRouter(BridgeCommandHandler {
            calls++
            BridgeHandlerResult.Success()
        }))
        val oversizedMalformed = "[".repeat(100_000)
        for ((inputContext, code) in listOf(
            context.copy(origin = "https://attacker.invalid") to "UNTRUSTED_ORIGIN",
            context.copy(isMainFrame = false) to "NOT_MAIN_FRAME",
            context to "PAYLOAD_TOO_LARGE",
        )) {
            assertContains(router.route(oversizedMalformed, inputContext), code)
            assertContains(router.routeSynchronously(oversizedMalformed, inputContext), code)
        }
        kotlin.test.assertEquals(0, calls)
    }

    @Test
    fun legitimateLimitAndVideoOriginRemainAccepted() = runJsonBridgeRouterTest {
        val payload = """{"version":1,"id":"request","method":"completePageLoad","arguments":[]}"""
        val router = JsonBridgeRouter(BridgeRouter(
            BridgeCommandHandler { BridgeHandlerResult.Success() },
            validator = BridgeValidator(maximumPayloadSizeBytes = payload.encodeToByteArray().size),
        ))
        assertContains(router.route(payload, context), "\"ok\":true")
        assertContains(router.route(payload, context.copy(
            surface = BridgeSurface.VIDEO, origin = "https://video.kw.ac.kr",
        )), "\"ok\":true")
    }

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
