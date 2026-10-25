package com.icecream.kwklasplus.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals

class IosAcademicWidgetAppGroupTest {
    @Test
    fun releaseAppGroupStaysFixed() {
        assertEquals("group.com.icecream.kwklasplus.Q2C928H69W", IosAcademicWidgetDisplayStore.RELEASE_APP_GROUP)
    }

    @Test
    fun configuredAppGroupIsUsed() {
        assertEquals(
            "group.dev.example.klas",
            IosAcademicWidgetDisplayStore.resolveAppGroup(" group.dev.example.klas "),
        )
    }

    @Test
    fun missingOrUnexpandedValueFallsBackToReleaseGroup() {
        listOf(null, "", "  ", "$(APP_GROUP_IDENTIFIER)", "com.icecream.kwklasplus").forEach { configured ->
            assertEquals(
                IosAcademicWidgetDisplayStore.RELEASE_APP_GROUP,
                IosAcademicWidgetDisplayStore.resolveAppGroup(configured),
            )
        }
    }

    @Test
    fun testBundleWithoutInfoKeyUsesReleaseGroup() {
        assertEquals(IosAcademicWidgetDisplayStore.RELEASE_APP_GROUP, IosAcademicWidgetDisplayStore.APP_GROUP)
    }
}
