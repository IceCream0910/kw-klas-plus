package com.icecream.kwklasplus.core.notification

import kotlinx.serialization.json.*

object DeadlineNotificationProjection {
    fun parse(term: String, subjectId: String, subjectName: String, kind: String, rows: JsonArray): List<ReminderDeadline>? {
        val items = mutableListOf<ReminderDeadline>()
        for (element in rows) {
            val row = element as? JsonObject ?: return null
            if (kind == "onlineLecture") {
                if (row.text("evltnSe") != "lesson") continue
                val progress = row.text("prog")?.toIntOrNull() ?: return null
                if (progress >= 100) continue
            } else when (row.text("submityn")) { "Y" -> continue; "N" -> Unit; else -> return null }
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
            val start = row.text(if (kind == "onlineLecture") "startDate" else "startdate") ?: return null
            var end = row.text(if (kind == "onlineLecture") "endDate" else "expiredate") ?: return null
            if (kind == "onlineLecture" && Regex("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}").matches(end)) end += ":59"
            val startsAt = if (start.isEmpty()) null else ReminderTime.parse(start) ?: return null
            val dueAt = ReminderTime.parse(end) ?: return null
            items += ReminderDeadline("$term/$subjectId/$kind/$id", startsAt, dueAt, subjectId, subjectName, kind)
        }
        return items.takeIf { it.map(ReminderDeadline::key).distinct().size == it.size }
    }
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
}
