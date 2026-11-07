package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.academic.*
import io.ktor.util.date.GMTDate

object DemoWidgetFixtures {
    fun snapshot(owner: String, now: Long, localDate: String? = null): AcademicWidgetSnapshot {
        val date = GMTDate(now)
        val month = localDate?.take(7) ?: "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}"
        val day = localDate ?: "$month-${date.dayOfMonth.toString().padStart(2, '0')}"
        return AcademicWidgetSnapshot(owner, DemoFixtures.term,
            timetable = listOf(
                TimetableEntry("컴퓨터프로그래밍", 0, "09:00", "10:30", "새빛관 101", "DEMO001"),
                TimetableEntry("자료구조", 1, "10:30", "12:00", "새빛관 202", "DEMO002"),
                TimetableEntry("대학영어", 2, "13:00", "14:30", "한울관 301", "DEMO003"),
            ), timetableFetchedAt = now, month = month,
            calendar = listOf(CalendarEvent("demo-study", "데모 학습 계획", day+"T15:00:00", day+"T16:00:00", "개인일정", "#336699", "새빛관")),
            calendarFetchedAt = now, calendarStatus = WidgetSyncStatus.READY,
        )
    }
}
