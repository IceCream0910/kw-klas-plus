package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.demo.DemoSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDemoWidgetLifecycleTest {
    @AfterTest fun cleanup() { DemoSession.end() }

    @Test fun homeWidgetRecreationDoesNotRegisterMoreDemoEndListeners() {
        var applicationReloads = 0
        var temporaryReloads = 0
        repeat(3) {
            IosAcademicWidgets.installDemoLifecycle { applicationReloads++ }
            IosAcademicWidgets(
                preferences = { null },
                store = IosAcademicWidgetDisplayStore(defaults = null),
                calendarSync = null,
                credential = { error("real credential must not be read") },
                session = { error("real session must not be read") },
                reloader = { temporaryReloads++ },
            )
        }
        DemoSession.restore()
        DemoSession.end()
        assertEquals(1, applicationReloads)
        assertEquals(0, temporaryReloads)
        DemoSession.restore()
        DemoSession.expire()
        assertEquals(2, applicationReloads)
        assertEquals(0, temporaryReloads)
    }
}
