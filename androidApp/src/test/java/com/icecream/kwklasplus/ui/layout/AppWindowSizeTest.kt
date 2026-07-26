package com.icecream.kwklasplus.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowSizeTest {
    @Test
    fun classifiesPhoneTabletAndExpandedWidthsAtStableBoundaries() {
        assertEquals(AppWindowWidthClass.Compact, classifyWindowWidth(599))
        assertEquals(AppWindowWidthClass.Medium, classifyWindowWidth(600))
        assertEquals(AppWindowWidthClass.Medium, classifyWindowWidth(839))
        assertEquals(AppWindowWidthClass.Expanded, classifyWindowWidth(840))
    }
}
