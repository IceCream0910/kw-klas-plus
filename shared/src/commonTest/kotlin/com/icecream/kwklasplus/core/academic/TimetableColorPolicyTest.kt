package com.icecream.kwklasplus.core.academic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimetableColorPolicyTest {
    private val titles = listOf("자료구조", "운영체제", "컴퓨터구조", "알고리즘", "네트워크", "데이터베이스", "인공지능", "소프트웨어공학")

    @Test
    fun defaultPaletteSupportsEightDistinctCourses() {
        val palette = TimetableColorPolicy.defaultPalette
        val assigned = TimetableColorPolicy.assign(titles)

        assertEquals(8, palette.light.size)
        assertEquals(8, palette.dark.size)
        assertEquals(8, assigned.values.map(TimetableColor::slot).distinct().size)
    }

    @Test
    fun assignmentIsStableAndAvoidsHashCollisions() {
        val forward = TimetableColorPolicy.assign(titles)
        val reversed = TimetableColorPolicy.assign(titles.reversed())

        assertEquals(forward, reversed)
        assertEquals(forward.getValue("자료구조"), TimetableColorPolicy.assignedColor(" 자료구조 ", forward))
    }

    @Test
    fun paletteReuseStartsAfterAllEightSlotsAreUsed() {
        val assigned = TimetableColorPolicy.assign(titles + "컴파일러")

        assertEquals(8, assigned.values.map(TimetableColor::slot).distinct().size)
        assertEquals(9, assigned.size)
    }

    @Test
    fun everyThemePairMeetsTextContrastRequirement() {
        val colors = TimetableColorPolicy.defaultPalette.let { it.light + it.dark }
        colors.forEach { color ->
            assertTrue(contrast(color.background, color.text) >= 4.5, "${color.background}/${color.text}")
        }
    }

    private fun contrast(first: String, second: String): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(hex: String): Double {
        val components = listOf(hex.substring(1, 3), hex.substring(3, 5), hex.substring(5, 7))
            .map { it.toInt(16) / 255.0 }
            .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return components[0] * 0.2126 + components[1] * 0.7152 + components[2] * 0.0722
    }
}
