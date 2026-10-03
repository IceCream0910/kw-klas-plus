package com.icecream.kwklasplus.core.notification

object DeadlineReminderMessage {
    fun create(items: List<ReminderDeadline>, now: Long, additional: Boolean): ReminderMessage {
        require(items.isNotEmpty())
        val groups = items.groupBy { it.subjectId to it.kind }.values.sortedWith(
            compareBy({ group -> group.minOf { it.dueAt } }, { group -> group.first().subjectName }, { group -> group.first().kind }),
        )
        val shown = groups.take(2)
        val summaries = shown.map { group ->
            val name = shortSubjectName(group.first().subjectName)
            val label = when (group.first().kind) {
                "onlineLecture" -> "온라인 강의"
                "teamTask" -> "팀프로젝트"
                else -> "과제"
            }
            "$name $label ${group.size}건"
        }
        val omitted = items.size - shown.sumOf { it.size }
        val summary = summaries.joinToString(", ") + if (omitted > 0) " 외 ${omitted}건" else ""
        val left = (items.minOf { it.dueAt } - now).coerceAtLeast(1)
        val remaining = if (left < 3_600_000) "약 ${(left + 59_999) / 60_000}분" else "약 ${left / 3_600_000}시간"
        val timing = if (items.size == 1) "$remaining 뒤 마감돼요." else "가장 빠른 마감은 $remaining 뒤예요."
        return ReminderMessage(
            if (additional) "곧 마감되는 할 일이 ${items.size}건 더 생겼어요" else "하루 안에 마감되는 할 일이 ${items.size}건 있어요",
            "${summary}이 있어요. $timing",
        )
    }

    private fun shortSubjectName(value: String): String {
        val clean = value.filterNot {
            it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' ||
                it == '\u061c' || it == '\u200e' || it == '\u200f'
        }.trim().ifEmpty { "과목" }
        if (clean.length <= 20) return clean
        val prefix = clean.take(19).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
        return "$prefix…"
    }
}
