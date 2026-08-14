package com.icecream.kwklasplus.core.web

import com.icecream.kwklasplus.core.academic.AcademicTermDisplay
import kotlin.test.Test
import kotlin.test.assertTrue

class IosWebCallbacksTest {
    @Test
    fun receivedDataVariantsUseExpectedArgumentCounts() {
        val two = IosWebCallbacks.receivedData("t", "s").reveal()
        val three = IosWebCallbacks.receivedData("t", "s", "2026,1").reveal()
        val four = IosWebCallbacks.receivedData("t", "s", "2026,1", "path").reveal()
        assertTrue(two.startsWith("window.receivedData("))
        assertTrue(three.contains("\"2026,1\""))
        assertTrue(four.contains("\"path\""))
        assertTrue(IosWebCallbacks.receiveToken("tok").reveal().contains("window.receiveToken"))
        assertTrue(
            IosWebCallbacks.updateYearHakgiButtonText(AcademicTermDisplay.buttonText("2026,1"))
                .reveal()
                .contains("년도"),
        )
    }
}
