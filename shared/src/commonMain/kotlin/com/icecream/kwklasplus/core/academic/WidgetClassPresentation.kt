package com.icecream.kwklasplus.core.academic

enum class WidgetClassPhase { CURRENT, UPCOMING, FINISHED }

data class WidgetClassPresentation(
    val entry: TimetableEntry,
    val phase: WidgetClassPhase,
    val progress: Int,
    val minutesUntilStart: Int,
)

object WidgetClassPolicy {
    fun present(entries: List<TimetableEntry>, day: Int, minute: Int): List<WidgetClassPresentation> =
        AcademicWidgetPolicy.todayClasses(entries, day).map { entry ->
            val start = AcademicWidgetPolicy.minutes(entry.startTime)
            val end = AcademicWidgetPolicy.minutes(entry.endTime)
            val phase = when {
                minute < start -> WidgetClassPhase.UPCOMING
                minute >= end -> WidgetClassPhase.FINISHED
                else -> WidgetClassPhase.CURRENT
            }
            WidgetClassPresentation(entry, phase,
                ((minute - start) * 100 / (end - start).coerceAtLeast(1)).coerceIn(0, 100),
                (start - minute).coerceAtLeast(0))
        }.sortedBy { when (it.phase) {
            WidgetClassPhase.CURRENT -> 0
            WidgetClassPhase.UPCOMING -> 1
            WidgetClassPhase.FINISHED -> 2
        } }

    fun needsMinuteRefresh(entries: List<TimetableEntry>, day: Int, minute: Int, hasCompactWidget: Boolean): Boolean =
        hasCompactWidget && AcademicWidgetPolicy.todayClasses(entries, day)
            .any { AcademicWidgetPolicy.minutes(it.endTime) >= minute }

    fun colorIndex(title: String): Int =
        TimetableColorPolicy.slot(title)
}
