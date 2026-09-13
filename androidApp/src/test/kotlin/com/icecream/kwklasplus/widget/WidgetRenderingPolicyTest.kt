package com.icecream.kwklasplus.widget

import com.icecream.kwklasplus.core.academic.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class WidgetRenderingPolicyTest {
    @Test fun bitmapUsesScreenDensityAndCapsPixelBudget() {
        assertEquals(WidgetBitmapResolution(900, 450), WidgetBitmapResolution.calculate(300, 150, 3f, 1080, 2400, 1))
        val large = WidgetBitmapResolution.calculate(2000, 1000, 4f, 1080, 2400, 1)
        assertTrue(large.width.toLong() * large.height <= 1080 * 2400 * 1.2)
        assertEquals(2.0, large.width.toDouble() / large.height, 0.01)
    }

    @Test fun cacheIgnoresFetchMetadataButTracksRenderInputs() {
        val snapshot = AcademicWidgetSnapshot("owner", "2026,2", timetable = emptyList())
        fun key(data: AcademicWidgetSnapshot = snapshot, date: String = "2026-09-05", dark: Boolean = false) =
            WidgetBitmapKey.create(AcademicWidgetKind.TIMETABLE, data, date, 280, 140, dark, WidgetBitmapResolution(840, 420))
        assertEquals(key(), key(snapshot.copy(timetableFetchedAt = 200, calendarFetchedAt = 300, calendarStatus = WidgetSyncStatus.RETRY)))
        assertNotEquals(key(), key(date = "2026-09-06"))
        assertNotEquals(key(), key(dark = true))
        assertNotEquals(key(), key(snapshot.copy(owner = "other")))
        assertNotEquals(key(), key(snapshot.copy(timetable = listOf(TimetableEntry("수업", 0, "9:0", "10:0", "", "A")))))
    }

    @Test fun everySubjectPaletteHasReadableTextInBothThemes() {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val c = (color shr shift and 255) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val palette = TimetableColorPolicy.defaultPalette
        for ((theme, colors) in listOf("light" to palette.light, "dark" to palette.dark)) for ((slot, color) in colors.withIndex()) {
            val a = luminance(color.text.removePrefix("#").toLong(16).toInt())
            val b = luminance(color.background.removePrefix("#").toLong(16).toInt())
            assertTrue("slot=$slot theme=$theme", (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05) >= 4.5)
        }
    }
}
