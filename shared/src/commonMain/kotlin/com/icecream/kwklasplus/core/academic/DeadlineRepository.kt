package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

fun interface DeadlineDateParser {
    fun parseEpochMillis(value: String): Long?
}

@Serializable
data class DeadlineItem(
    val startDate: String,
    val endDate: String,
    val hourGap: Int,
)

@Serializable
data class SubjectDeadlines(
    val name: String,
    val subj: String,
    val onlineLecture: List<DeadlineItem>,
    val task: List<DeadlineItem>,
    val teamTask: List<DeadlineItem>,
)

sealed interface DeadlinesResult {
    data class Success(val subjects: List<SubjectDeadlines>) : DeadlinesResult
    data object SessionExpired : DeadlinesResult
    data object Timeout : DeadlinesResult
    data object NetworkFailure : DeadlinesResult
    data class HttpFailure(val statusCode: Int) : DeadlinesResult
    data object EmptyResponse : DeadlinesResult
    data object MalformedResponse : DeadlinesResult
}

class DeadlinesWebCodec(
    private val json: Json = Json,
) {
    fun encode(subjects: List<SubjectDeadlines>): String = json.encodeToString(subjects)
}

class DeadlineRepository(
    private val transport: KlasAuthenticatedTransport,
    private val clock: Clock,
    private val onlineLectureEndParser: DeadlineDateParser,
    private val assignmentEndParser: DeadlineDateParser,
) {
    suspend fun fetch(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearSemester: String,
        subjects: List<AcademicSubject>,
    ): DeadlinesResult = coroutineScope {
        if (yearSemester.isBlank()) return@coroutineScope DeadlinesResult.MalformedResponse
        val results = subjects.map { subject ->
            async { fetchSubject(session, userAgent, yearSemester, subject) }
        }.awaitAll()
        val failure = results.firstOrNull { it !is SubjectDeadlineResult.Success }
        if (failure != null) failure.toPublicResult()
        else DeadlinesResult.Success(results.map { (it as SubjectDeadlineResult.Success).deadlines })
    }

    private suspend fun fetchSubject(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearSemester: String,
        subject: AcademicSubject,
    ): SubjectDeadlineResult {
        val requestBody = buildJsonObject {
            put("selectChangeYn", "Y")
            put("selectYearhakgi", yearSemester)
            put("selectSubj", subject.id)
        }
        val online = fetchArray(
            AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES,
            session,
            userAgent,
            requestBody,
        )
        if (online !is ArrayResult.Success) return online.toSubjectResult()
        val tasks = fetchArray(
            AuthenticatedKlasEndpoint.TASK_DEADLINES,
            session,
            userAgent,
            requestBody,
        )
        if (tasks !is ArrayResult.Success) return tasks.toSubjectResult()
        val teamTasks = fetchArray(
            AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES,
            session,
            userAgent,
            requestBody,
        )
        if (teamTasks !is ArrayResult.Success) return teamTasks.toSubjectResult()

        return try {
            SubjectDeadlineResult.Success(
                SubjectDeadlines(
                    name = subject.name,
                    subj = subject.id,
                    onlineLecture = parseOnlineLectures(online.body),
                    task = parseAssignments(tasks.body),
                    teamTask = parseAssignments(teamTasks.body),
                ),
            )
        } catch (_: MalformedDeadlineException) {
            SubjectDeadlineResult.MalformedResponse
        }
    }

    private suspend fun fetchArray(
        endpoint: AuthenticatedKlasEndpoint,
        session: SecretValue,
        userAgent: KlasUserAgent,
        body: JsonObject,
    ): ArrayResult = when (val result = transport.postJson(endpoint, session, userAgent, body)) {
        is KlasAuthenticatedResult.Success -> (result.body as? JsonArray)
            ?.let(ArrayResult::Success)
            ?: ArrayResult.MalformedResponse
        KlasAuthenticatedResult.SessionExpired -> ArrayResult.SessionExpired
        KlasAuthenticatedResult.Timeout -> ArrayResult.Timeout
        KlasAuthenticatedResult.NetworkFailure -> ArrayResult.NetworkFailure
        is KlasAuthenticatedResult.HttpFailure -> ArrayResult.HttpFailure(result.statusCode)
        KlasAuthenticatedResult.EmptyResponse -> ArrayResult.EmptyResponse
        KlasAuthenticatedResult.MalformedResponse -> ArrayResult.MalformedResponse
    }

    private fun parseOnlineLectures(rows: JsonArray): List<DeadlineItem> = rows.mapNotNull { element ->
        val row = element as? JsonObject ?: throw MalformedDeadlineException()
        if (row.string("evltnSe") != "lesson") return@mapNotNull null
        if ((row.int("prog") ?: throw MalformedDeadlineException()) >= 100) return@mapNotNull null
        val startDate = row.string("startDate") ?: throw MalformedDeadlineException()
        val endDate = row.string("endDate") ?: throw MalformedDeadlineException()
        deadlineItem(startDate, endDate, onlineLectureEndParser.parseEpochMillis("$endDate:59"))
    }

    private fun parseAssignments(rows: JsonArray): List<DeadlineItem> = rows.mapNotNull { element ->
        val row = element as? JsonObject ?: throw MalformedDeadlineException()
        if (row.string("submityn") == "Y") return@mapNotNull null
        val startDate = row.string("startdate") ?: throw MalformedDeadlineException()
        val endDate = row.string("expiredate") ?: throw MalformedDeadlineException()
        deadlineItem(startDate, endDate, assignmentEndParser.parseEpochMillis(endDate))
    }

    private fun deadlineItem(startDate: String, endDate: String, endEpochMillis: Long?): DeadlineItem? {
        val end = endEpochMillis ?: throw MalformedDeadlineException()
        val hourGap = ((end - clock.nowEpochMillis()) / MILLIS_PER_HOUR).toInt()
        return if (hourGap >= 0) DeadlineItem(startDate, endDate, hourGap) else null
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.intOrNull

    private sealed interface ArrayResult {
        data class Success(val body: JsonArray) : ArrayResult
        data object SessionExpired : ArrayResult
        data object Timeout : ArrayResult
        data object NetworkFailure : ArrayResult
        data class HttpFailure(val statusCode: Int) : ArrayResult
        data object EmptyResponse : ArrayResult
        data object MalformedResponse : ArrayResult
    }

    private sealed interface SubjectDeadlineResult {
        data class Success(val deadlines: SubjectDeadlines) : SubjectDeadlineResult
        data object SessionExpired : SubjectDeadlineResult
        data object Timeout : SubjectDeadlineResult
        data object NetworkFailure : SubjectDeadlineResult
        data class HttpFailure(val statusCode: Int) : SubjectDeadlineResult
        data object EmptyResponse : SubjectDeadlineResult
        data object MalformedResponse : SubjectDeadlineResult
    }

    private fun ArrayResult.toSubjectResult(): SubjectDeadlineResult = when (this) {
        is ArrayResult.Success -> SubjectDeadlineResult.MalformedResponse
        ArrayResult.SessionExpired -> SubjectDeadlineResult.SessionExpired
        ArrayResult.Timeout -> SubjectDeadlineResult.Timeout
        ArrayResult.NetworkFailure -> SubjectDeadlineResult.NetworkFailure
        is ArrayResult.HttpFailure -> SubjectDeadlineResult.HttpFailure(statusCode)
        ArrayResult.EmptyResponse -> SubjectDeadlineResult.EmptyResponse
        ArrayResult.MalformedResponse -> SubjectDeadlineResult.MalformedResponse
    }

    private fun SubjectDeadlineResult.toPublicResult(): DeadlinesResult = when (this) {
        is SubjectDeadlineResult.Success -> DeadlinesResult.Success(listOf(deadlines))
        SubjectDeadlineResult.SessionExpired -> DeadlinesResult.SessionExpired
        SubjectDeadlineResult.Timeout -> DeadlinesResult.Timeout
        SubjectDeadlineResult.NetworkFailure -> DeadlinesResult.NetworkFailure
        is SubjectDeadlineResult.HttpFailure -> DeadlinesResult.HttpFailure(statusCode)
        SubjectDeadlineResult.EmptyResponse -> DeadlinesResult.EmptyResponse
        SubjectDeadlineResult.MalformedResponse -> DeadlinesResult.MalformedResponse
    }

    private class MalformedDeadlineException : Exception()

    companion object {
        private const val MILLIS_PER_HOUR = 3_600_000L
    }
}
