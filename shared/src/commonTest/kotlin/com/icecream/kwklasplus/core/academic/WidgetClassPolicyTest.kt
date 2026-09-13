package com.icecream.kwklasplus.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetClassPolicyTest {
    private val lesson = TimetableEntry("자료구조", 0, "9:0", "10:0", "301", "A")

    @Test fun startEndAndProgressBoundaries() {
        fun at(minute: Int) = WidgetClassPolicy.present(listOf(lesson), 0, minute).single()
        assertEquals(WidgetClassPhase.UPCOMING, at(539).phase)
        assertEquals(1, at(539).minutesUntilStart)
        assertEquals(WidgetClassPhase.CURRENT, at(540).phase)
        assertEquals(0, at(540).progress)
        assertEquals(50, at(570).progress)
        assertEquals(WidgetClassPhase.FINISHED, at(600).phase)
        assertEquals(100, at(900).progress)
    }

    @Test fun currentThenUpcomingThenFinishedAndNoOtherDays() {
        val future = lesson.copy(startTime = "11:0", endTime = "12:0")
        val current = lesson.copy(startTime = "10:0", endTime = "11:0")
        assertEquals(listOf(current, future, lesson), WidgetClassPolicy.present(
            listOf(lesson, future, lesson.copy(day = 1), current), 0, 630).map { it.entry })
        assertEquals(emptyList(), WidgetClassPolicy.present(listOf(lesson), 6, 540))
    }

    @Test fun expandedEmptyAndFinishedTimetablesDoNotNeedMinuteRefresh() {
        fun needs(compact: Boolean, minute: Int = 570, day: Int = 0) =
            WidgetClassPolicy.needsMinuteRefresh(listOf(lesson), day, minute, compact)
        assertEquals(false, needs(false))
        assertEquals(true, needs(true))
        assertEquals(true, needs(true, 600))
        assertEquals(false, needs(true, 601))
        assertEquals(false, needs(true, day = 6))
        assertEquals(false, WidgetClassPolicy.needsMinuteRefresh(emptyList(), 0, 570, true))
    }

    @Test fun colorUsesNormalizedTitleAcrossSections() {
        assertEquals(WidgetClassPolicy.colorIndex("자료 구조"), WidgetClassPolicy.colorIndex(" 자료   구조 "))
        assertEquals(WidgetClassPolicy.colorIndex(lesson.title), WidgetClassPolicy.colorIndex(lesson.copy(subj = "B").title))
    }
}
