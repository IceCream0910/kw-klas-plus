package com.icecream.kwklasplus.core.search

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable
data class SearchAgentReference(
    val kind: String,
    val title: String,
    val courseId: String = "",
    val courseName: String = "",
    val boardNo: String = "",
    val masterNo: String = "",
    val sourceId: String = "",
)

@Serializable
data class SearchAgentContext(val question: String, val term: String, val references: List<SearchAgentReference>)

object SearchAgentHandoff {
    private val mutex = Mutex()
    private var pending: Triple<String, String, TimeMark>? = null

    @OptIn(ExperimentalUuidApi::class)
    suspend fun offer(raw: String): String? = mutex.withLock {
        val context = decode(raw) ?: return@withLock null
        val id = Uuid.random().toString()
        pending = Triple(id, Json.encodeToString(context), TimeSource.Monotonic.markNow())
        id
    }

    suspend fun take(id: String): String = mutex.withLock {
        val entry = pending ?: return@withLock ""
        if (entry.third.elapsedNow().inWholeSeconds >= 60) { pending = null; return@withLock "" }
        if (entry.first != id) return@withLock ""
        pending = null
        entry.second
    }

    suspend fun clear() = mutex.withLock { pending = null }

    fun decode(raw: String): SearchAgentContext? {
        if (raw.length > 8192 || raw.encodeToByteArray().size > 16384) return null
        val context = runCatching { Json.decodeFromString<SearchAgentContext>(raw) }.getOrNull() ?: return null
        if (context.question.isBlank() || context.question.length > 200 || context.term.length > 32 || context.references.size > 5) return null
        if (context.references.any { it.kind !in setOf("task", "teamTask", "onlineLecture", "notice", "material") ||
                it.title.length > 512 || it.courseId.length > 128 || it.courseName.length > 256 ||
                it.boardNo.length > 20 || it.masterNo.length > 64 || it.sourceId.length > 256 }) return null
        return context
    }
}
