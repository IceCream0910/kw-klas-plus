package com.icecream.kwklasplus.core.academic

import kotlin.test.*

class WidgetDestinationTest {
    @Test fun onlyFixedWidgetDestinationsAreAccepted() {
        assertEquals("timetable", WidgetDestination.fromUri("klasplus://widget/timetable")?.tab)
        assertEquals("calendar", WidgetDestination.fromUri("klasplus://widget/calendar")?.tab)
        for (uri in listOf(null, "https://example.com/calendar", "klasplus://widget/menu",
            "klasplus://widget/calendar?url=https://example.com", "klasplus://other/calendar"))
            assertNull(WidgetDestination.fromUri(uri))
    }

    @Test fun weekendsDoNotAffectWeekdayGridAndAreSortedForList() {
        val mon = TimetableEntry("월", 0, "9:0", "10:0", "", "M")
        val sat = mon.copy(title = "토", day = 5)
        val sun = mon.copy(title = "일", day = 6)
        val later = sat.copy(startTime = "11:0")
        val entries = listOf(sun, later, mon, sat)
        assertEquals(listOf(mon), AcademicWidgetPolicy.weekdays(entries))
        assertEquals(listOf(sat, later, sun), AcademicWidgetPolicy.weekends(entries))
        assertEquals(emptyList(), AcademicWidgetPolicy.weekdays(listOf(sat, sun)))
        assertEquals(emptyList(), AcademicWidgetPolicy.weekends(listOf(mon)))
    }
}
