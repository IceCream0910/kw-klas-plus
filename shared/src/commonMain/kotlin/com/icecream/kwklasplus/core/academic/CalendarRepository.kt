package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val kind: String,
    val color: String,
    val place: String,
)

fun interface CalendarEventNormalizer {
    fun normalize(event: CalendarEvent): CalendarEvent?
}

sealed interface CalendarResult {
    data class Success(val events: List<CalendarEvent>) : CalendarResult
    data object SessionExpired : CalendarResult
    data object Retry : CalendarResult
    data object MalformedResponse : CalendarResult
}

class CalendarRepository(
    private val transport: KlasAuthenticatedTransport,
    private val normalizer: CalendarEventNormalizer,
) {
    suspend fun fetch(session: SecretValue, userAgent: KlasUserAgent, start: String, end: String): CalendarResult {
        val body = buildJsonObject { put("start", start); put("end", end) }
        return when (val result = transport.postJson(AuthenticatedKlasEndpoint.CALENDAR, session, userAgent, body)) {
            is KlasAuthenticatedResult.Success -> {
                val rows = result.body as? JsonArray ?: return CalendarResult.MalformedResponse
                val events = rows.mapNotNull { element ->
                    val row = element as? JsonObject ?: return CalendarResult.MalformedResponse
                    fun field(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val title = field("title")
                    if (title.isBlank()) return CalendarResult.MalformedResponse
                    normalizer.normalize(CalendarEvent(
                        id = field("id").ifBlank { "${field("started")}|${field("ended")}|$title" },
                        title = title.replace("[개인일정] ", "").replace("[학사일정] ", ""),
                        start = field("started"), end = field("ended"), kind = field("typeNm"),
                        color = field("schdulColor"),
                        place = if (field("typeNm") == "과제") field("subj") else field("place"),
                    ))
                }
                if (rows.isNotEmpty() && events.isEmpty()) return CalendarResult.MalformedResponse
                CalendarResult.Success(events.distinct().sortedWith(compareBy({ it.start }, { it.end }, { it.id })))
            }
            KlasAuthenticatedResult.SessionExpired -> CalendarResult.SessionExpired
            KlasAuthenticatedResult.MalformedResponse, KlasAuthenticatedResult.EmptyResponse -> CalendarResult.MalformedResponse
            else -> CalendarResult.Retry
        }
    }
}
