package com.icecream.kwklasplus.core.academic

import kotlin.test.*

class AcademicWidgetPolicyTest {
    @Test fun layoutUsesCurrentCompactWidthThreshold() {
        assertEquals(AcademicWidgetLayout.COMPACT, AcademicWidgetPolicy.layout(70, 70))
        assertEquals(AcademicWidgetLayout.COMPACT, AcademicWidgetPolicy.layout(140, 70))
        assertEquals(AcademicWidgetLayout.COMPACT, AcademicWidgetPolicy.layout(140, 140))
        assertEquals(AcademicWidgetLayout.COMPACT, AcademicWidgetPolicy.layout(110, 110))
        assertEquals(AcademicWidgetLayout.COMPACT, AcademicWidgetPolicy.layout(149, 140))
        assertEquals(AcademicWidgetLayout.SUMMARY, AcademicWidgetPolicy.layout(150, 140))
        assertEquals(AcademicWidgetLayout.SUMMARY, AcademicWidgetPolicy.layout(280, 140))
        for (width in listOf(110, 180, 250, 320, 600)) {
            for (height in listOf(40, 110, 180, 249))
                assertEquals(if (width < 150) AcademicWidgetLayout.COMPACT else AcademicWidgetLayout.SUMMARY,
                    AcademicWidgetPolicy.layout(width, height))
            for (height in listOf(250, 320, 600))
                assertEquals(AcademicWidgetLayout.FULL, AcademicWidgetPolicy.layout(width, height))
        }
        assertEquals(AcademicWidgetLayout.SUMMARY, AcademicWidgetPolicy.layout(280, 70))
    }

    @Test fun numericClassTimesAndSundayAreHandled() {
        val entries = listOf("10:30", "9:0", "18:0").map { TimetableEntry(it, 0, it, "19:0", "", it) }
        assertEquals(listOf("9:0", "10:30", "18:0"), AcademicWidgetPolicy.todayClasses(entries, 0).map { it.startTime })
        assertTrue(AcademicWidgetPolicy.todayClasses(entries, 6).isEmpty())
    }

    @Test fun agendaUsesTodayThenNextThenMostRecentWithinMonth() {
        val past = event("past", "2026-09-01", "2026-09-02")
        val next = event("next", "2026-09-09", "2026-09-10")
        val outside = event("outside", "2026-10-01", "2026-10-01")
        assertEquals("2026-09-09", AcademicWidgetPolicy.agenda(listOf(past, next, outside), "2026-09-04").date)
        val today = AcademicWidgetPolicy.agenda(listOf(next), "2026-09-10")
        assertEquals(1, today.todayCount)
        assertEquals("2026-09-10", today.date)
        assertEquals("2026-09-10", AcademicWidgetPolicy.agenda(listOf(next, outside), "2026-09-20").date)
        assertTrue(AcademicWidgetPolicy.agenda(listOf(outside), "2026-09-20").events.isEmpty())
    }

    @Test fun monthBarsClipAtMonthAndSplitAcrossWeeksWithoutOverlap() {
        val spanning = event("span", "2026-08-30", "2026-09-09")
        val overlapping = event("overlap", "2026-09-02", "2026-09-03")
        val bars = AcademicWidgetPolicy.monthBars(listOf(spanning, overlapping), "2026-09", 30, 2)
        val span = bars.filter { it.event.id == "span" }
        assertEquals(listOf(0, 1), span.map { it.week })
        assertEquals(listOf(2, 0), span.map { it.column })
        assertEquals(listOf(5, 4), span.map { it.span })
        assertEquals(1, bars.single { it.event.id == "overlap" }.lane)
    }

    @Test fun snapshotPreservesUnknownVersusSuccessfullyEmpty() {
        val unknown = AcademicWidgetSnapshot("owner", "2026,2")
        assertNull(AcademicWidgetSnapshotCodec.decode(AcademicWidgetSnapshotCodec.encode(unknown)).timetable)
        val empty = unknown.copy(timetable = emptyList(), calendar = emptyList(), month = "2026-09")
        assertEquals(empty, AcademicWidgetSnapshotCodec.decode(AcademicWidgetSnapshotCodec.encode(empty)))
    }

    @Test fun accountChangesDiscardAllDataAndSemesterChangesDiscardOnlyTimetable() {
        val previous = AcademicWidgetSnapshot("account-a", "2026,1", emptyList(), 10,
            "2026-09", listOf(event("event", "2026-09-04", "2026-09-04")), 20)
        val semester = AcademicWidgetSnapshotPolicy.forIdentity(previous, "account-a", "2026,2")!!
        assertNull(semester.timetable)
        assertEquals(0L, semester.timetableFetchedAt)
        assertEquals(previous.calendar, semester.calendar)
        val account = AcademicWidgetSnapshotPolicy.forIdentity(previous, "account-b", "2026,1")!!
        assertNull(account.timetable)
        assertNull(account.calendar)
        assertNull(AcademicWidgetSnapshotPolicy.forIdentity(previous, "", ""))
    }

    private fun event(id: String, start: String, end: String) = CalendarEvent(id, id, "${start}T00:00", "${end}T23:59", "", "", "")
}
