package com.icecream.kwklasplus.core.academic

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
    ): AcademicTermsResult {
        return when (
            val result = transport.postJson(
                AuthenticatedKlasEndpoint.ACADEMIC_TERM_SUBJECTS,
                session,
                userAgent,
                buildJsonObject {},
            )
        ) {
            is KlasAuthenticatedResult.Success -> mapTerms(result.body as? JsonArray)
            KlasAuthenticatedResult.SessionExpired -> AcademicTermsResult.SessionExpired
            KlasAuthenticatedResult.Timeout -> AcademicTermsResult.Timeout
            KlasAuthenticatedResult.NetworkFailure -> AcademicTermsResult.NetworkFailure
            is KlasAuthenticatedResult.HttpFailure -> AcademicTermsResult.HttpFailure(result.statusCode)
            KlasAuthenticatedResult.EmptyResponse -> AcademicTermsResult.EmptyResponse
            KlasAuthenticatedResult.MalformedResponse -> AcademicTermsResult.MalformedResponse
        }
    }

    private fun mapTerms(body: JsonArray?): AcademicTermsResult {
        body ?: return AcademicTermsResult.MalformedResponse
        val terms = body.map { element ->
            val term = element as? JsonObject ?: return AcademicTermsResult.MalformedResponse
            val value = term.string("value")?.takeIf(String::isNotBlank)
                ?: return AcademicTermsResult.MalformedResponse
            val subjectArray = term["subjList"] as? JsonArray
                ?: return AcademicTermsResult.MalformedResponse
            val subjects = subjectArray.map { subjectElement ->
                val subject = subjectElement as? JsonObject
                    ?: return AcademicTermsResult.MalformedResponse
                AcademicSubject(
                    id = subject.string("value")?.takeIf(String::isNotBlank)
                        ?: return AcademicTermsResult.MalformedResponse,
                    name = subject.string("name")?.takeIf(String::isNotBlank)
                        ?: return AcademicTermsResult.MalformedResponse,
                )
            }
            AcademicTerm(value, subjects)
        }
        return AcademicTermsResult.Success(terms)
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull
}
