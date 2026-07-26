package com.icecream.kwklasplus.ui.layout

enum class AppWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

fun classifyWindowWidth(widthDp: Int): AppWindowWidthClass = when {
    widthDp < 600 -> AppWindowWidthClass.Compact
    widthDp < 840 -> AppWindowWidthClass.Medium
    else -> AppWindowWidthClass.Expanded
}
