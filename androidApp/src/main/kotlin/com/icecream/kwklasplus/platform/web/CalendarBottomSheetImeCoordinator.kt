package com.icecream.kwklasplus.platform.web

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

internal class CalendarBottomSheetImeCoordinator(
    private val rootView: View,
    private val density: Float,
    private val onFooterInsetChanged: (Int) -> Unit,
) {
    private var active = false
    private var appliedInsetCssPx = 0

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (active) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
                applyInsets(insets)
                insets
            }
            ViewCompat.requestApplyInsets(rootView)
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
            applyFooterInset(0)
        }
    }

    fun dispose() {
        active = false
        ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        applyFooterInset(0)
    }

    private fun applyInsets(insets: WindowInsetsCompat) {
        if (!active) return
        val navigationBarBottom = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.navigationBars(),
        ).bottom
        val tappableElementBottom = insets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.tappableElement(),
        ).bottom
        val targetInset = WebBottomSheetImePolicy.resolveFooterInsetCssPx(
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()),
            navigationBarBottomPx = navigationBarBottom,
            tappableElementBottomPx = tappableElementBottom,
            density = density,
        )
        applyFooterInset(targetInset)
    }

    private fun applyFooterInset(insetCssPx: Int) {
        if (appliedInsetCssPx == insetCssPx) return
        appliedInsetCssPx = insetCssPx
        onFooterInsetChanged(insetCssPx)
    }
}

internal object WebBottomSheetImePolicy {
    fun resolveFooterInsetCssPx(
        imeVisible: Boolean,
        navigationBarBottomPx: Int,
        tappableElementBottomPx: Int,
        density: Float,
    ): Int {
        if (!imeVisible || tappableElementBottomPx <= 0 || density <= 0f) return 0
        return (navigationBarBottomPx.coerceAtLeast(0) / density).roundToInt()
    }
}
