package com.icecream.kwklasplus.widget

import kotlin.math.floor
import kotlin.math.sqrt

internal data class WidgetBitmapResolution(val width: Int, val height: Int) {
    companion object {
        fun calculate(widthDp: Int, heightDp: Int, density: Float, screenWidth: Int, screenHeight: Int,
                      variants: Int = 2): WidgetBitmapResolution {
            val w = widthDp.coerceAtLeast(1)
            val h = heightDp.coerceAtLeast(1)
            val pixelsPerVariant = (screenWidth.toDouble().coerceAtLeast(1.0) *
                screenHeight.coerceAtLeast(1) * 1.2 / variants.coerceAtLeast(1)).coerceAtLeast(1.0)
            val scale = minOf(density.toDouble().coerceAtLeast(1.0), sqrt(pixelsPerVariant / (w.toDouble() * h)))
            return WidgetBitmapResolution(floor(w * scale).toInt().coerceAtLeast(1),
                floor(h * scale).toInt().coerceAtLeast(1))
        }
    }
}
