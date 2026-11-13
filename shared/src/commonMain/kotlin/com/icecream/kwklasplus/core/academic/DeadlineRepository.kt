package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.notification.DeadlineNotificationProjection
import com.icecream.kwklasplus.core.notification.ReminderDeadline
import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.search.SearchBatch
import com.icecream.kwklasplus.core.search.SearchProjection
import com.icecream.kwklasplus.core.diagnostics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    data class Success(val subjects: List<SubjectDeadlines>, val reminders: List<ReminderDeadline>? = null, val startedAt: Long = 0, val fetchedAt: Long = 0) : DeadlinesResult
    data class PartialSuccess(val subjects: List<SubjectDeadlines>) : DeadlinesResult
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

    fun encodePartial(subjects: List<SubjectDeadlines>): String =
        if (subjects.any { it.onlineLecture.isNotEmpty() || it.task.isNotEmpty() || it.teamTask.isNotEmpty() }) {
            encode(subjects)
        } else ""
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
        onSearchBatch: (SearchBatch) -> Unit = {},
    ): DeadlinesResult = coroutineScope {
        val startedAt = clock.nowEpochMillis()
        if (yearSemester.isBlank()) return@coroutineScope DeadlinesResult.MalformedResponse
        val elapsed = TimeSource.Monotonic.markNow()
        val permits = Semaphore(4)
        val results = subjects.map { subject ->
            async { permits.withPermit { fetchSubject(session, userAgent, yearSemester, subject, onSearchBatch) } }
        }.awaitAll()
        val successes = results.filterIsInstance<SubjectDeadlineResult.Success>()
        val failure = results.firstOrNull { it == SubjectDeadlineResult.SessionExpired }
            ?: results.firstOrNull { it !is SubjectDeadlineResult.Success }
        if (failure != null) {
            val partial = failure != SubjectDeadlineResult.SessionExpired && successes.isNotEmpty()
            NativeDiagnostics.report(
                DiagnosticStage.DEADLINE_REFRESH,
                if (partial) DiagnosticFailure.PARTIAL else failure.diagnosticFailure(),
                elapsedMs = elapsed.elapsedNow().inWholeMilliseconds,
                successCount = successes.size,
                failureCount = results.size - successes.size,
            )
            if (partial) DeadlinesResult.PartialSuccess(successes.map { it.deadlines })
            else failure.toPublicResult()
        } else {
            val reminders = if (successes.all { it.reminders != null }) successes.flatMap { it.reminders.orEmpty() } else null
            DeadlinesResult.Success(successes.map { it.deadlines }, reminders?.takeIf { it.map(ReminderDeadline::key).distinct().size == it.size }, startedAt, clock.nowEpochMillis())
        }
    }

    private suspend fun fetchSubject(
        session: SecretValue,
        userAgent: KlasUserAgent,
        yearSemester: String,
        subject: AcademicSubject,
        onSearchBatch: (SearchBatch) -> Unit,
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
        publishSearch(subject, "onlineLecture", online, onSearchBatch)
        if (online !is ArrayResult.Success) return online.toSubjectResult()
        val tasks = fetchArray(
            AuthenticatedKlasEndpoint.TASK_DEADLINES,
            session,
            userAgent,
            requestBody,
        )
        publishSearch(subject, "task", tasks, onSearchBatch)
        if (tasks !is ArrayResult.Success) return tasks.toSubjectResult()
        val teamTasks = fetchArray(
            AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES,
            session,
            userAgent,
            requestBody,
        )
        publishSearch(subject, "teamTask", teamTasks, onSearchBatch)
        if (teamTasks !is ArrayResult.Success) return teamTasks.toSubjectResult()

        val onlineItems = parseItems(AuthenticatedKlasEndpoint.ONLINE_LECTURE_DEADLINES, online.body, ::parseOnlineLectures)
            ?: return SubjectDeadlineResult.MalformedResponse
        val tasksItems = parseItems(AuthenticatedKlasEndpoint.TASK_DEADLINES, tasks.body, ::parseAssignments)
            ?: return SubjectDeadlineResult.MalformedResponse
        val teamTasksItems = parseItems(AuthenticatedKlasEndpoint.TEAM_TASK_DEADLINES, teamTasks.body, ::parseAssignments)
            ?: return SubjectDeadlineResult.MalformedResponse
        val projections = listOf(
            DeadlineNotificationProjection.parse(yearSemester, subject.id, subject.name, "onlineLecture", online.body),
            DeadlineNotificationProjection.parse(yearSemester, subject.id, subject.name, "task", tasks.body),
            DeadlineNotificationProjection.parse(yearSemester, subject.id, subject.name, "teamTask", teamTasks.body),
        )
        return SubjectDeadlineResult.Success(
            SubjectDeadlines(subject.name, subject.id, onlineItems, tasksItems, teamTasksItems),
            if (projections.all { it != null }) projections.flatMap { it.orEmpty() } else null,
        )
    }

    private fun parseItems(
        endpoint: AuthenticatedKlasEndpoint,
        rows: JsonArray,
        parse: (JsonArray) -> List<DeadlineItem>,
    ): List<DeadlineItem>? = try {
        parse(rows)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        NativeDiagnostics.report(
            if (cause is InvalidDeadlineDateException) DiagnosticStage.DEADLINE_DATE else DiagnosticStage.DEADLINE_PAYLOAD,
            DiagnosticFailure.MALFORMED,
            endpoint,
        )
        null
    }

    private fun publishSearch(subject: AcademicSubject, kind: String, result: ArrayResult, publish: (SearchBatch) -> Unit) {
        try {
            val now = clock.nowEpochMillis()
            publish(if (result is ArrayResult.Success) SearchProjection.parse(subject.id, subject.name, kind, result.body, now)
            else SearchBatch(subject.id, subject.name, kind, if (result == ArrayResult.SessionExpired) "loginRequired" else "failed", now, emptyList()))
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
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
            ?: ArrayResult.MalformedResponse.also {
                NativeDiagnostics.report(DiagnosticStage.DEADLINE_PAYLOAD, DiagnosticFailure.MALFORMED, endpoint)
            }
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
        if ((row.number("prog") ?: throw MalformedDeadlineException()) >= 100.0) return@mapNotNull null
        val startDate = row.string("startDate") ?: throw MalformedDeadlineException()
        val endDate = row.string("endDate") ?: throw MalformedDeadlineException()
        deadlineItem(startDate, endDate, parseDate(onlineLectureEndParser, "$endDate:59"))
    }

    private fun parseAssignments(rows: JsonArray): List<DeadlineItem> = rows.mapNotNull { element ->
        val row = element as? JsonObject ?: throw MalformedDeadlineException()
        if (row.string("submityn") == "Y") return@mapNotNull null
        val startDate = row.string("startdate") ?: throw MalformedDeadlineException()
        val endDate = row.string("expiredate") ?: throw MalformedDeadlineException()
        deadlineItem(startDate, endDate, parseDate(assignmentEndParser, endDate))
    }

    private fun parseDate(parser: DeadlineDateParser, value: String): Long = try {
        parser.parseEpochMillis(value) ?: throw InvalidDeadlineDateException()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        throw InvalidDeadlineDateException()
    }

    private fun deadlineItem(startDate: String, endDate: String, endEpochMillis: Long): DeadlineItem? {
        val hourGap = ((endEpochMillis - clock.nowEpochMillis()) / MILLIS_PER_HOUR).toInt()
        return if (hourGap >= 0) DeadlineItem(startDate, endDate, hourGap) else null
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

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
        data class Success(val deadlines: SubjectDeadlines, val reminders: List<ReminderDeadline>?) : SubjectDeadlineResult
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

    private fun SubjectDeadlineResult.diagnosticFailure(): DiagnosticFailure = when (this) {
        SubjectDeadlineResult.SessionExpired -> DiagnosticFailure.SESSION_EXPIRED
        SubjectDeadlineResult.Timeout -> DiagnosticFailure.TIMEOUT
        SubjectDeadlineResult.NetworkFailure -> DiagnosticFailure.NETWORK
        is SubjectDeadlineResult.HttpFailure -> DiagnosticFailure.HTTP
        SubjectDeadlineResult.EmptyResponse -> DiagnosticFailure.EMPTY
        SubjectDeadlineResult.MalformedResponse -> DiagnosticFailure.MALFORMED
        is SubjectDeadlineResult.Success -> error("Expected deadline failure")
    }

    private class InvalidDeadlineDateException : Exception()
    private class MalformedDeadlineException : Exception()

    companion object {
        private const val MILLIS_PER_HOUR = 3_600_000L
    }
}
