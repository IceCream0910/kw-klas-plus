package com.icecream.kwklasplus.platform.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebBottomSheetImePolicyTest {
    @Test
    fun returnsNavigationBarHeightInCssPixelsForThreeButtonIme() {
        assertEquals(
            48,
            WebBottomSheetImePolicy.resolveFooterInsetCssPx(
                imeVisible = true,
                navigationBarBottomPx = 144,
                tappableElementBottomPx = 144,
                density = 3f,
            ),
        )
    }

    @Test
    fun returnsZeroOutsideThreeButtonIme() {
        assertEquals(
            0,
            WebBottomSheetImePolicy.resolveFooterInsetCssPx(true, 72, 0, 3f),
        )
        assertEquals(
            0,
            WebBottomSheetImePolicy.resolveFooterInsetCssPx(false, 144, 144, 3f),
        )
    }
}
