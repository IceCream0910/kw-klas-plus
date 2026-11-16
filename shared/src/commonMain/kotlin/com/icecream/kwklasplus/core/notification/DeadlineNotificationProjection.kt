package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.academic.deadlineLectureProgress
import com.icecream.kwklasplus.core.academic.normalizeDeadlineDate
import kotlinx.serialization.json.*

internal object DeadlineNotificationProjection {
    data class Result(val items: List<ReminderDeadline>, val isComplete: Boolean)

    fun parse(term: String, subjectId: String, subjectName: String, kind: String, rows: JsonArray): Result {
        var isComplete = true
        val items = rows.mapNotNull { element ->
            val row = element as? JsonObject
            if (row != null && isCompleted(row, kind)) return@mapNotNull null
            val item = row?.let { parsePending(term, subjectId, subjectName, kind, it) }
            if (item == null) isComplete = false
            item
        }
        return Result(items, isComplete && items.distinctBy(ReminderDeadline::key).size == items.size)
    }

    private fun isCompleted(row: JsonObject, kind: String): Boolean = when (kind) {
        "onlineLecture" -> when (row.text("evltnSe")) {
            null -> false
            "lesson" -> (row.deadlineLectureProgress() ?: 0.0) >= 100.0
            else -> true
        }
        else -> row.text("submityn") == "Y"
    }

    private fun parsePending(term: String, subjectId: String, subjectName: String, kind: String, row: JsonObject): ReminderDeadline? {
        val online = kind == "onlineLecture"
        if (online) {
            if (row.text("evltnSe") != "lesson" || row.deadlineLectureProgress() == null) return null
        } else if (row.text("submityn") != "N") return null
        val id = when (kind) {
            "onlineLecture" -> {
                val lesson = row.text("lesson")?.takeIf { it.isNotBlank() } ?: return null
                val oid = row.text("oid")?.takeIf { it.isNotBlank() } ?: return null
                "$lesson/$oid"
            }
            "task" -> row.text("taskNo")
            "teamTask" -> row.text("prjctNo")
            else -> return null
        }?.takeIf { it.isNotBlank() && it.length <= 256 } ?: return null
        val start = row.text(if (online) "startDate" else "startdate") ?: return null
        val end = row.text(if (online) "endDate" else "expiredate") ?: return null
        val startsAt = if (start.isBlank()) null else ReminderTime.parse(normalizeDeadlineDate(start)) ?: return null
        val dueAt = ReminderTime.parse(normalizeDeadlineDate(end, if (online) "59" else "00")) ?: return null
        return ReminderDeadline("$term/$subjectId/$kind/$id", startsAt, dueAt, subjectId, subjectName, kind)
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
}
