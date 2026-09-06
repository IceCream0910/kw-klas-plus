package com.icecream.kwklasplus.widget

import android.graphics.Color
import com.icecream.kwklasplus.core.academic.TimetableColorPolicy

internal object WidgetColors {
    fun text(color: com.icecream.kwklasplus.core.academic.TimetableColor, dark: Boolean): Int = Color.parseColor(
        if (dark) color.dark.text else color.light.text,
    )

    fun fill(color: com.icecream.kwklasplus.core.academic.TimetableColor, dark: Boolean): Int = Color.parseColor(
        if (dark) color.dark.background else color.light.background,
    )

    fun text(title: String, dark: Boolean) = text(TimetableColorPolicy.resolve(title), dark)
    fun fill(title: String, dark: Boolean) = fill(TimetableColorPolicy.resolve(title), dark)
}
