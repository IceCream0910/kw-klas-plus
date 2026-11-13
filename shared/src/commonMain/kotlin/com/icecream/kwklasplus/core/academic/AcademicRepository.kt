package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.diagnostics.DiagnosticFailure
import com.icecream.kwklasplus.core.diagnostics.DiagnosticStage
import com.icecream.kwklasplus.core.diagnostics.NativeDiagnostics
import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

@Serializable
data class AcademicSubject(
    val id: String,
    val name: String,
)

@Serializable
data class AcademicTerm(
    val value: String,
    val subjects: List<AcademicSubject>,
)

data class AcademicTermKey(
    val year: String,
    val semester: String,
) {
    companion object {
        fun parse(value: String): AcademicTermKey? {
            val parts = value.split(',', limit = 2)
            if (parts.size != 2) return null
            val year = parts[0].trim()
            val semester = parts[1].trim()
            if (year.isEmpty() || semester.isEmpty()) return null
            return AcademicTermKey(year, semester)
        }
    }
}

data class AcademicTermSelection(
    val index: Int,
    val term: AcademicTerm,
)

object AcademicTermSelector {
    fun select(terms: List<AcademicTerm>, savedValue: String?): AcademicTermSelection? {
        if (terms.isEmpty()) return null
        val index = savedValue
            ?.takeIf(String::isNotBlank)
            ?.let { saved -> terms.indexOfFirst { it.value == saved } }
            ?.takeIf { it >= 0 }
            ?: 0
        return AcademicTermSelection(index, terms[index])
    }
}

sealed interface AcademicTermsResult {
    data class Selected(
        val availableTerms: List<String>,
        val selection: AcademicTermSelection?,
    ) : AcademicTermsResult
    data class Success(val terms: List<AcademicTerm>) : AcademicTermsResult
    data object SessionExpired : AcademicTermsResult
    data object Timeout : AcademicTermsResult
    data object NetworkFailure : AcademicTermsResult
    data class HttpFailure(val statusCode: Int) : AcademicTermsResult
    data object EmptyResponse : AcademicTermsResult
    data object MalformedResponse : AcademicTermsResult
}

class AcademicRepository(
    private val transport: KlasAuthenticatedTransport,
) {
    suspend fun fetchTerms(
        session: SecretValue,
        userAgent: KlasUserAgent,
    ): AcademicTermsResult = fetch(session, userAgent, ::mapTerms)

    suspend fun fetchSelectedTerm(
        session: SecretValue,
        userAgent: KlasUserAgent,
        savedValue: String?,
        allowFallback: Boolean = true,
    ): AcademicTermsResult = fetch(session, userAgent) { body ->
        val entries = termEntries(body) ?: return@fetch AcademicTermsResult.MalformedResponse
        val values = entries.map { it.first }
        val index = values.indexOf(savedValue).takeIf { it >= 0 }
            ?: 0.takeIf { allowFallback && values.isNotEmpty() }
        val selection = index?.let {
            val (value, entry) = entries[it]
            val subjects = parseSubjects(entry) ?: return@fetch AcademicTermsResult.MalformedResponse
            AcademicTermSelection(it, AcademicTerm(value, subjects))
        }
        AcademicTermsResult.Selected(values, selection)
    }

    private suspend fun fetch(
        session: SecretValue,
        userAgent: KlasUserAgent,
        decode: (JsonArray?) -> AcademicTermsResult,
    ): AcademicTermsResult {
        val result = when (
            val result = transport.postJson(
                AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS,
                session,
                userAgent,
                buildJsonObject {},
            )
        ) {
            is KlasAuthenticatedResult.Success -> decode(result.body as? JsonArray)
            KlasAuthenticatedResult.SessionExpired -> AcademicTermsResult.SessionExpired
            KlasAuthenticatedResult.Timeout -> AcademicTermsResult.Timeout
            KlasAuthenticatedResult.NetworkFailure -> AcademicTermsResult.NetworkFailure
            is KlasAuthenticatedResult.HttpFailure -> AcademicTermsResult.HttpFailure(result.statusCode)
            KlasAuthenticatedResult.EmptyResponse -> AcademicTermsResult.EmptyResponse
            KlasAuthenticatedResult.MalformedResponse -> AcademicTermsResult.MalformedResponse
        }
        if (result == AcademicTermsResult.MalformedResponse) {
            NativeDiagnostics.report(
                DiagnosticStage.TERM_SUBJECTS,
                DiagnosticFailure.MALFORMED,
            )
        }
        return result
    }

    private fun mapTerms(body: JsonArray?): AcademicTermsResult {
        val entries = termEntries(body) ?: return AcademicTermsResult.MalformedResponse
        return AcademicTermsResult.Success(entries.map { (value, entry) ->
            AcademicTerm(value, parseSubjects(entry) ?: return AcademicTermsResult.MalformedResponse)
        })
    }

    private fun termEntries(body: JsonArray?): List<Pair<String, JsonObject>>? =
        body?.map { element ->
            val term = element as? JsonObject ?: return null
            val value = term.string("value")?.takeIf(String::isNotBlank) ?: return null
            value to term
        }

    private fun parseSubjects(term: JsonObject): List<AcademicSubject>? =
        (term["subjList"] as? JsonArray)?.map { element ->
            val subject = element as? JsonObject ?: return null
            AcademicSubject(
                subject.string("value")?.takeIf(String::isNotBlank) ?: return null,
                subject.string("name")?.takeIf(String::isNotBlank) ?: return null,
            )
        }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull
}

object AcademicTermDisplay {
    fun buttonText(value: String): String =
        value.replace(",3", ",여름").replace(",4", ",겨울").replace(",", "년도 ") + "학기"
}
