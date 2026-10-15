package com.icecream.kwklasplus.core.search

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val searchJson = Json { encodeDefaults = true }

@Serializable
data class SearchItem(
    val sourceId: String,
    val title: String,
    val startsAt: String?,
    val endsAt: String?,
    val completionState: String,
)

@Serializable
data class SearchBatch(
    val courseId: String,
    val courseName: String,
    val kind: String,
    val status: String,
    val fetchedAt: Long,
    val items: List<SearchItem>,
)

@Serializable
data class SearchSnapshot(
    val accountScope: String,
    val term: String,
    val generation: Long,
    val batches: List<SearchBatch>,
    val schemaVersion: Int = 1,
) {
    fun encode(): String = searchJson.encodeToString(this)
}

object SearchProjection {
    fun parse(courseId: String, courseName: String, kind: String, rows: JsonArray, fetchedAt: Long): SearchBatch {
        var partial = false
        val items = rows.mapNotNull { element ->
            val row = element as? JsonObject ?: run { partial = true; return@mapNotNull null }
            fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull
            if (kind == "onlineLecture" && text("evltnSe") != "lesson") return@mapNotNull null
            val nativeId = when (kind) {
                "task" -> text("taskNo")
                "teamTask" -> text("prjctNo")
                "onlineLecture" -> text("lesson")?.let { lesson -> text("oid")?.let { "$lesson/$it" } }
                else -> null
            }
            val title = listOf("title", "sbjt", "taskTitle", "prjctTitle", "lessonTitle", "cntntsNm").firstNotNullOfOrNull { text(it)?.takeIf(String::isNotBlank) }
            if (title.isNullOrBlank()) {
                partial = true
                return@mapNotNull null
            }
            val start = text(if (kind == "onlineLecture") "startDate" else "startdate") ?: text("sdates")
            val end = text(if (kind == "onlineLecture") "endDate" else "expiredate") ?: text("edates")
            // 원본 ID 없는 목록 항목도 기존 종류별 목록 이동으로 열 수 있다.
            val id = nativeId?.takeIf { it.isNotBlank() && it.length <= 256 }
                ?: "metadata:${title.hashCode()}:${start.orEmpty()}:${end.orEmpty()}".take(256)
            val completion = if (kind == "onlineLecture") {
                text("prog")?.toDoubleOrNull()?.let { if (it >= 100) "complete" else "incomplete" } ?: "unknown"
            } else when (text("submityn") ?: text("submit")) { "Y" -> "complete"; "N" -> "incomplete"; else -> "unknown" }
            SearchItem(id, title.take(512), start?.take(40), end?.take(40), completion)
        }
        val unique = items.distinctBy { it.sourceId }
        return SearchBatch(courseId, courseName.take(256), kind, if (partial || unique.size != items.size) "partial" else "success", fetchedAt, unique)
    }
}
