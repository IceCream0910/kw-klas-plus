package com.icecream.kwklasplus.core.academic

data class CalendarAgenda(val date: String, val todayCount: Int, val events: List<CalendarEvent>)
data class CalendarBar(val event: CalendarEvent, val week: Int, val column: Int, val span: Int, val lane: Int)

object AcademicWidgetPolicy {
    fun weekdays(entries: List<TimetableEntry>) = entries.filter { it.day in 0..4 }

    fun weekends(entries: List<TimetableEntry>) = entries.filter { it.day in 5..6 }
        .sortedWith(compareBy({ it.day }, { minutes(it.startTime) }, { it.title }))

    fun minutes(time: String): Int {
        val parts = time.split(':')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun todayClasses(entries: List<TimetableEntry>, mondayBasedDay: Int): List<TimetableEntry> =
        entries.filter { it.day == mondayBasedDay }.sortedBy { minutes(it.startTime) }

    fun agenda(events: List<CalendarEvent>, today: String): CalendarAgenda {
        val month = today.take(7)
        val monthEvents = events.filter { it.start.take(7) <= month && it.end.take(7) >= month }
        fun on(date: String) = monthEvents.filter { it.start.take(10) <= date && it.end.take(10) >= date }
            .sortedWith(compareBy({ it.start }, { it.title }))
        val current = on(today)
        if (current.isNotEmpty()) return CalendarAgenda(today, current.size, current)
        val upcoming = monthEvents.filter { it.start.take(10) > today }.minOfOrNull { it.start.take(10) }
        val previous = monthEvents.filter { it.end.take(10) < today }.maxOfOrNull { it.end.take(10) }
        val date = upcoming ?: previous ?: today
        return CalendarAgenda(date, 0, on(date))
    }

    fun monthBars(events: List<CalendarEvent>, month: String, daysInMonth: Int, sundayOffset: Int): List<CalendarBar> {
        val first = "$month-01"
        val last = "$month-${daysInMonth.toString().padStart(2, '0')}"
        val bars = mutableListOf<CalendarBar>()
        val occupied = mutableMapOf<Pair<Int, Int>, Int>()
        events.filter { it.start.take(10) <= last && it.end.take(10) >= first }
            .sortedWith(compareBy<CalendarEvent>({ it.start }, { it.end }, { it.id }))
            .forEach { event ->
                val from = maxOf(first, event.start.take(10)).takeLast(2).toInt() - 1 + sundayOffset
                val to = minOf(last, event.end.take(10)).takeLast(2).toInt() - 1 + sundayOffset
                for (week in from / 7..to / 7) {
                    val column = maxOf(from, week * 7) % 7
                    val endColumn = minOf(to, week * 7 + 6) % 7
                    val mask = ((1 shl (endColumn - column + 1)) - 1) shl column
                    var lane = 0
                    while ((occupied[week to lane] ?: 0) and mask != 0) lane++
                    occupied[week to lane] = (occupied[week to lane] ?: 0) or mask
                    bars += CalendarBar(event, week, column, endColumn - column + 1, lane)
                }
            }
        return bars
    }
}
