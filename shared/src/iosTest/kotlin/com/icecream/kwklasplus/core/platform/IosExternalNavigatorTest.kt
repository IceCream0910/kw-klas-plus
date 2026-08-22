package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosExternalNavigatorTest {
    @Test
    fun opensAllowedDestinationsAndRejectsMaliciousSchemes() {
        val opened = mutableListOf<String>()
        val navigator = IosExternalNavigator { url ->
            opened += requireNotNull(url.absoluteString)
            true
        }

        assertEquals(
            PlatformActionResult.Success,
            navigator.openValidated("https://klas.kw.ac.kr/std/file"),
        )
        assertEquals(
            PlatformActionResult.Success,
            navigator.openValidated("mailto:help@example.com"),
        )
        assertEquals(
            PlatformActionResult.Success,
            navigator.openValidated("tel:+82-2-123-4567"),
        )

        listOf(
            "javascript:alert(1)",
            "intent://settings",
            "file:///tmp/secret",
            "https://user@example.com",
        ).forEach { raw ->
            assertIs<PlatformActionResult.Failed>(navigator.openValidated(raw))
        }

        assertEquals(
            listOf(
                "https://klas.kw.ac.kr/std/file",
                "mailto:help@example.com",
                "tel:+82-2-123-4567",
            ),
            opened,
        )
        assertTrue(opened.none { it.startsWith("javascript") || it.startsWith("intent") || it.startsWith("file:") })
    }

    @Test
    fun platformUriIsUnsupported() {
        val navigator = IosExternalNavigator { true }
        assertEquals(
            PlatformActionResult.Unsupported,
            navigator.openNow(ExternalDestination.PlatformUri("custom://x")),
        )
    }
}
