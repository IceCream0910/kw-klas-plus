package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExternalNavigationPolicyTest {
    private val policy = ExternalNavigationPolicy()

    @Test
    fun allowsHttpAndHttpsDestinations() {
        assertEquals(
            ExternalNavigationResolution.Allowed(ExternalDestination.Web("https://klas.kw.ac.kr/path?q=1")),
            policy.resolve("https://klas.kw.ac.kr/path?q=1"),
        )
        assertEquals(
            ExternalNavigationResolution.Allowed(ExternalDestination.Web("http://example.com")),
            policy.resolve("http://example.com"),
        )
    }

    @Test
    fun mapsMailAndTelephoneDestinations() {
        assertEquals(
            ExternalNavigationResolution.Allowed(ExternalDestination.Email("help@example.com")),
            policy.resolve("mailto:help@example.com"),
        )
        assertEquals(
            ExternalNavigationResolution.Allowed(ExternalDestination.Telephone("+82 2-123-4567")),
            policy.resolve("tel:+82 2-123-4567"),
        )
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousValues() {
        listOf(
            "javascript:alert(1)",
            "intent://settings",
            "file:///data/local/file",
            "https:klas.kw.ac.kr",
            "https://user@example.com",
            " https://example.com",
            "https://example.com\njavascript:alert(1)",
            "mailto:not-an-address",
            "tel:*123#",
        ).forEach { assertIs<ExternalNavigationResolution.Rejected>(policy.resolve(it)) }
    }

    @Test
    fun rejectsOversizedValue() {
        assertIs<ExternalNavigationResolution.Rejected>(
            ExternalNavigationPolicy(maximumLength = 16).resolve("https://example.com"),
        )
    }
}
