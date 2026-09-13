package com.icecream.kwklasplus.core.academic

class KlasCalendarEventNormalizer : CalendarEventNormalizer {
    override fun normalize(event: CalendarEvent): CalendarEvent? {
        var end = Moment.parse(event.end) ?: return null
        var start = if (event.kind == "과제") end else Moment.parse(event.start) ?: return null
        if (event.kind == "학사일정" && (end.minutes - start.minutes) / 1440 > 365) {
            start = start.inYear(end.year)
            if (start.minutes > end.minutes) start = start.inYear(end.year - 1)
        }
        if (event.kind == "과제") {
            start = end.copy(hour = 0, minute = 0)
            end = end.copy(hour = 23, minute = 59)
        } else {
            if (start.hour == 23 && start.minute == 59) start = start.copy(hour = 0, minute = 0)
            if (end.hour == 0 && end.minute == 0) end = end.copy(hour = 23, minute = 59)
        }
        return if (end.minutes < start.minutes) null else event.copy(start = start.iso, end = end.iso)
    }

    private data class Moment(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
        val iso: String get() = "${year.toString().padStart(4, '0')}-${two(month)}-${two(day)}T${two(hour)}:${two(minute)}"
        val minutes: Long get() {
            val previous = year - 1L
            val days = previous * 365 + previous / 4 - previous / 100 + previous / 400 +
                (1 until month).sumOf { monthDays(year, it) } + day - 1
            return days * 1440 + hour * 60 + minute
        }
        fun inYear(value: Int) = copy(year = value, day = day.coerceAtMost(monthDays(value, month)))

        companion object {
            fun parse(value: String): Moment? {
                val input = value.trim()
                if (!Regex("(?:[0-9]{8}(?:[0-9]{2}){0,3}|[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[ T][0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)").matches(input)) return null
                val digits = input.filter { it in '0'..'9' }.padEnd(12, '0')
                val year = digits.substring(0, 4).toInt()
                val month = digits.substring(4, 6).toInt()
                val day = digits.substring(6, 8).toInt()
                val hour = digits.substring(8, 10).toInt()
                val minute = digits.substring(10, 12).toInt()
                if (year < 1 || month !in 1..12 || day !in 1..monthDays(year, month) || hour !in 0..23 || minute !in 0..59) return null
                return Moment(year, month, day, hour, minute)
            }
            private fun two(value: Int) = value.toString().padStart(2, '0')
            private fun monthDays(year: Int, month: Int) = when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        }
    }
}
