package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
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

@Serializable
data class TimetableEntry(
    val title: String,
    val day: Int,
    val startTime: String,
    val endTime: String,
    val info: String,
    val subj: String,
)

sealed interface TimetableResult {
    data class Success(val entriesBySubject: Map<String, List<TimetableEntry>>) : TimetableResult
    data object SessionExpired : TimetableResult
    data object Timeout : TimetableResult
    data object NetworkFailure : TimetableResult
    data class HttpFailure(val statusCode: Int) : TimetableResult
    data object EmptyResponse : TimetableResult
    data object MalformedResponse : TimetableResult
}

class TimetableWebCodec(
    private val json: Json = Json,
) {
    fun encode(entriesBySubject: Map<String, List<TimetableEntry>>): String =
        json.encodeToString(entriesBySubject)
}

class TimetableRepository(
    private val transport: KlasAuthenticatedTransport,
) {
    suspend fun fetch(
        session: SecretValue,
        userAgent: KlasUserAgent,
        year: String,
        semester: String,
    ): TimetableResult {
        if (year.isBlank() || semester.isBlank()) return TimetableResult.MalformedResponse
        val requestBody = buildJsonObject {
            put("list", JsonArray(emptyList()))
            put("searchYear", year)
            put("searchHakgi", semester)
            put("atnlcYearList", JsonArray(emptyList()))
            put("timeTableList", JsonArray(emptyList()))
        }
        return when (
            val result = transport.postJson(
                AuthenticatedKlasEndpoint.TIMETABLE,
                session,
                userAgent,
                requestBody,
            )
        ) {
            is KlasAuthenticatedResult.Success -> mapRows(result.body as? JsonArray)
            KlasAuthenticatedResult.SessionExpired -> TimetableResult.SessionExpired
            KlasAuthenticatedResult.Timeout -> TimetableResult.Timeout
            KlasAuthenticatedResult.NetworkFailure -> TimetableResult.NetworkFailure
            is KlasAuthenticatedResult.HttpFailure -> TimetableResult.HttpFailure(result.statusCode)
            KlasAuthenticatedResult.EmptyResponse -> TimetableResult.EmptyResponse
            KlasAuthenticatedResult.MalformedResponse -> TimetableResult.MalformedResponse
        }
    }

    private fun mapRows(rows: JsonArray?): TimetableResult {
        rows ?: return TimetableResult.MalformedResponse
        val entries = linkedMapOf<String, MutableList<TimetableEntry>>()
        for (element in rows) {
            val row = element as? JsonObject ?: return TimetableResult.MalformedResponse
            if (row.string("wtHasSchedule") == "N") continue
            val period = row.int("wtTime") ?: return TimetableResult.MalformedResponse
            for (day in 1..6) {
                val subjectId = row.string("wtSubj_$day") ?: continue
                val title = row.string("wtSubjNm_$day") ?: continue
                val place = row.string("wtLocHname_$day") ?: continue
                val professor = row.string("wtProfNm_$day").orEmpty()
                val span = row.int("wtSpan_$day") ?: 1
                val start = AcademicPeriodTimePolicy.start(period)
                val end = AcademicPeriodTimePolicy.end(period + span - 1)
                entries.getOrPut(subjectId, ::mutableListOf) += TimetableEntry(
                    title = title,
                    day = day - 1,
                    startTime = start,
                    endTime = end,
                    info = "$place/$professor",
                    subj = subjectId,
                )
            }
        }
        return TimetableResult.Success(entries.mapValues { it.value.toList() })
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.intOrNull
}

object AcademicPeriodTimePolicy {
    fun start(index: Int): String = when (index) {
        0 -> "8:0"
        1 -> "9:0"
        2 -> "10:30"
        3 -> "12:0"
        4 -> "13:30"
        5 -> "15:0"
        6 -> "16:30"
        7 -> "18:0"
        8 -> "18:50"
        9 -> "19:40"
        10 -> "20:30"
        11 -> "21:30"
        else -> "0:0"
    }

    fun end(index: Int): String = when (index) {
        0 -> "8:50"
        1 -> "10:15"
        2 -> "11:45"
        3 -> "13:15"
        4 -> "14:45"
        5 -> "16:15"
        6 -> "17:45"
        7 -> "18:45"
        8 -> "19:35"
        9 -> "20:25"
        10 -> "21:15"
        11 -> "22:5"
        else -> "0:0"
    }
}
