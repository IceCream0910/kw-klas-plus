package com.icecream.kwklasplus.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeValidatorTest {
    @Test
    fun trustedUrlRequiresExactHttpsOrigin() {
        val policy = TrustedOriginPolicy()

        assertTrue(policy.isTrustedUrl("https://klas.kw.ac.kr/std/page"))
        assertTrue(policy.isTrustedUrl("https://KLASPLUS.YUNTAE.IN/feed"))
        assertFalse(policy.isTrustedUrl("http://klas.kw.ac.kr/std/page"))
        assertFalse(policy.isTrustedUrl("https://klas.kw.ac.kr.evil.example/std/page"))
        assertFalse(policy.isTrustedUrl("https://klas.kw.ac.kr@evil.example/std/page"))
        assertFalse(policy.isTrustedUrl("https://klas.kw.ac.kr:443/std/page"))
    }

    @Test
    fun klasContentPolicyAcceptsOnlyHttpsKwHostFamily() {
        val policy = KlasContentOriginPolicy()

        assertTrue(policy.isTrustedUrl("https://vod.kw.ac.kr/player/content"))
        assertTrue(policy.isTrustedUrl("https://klas.kw.ac.kr/player"))
        assertTrue(policy.isTrustedUrl("https://kw.ac.kr/"))
        assertTrue(policy.isTrustedVideoUrl("https://vod.kw.ac.kr/player/content"))
        assertFalse(policy.isTrustedVideoUrl("https://kw.ac.kr/"))
        assertFalse(policy.isTrustedUrl("http://vod.kw.ac.kr/player"))
        assertFalse(policy.isTrustedUrl("https://vod.kw.ac.kr.evil.example/player"))
        assertFalse(policy.isTrustedUrl("https://evil.example/?next=vod.kw.ac.kr"))
        assertFalse(policy.isTrustedUrl("https://vod.kw.ac.kr:443/player"))
    }

    private val validator = BridgeValidator()

    @Test
    fun catalogCapturesEveryLegacyMethodSignature() {
        assertEquals(57, LegacyBridgeCatalog.methods.values.sumOf { it.size })
        assertEquals(57, BridgeMethodId.entries.size)
        assertTrue(LegacyBridgeCatalog.methods.all { (surface, methods) ->
            methods.all { BridgeMethodId.from(surface, it.name) != null }
        })
        assertTrue(LegacyBridgeCatalog.methods.values.all { methods ->
            methods.distinctBy { it.name }.size == methods.size
        })

        val typoContract = LegacyBridgeCatalog.find(BridgeSurface.LECTURE, "evaluteKLASScript")
        assertNotNull(typoContract)
        assertEquals(listOf(BridgeArgumentType.STRING), typoContract.arguments)

        val synchronousContract = LegacyBridgeCatalog.find(BridgeSurface.SETTINGS, "getAppLockSettings")
        assertNotNull(synchronousContract)
        assertTrue(synchronousContract.synchronousReturn)
    }

    @Test
    fun rejectsRemovedWebViewModalBridge() {
        val result = validator.validate(
            request("openCustomBottomSheet", BridgeValue.Text("/modal")),
            context(BridgeSurface.HOME),
        )

        assertEquals(
            BridgeValidationResult.Rejected(BridgeRejection.UNKNOWN_METHOD),
            result,
        )
    }

    @Test
    fun rejectsUnknownMethodAndWrongArgumentType() {
        val unknown = validator.validate(request("missingMethod"), context(BridgeSurface.HOME))
        val wrongType = validator.validate(
            request("setAppLockEnabled", BridgeValue.Text("true")),
            context(BridgeSurface.SETTINGS),
        )

        assertEquals(BridgeValidationResult.Rejected(BridgeRejection.UNKNOWN_METHOD), unknown)
        assertEquals(BridgeValidationResult.Rejected(BridgeRejection.INVALID_ARGUMENT_TYPE), wrongType)
    }

    @Test
    fun rejectsExternalOriginSubframeAndOversizedPayload() {
        val request = request("completePageLoad")

        assertEquals(
            BridgeValidationResult.Rejected(BridgeRejection.UNTRUSTED_ORIGIN),
            validator.validate(request, context(BridgeSurface.HOME, origin = "https://evil.example")),
        )
        assertEquals(
            BridgeValidationResult.Rejected(BridgeRejection.NOT_MAIN_FRAME),
            validator.validate(request, context(BridgeSurface.HOME, isMainFrame = false)),
        )
        assertEquals(
            BridgeValidationResult.Rejected(BridgeRejection.PAYLOAD_TOO_LARGE),
            validator.validate(request, context(BridgeSurface.HOME, payloadSizeBytes = 65_537)),
        )
    }

    @Test
    fun originMatchingIsExact() {
        val policy = TrustedOriginPolicy()

        assertTrue(policy.isTrusted("https://klas.kw.ac.kr"))
        assertTrue(!policy.isTrusted("http://klas.kw.ac.kr"))
        assertTrue(!policy.isTrusted("https://klas.kw.ac.kr.evil.example"))
    }

    @Test
    fun videoBridgeAcceptsOnlyHttpsKwSubdomains() {
        val request = BridgeRequest(
            version = BridgeValidator.CURRENT_VERSION,
            id = "video-state-1",
            method = "receiveInitSpeed",
            arguments = listOf(BridgeValue.Text("1.0")),
        )

        assertTrue(
            validator.validate(
                request,
                BridgeContext(BridgeSurface.VIDEO, "https://vod.kw.ac.kr", true, 100),
            ) is BridgeValidationResult.Accepted,
        )
        listOf(
            "https://kw.ac.kr",
            "http://vod.kw.ac.kr",
            "https://vod.kw.ac.kr.evil.example",
            "https://vod.kw.ac.kr:443",
        ).forEach { origin ->
            assertEquals(
                BridgeValidationResult.Rejected(BridgeRejection.UNTRUSTED_ORIGIN),
                validator.validate(
                    request,
                    BridgeContext(BridgeSurface.VIDEO, origin, true, 100),
                ),
            )
        }
    }

    private fun request(method: String, vararg arguments: BridgeValue) = BridgeRequest(
        version = BridgeValidator.CURRENT_VERSION,
        id = "request-1",
        method = method,
        arguments = arguments.toList(),
    )

    private fun context(
        surface: BridgeSurface,
        origin: String = "https://klasplus.yuntae.in",
        isMainFrame: Boolean = true,
        payloadSizeBytes: Int = 128,
    ) = BridgeContext(surface, origin, isMainFrame, payloadSizeBytes)
}
