package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.IosSharedDependencies
import com.icecream.kwklasplus.core.session.IosSessionMutationGuard
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IosAcademicWidgetAppGroupTest {
    @Test
    fun configuredAppGroupIsUsed() {
        assertEquals(
            "group.dev.example.klas",
            IosAcademicWidgetDisplayStore.resolveAppGroup(" group.dev.example.klas "),
        )
    }

    @Test
    fun missingOrUnexpandedValueHasNoAppGroup() {
        listOf(null, "", "  ", "$(APP_GROUP_IDENTIFIER)", "com.icecream.kwklasplus").forEach { configured ->
            assertNull(IosAcademicWidgetDisplayStore.resolveAppGroup(configured))
        }
    }

    @Test
    fun testBundleWithoutInfoKeyHasNoAppGroup() {
        assertNull(IosAcademicWidgetDisplayStore.APP_GROUP)
    }

    @Test
    fun storesWithoutAppGroupDoNotFallBackToProcessStorage() {
        val store = IosAcademicWidgetDisplayStore()
        store.write(AcademicWidgetDisplay(owner = "owner", term = "2026-2"))
        assertNull(store.read())

        val flags = IosAcademicWidgetSharedFlags()
        flags.bumpRevision()
        assertEquals(0L, flags.revision())

        assertNull(IosSharedDependencies.createForWidgetExtension())
    }

    @Test
    fun sessionLockFailsWithoutAppGroup() = runSuspendTest {
        assertFailsWith<IllegalStateException> { IosSessionMutationGuard().acquire() }
        Unit
    }
}
