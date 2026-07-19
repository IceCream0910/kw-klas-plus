package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class AuthenticatedKlasEndpoint(val url: String) {
    QR_CHECKIN(KlasUrls.KLAS_QR_CHECKIN),
    ATTENDANCE_SUBJECTS(KlasUrls.KLAS_ATTEND_SUBJECTS),
    ATTENDANCE_LIST(KlasUrls.KLAS_ATTEND_LIST),
    ATTENDANCE_RANDOM_KEY(KlasUrls.KLAS_RANDOM_KEY),
    ACADEMIC_TERM_SUBJECTS(KlasUrls.KLAS_ACADEMIC_TERM_SUBJECTS),
    TIMETABLE(KlasUrls.KLAS_TIMETABLE),
    ONLINE_LECTURE_DEADLINES(KlasUrls.KLAS_ONLINE_LECTURE_DEADLINES),
    TASK_DEADLINES(KlasUrls.KLAS_TASK_DEADLINES),
    TEAM_TASK_DEADLINES(KlasUrls.KLAS_TEAM_TASK_DEADLINES),
}

class KlasUserAgent private constructor(private val value: String) {
    fun legacyHeaderValue(): String = "$value NuriwareApp"

    companion object {
        fun fromPlatform(value: String): KlasUserAgent {
            require(value.isNotBlank())
            return KlasUserAgent(value.trim())
        }
    }
}

sealed interface KlasAuthenticatedResult {
    data class Success(val body: JsonElement) : KlasAuthenticatedResult
    data object SessionExpired : KlasAuthenticatedResult
    data class HttpFailure(val statusCode: Int) : KlasAuthenticatedResult
    data object EmptyResponse : KlasAuthenticatedResult
    data object MalformedResponse : KlasAuthenticatedResult
    data object Timeout : KlasAuthenticatedResult
    data object NetworkFailure : KlasAuthenticatedResult
}

class KlasSessionHttpClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : KlasAuthenticatedTransport {
    override suspend fun postJson(
        endpoint: AuthenticatedKlasEndpoint,
        session: SecretValue,
        userAgent: KlasUserAgent,
        body: JsonElement,
    ): KlasAuthenticatedResult {
        return try {
            val response = client.post(endpoint.url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Cookie, "SESSION=${session.reveal()}")
                header(HttpHeaders.UserAgent, userAgent.legacyHeaderValue())
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            when {
                response.status.value == 401 || response.status.value == 403 ->
                    KlasAuthenticatedResult.SessionExpired
                !response.status.isSuccess() ->
                    KlasAuthenticatedResult.HttpFailure(response.status.value)
                responseBody.isBlank() -> KlasAuthenticatedResult.EmptyResponse
                responseBody.contains("<!DOCTYPE html>", ignoreCase = true) ->
                    KlasAuthenticatedResult.SessionExpired
                else -> json.parseToJsonElement(responseBody).let { parsed ->
                    if (parsed is JsonObject || parsed is JsonArray) {
                        KlasAuthenticatedResult.Success(parsed)
                    } else {
                        KlasAuthenticatedResult.MalformedResponse
                    }
                }
            }
        } catch (_: HttpRequestTimeoutException) {
            KlasAuthenticatedResult.Timeout
        } catch (_: SerializationException) {
            KlasAuthenticatedResult.MalformedResponse
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            KlasAuthenticatedResult.NetworkFailure
        }
    }
}

fun interface KlasAuthenticatedTransport {
    suspend fun postJson(
        endpoint: AuthenticatedKlasEndpoint,
        session: SecretValue,
        userAgent: KlasUserAgent,
        body: JsonElement,
    ): KlasAuthenticatedResult
}
