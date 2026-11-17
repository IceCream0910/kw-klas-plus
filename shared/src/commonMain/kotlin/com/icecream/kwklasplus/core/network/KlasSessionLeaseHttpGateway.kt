package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.diagnostics.*
import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.SessionExtensionResult
import com.icecream.kwklasplus.core.session.SessionInfoResult
import com.icecream.kwklasplus.core.session.SessionLeaseGateway
import com.icecream.kwklasplus.core.session.SessionLeaseInfo
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

class KlasSessionLeaseHttpGateway(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionLeaseGateway {
    override suspend fun fetchInfo(
        session: SecretValue,
        userAgent: KlasUserAgent,
    ): SessionInfoResult {
        var malformedKind: MalformedResponseKind? = null
        var field: String? = null
        var responseText: String? = null
        var responseStatus: Int? = null
        var loginRedirect = false
        val result = try {
        val response = get(KlasUrls.KLAS_SESSION_INFO, session, userAgent)
        responseStatus = response.status.value
        loginRedirect = response.status.value in 300..399 && isKlasLoginRedirect(response.headers[HttpHeaders.Location])
        val body = response.bodyAsText()
        responseText = body
        when {
            response.status.value == 401 || response.status.value == 403 ->
                SessionInfoResult.SessionExpired
            !response.status.isSuccess() -> SessionInfoResult.HttpFailure(response.status.value)
            body.isKlasLoginHtml() -> SessionInfoResult.SessionExpired
            else -> parseInfo(body)
        }
    } catch (_: HttpRequestTimeoutException) {
        SessionInfoResult.Timeout
    } catch (_: ConnectTimeoutException) {
        SessionInfoResult.Timeout
    } catch (_: SocketTimeoutException) {
        SessionInfoResult.Timeout
    } catch (cause: SerializationException) {
        val diagnostic = diagnoseSessionInfo(cause, responseText)
        malformedKind = diagnostic.first
        field = diagnostic.second
        SessionInfoResult.MalformedResponse
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        SessionInfoResult.NetworkFailure
    }
        if (result == SessionInfoResult.MalformedResponse && malformedKind == null) malformedKind = MalformedResponseKind.INVALID_VALUE
        val failure = when (result) {
            is SessionInfoResult.Success -> null
            SessionInfoResult.Timeout -> DiagnosticFailure.TIMEOUT
            SessionInfoResult.NetworkFailure -> DiagnosticFailure.NETWORK
            SessionInfoResult.SessionExpired -> DiagnosticFailure.SESSION_EXPIRED
            SessionInfoResult.MalformedResponse -> DiagnosticFailure.MALFORMED
            is SessionInfoResult.HttpFailure -> DiagnosticFailure.HTTP
        }
        if (failure != null && !loginRedirect) NativeDiagnostics.report(
            DiagnosticStage.SESSION_INFO, failure, statusCode = responseStatus, malformedKind = malformedKind, field = field,
        )
        return result
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun diagnoseSessionInfo(cause: SerializationException, text: String?): Pair<MalformedResponseKind, String?> {
        if (cause is MissingFieldException) return MalformedResponseKind.MISSING_FIELD to cause.missingFields.firstOrNull()
        if (text.isNullOrBlank()) return MalformedResponseKind.EMPTY_BODY to null
        val body = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return MalformedResponseKind.INVALID_JSON_STRUCTURE to null
        val descriptor = SessionInfoResponse.serializer().descriptor
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val value = body[name] as? JsonPrimitive ?: if (name in body) return MalformedResponseKind.INVALID_FIELD_TYPE to name else continue
            if (value.isString || value.longOrNull == null) return MalformedResponseKind.INVALID_FIELD_TYPE to name
        }
        return MalformedResponseKind.INVALID_VALUE to null
    }

    override suspend fun extend(
        session: SecretValue,
        userAgent: KlasUserAgent,
    ): SessionExtensionResult = (try {
        val response = get(KlasUrls.KLAS_SESSION_UPDATE, session, userAgent)
        val body = response.bodyAsText()
        when {
            response.status.value == 401 || response.status.value == 403 ->
                SessionExtensionResult.SessionExpired
            !response.status.isSuccess() -> SessionExtensionResult.HttpFailure(response.status.value)
            body.isKlasLoginHtml() -> SessionExtensionResult.SessionExpired
            body.isBlank() -> SessionExtensionResult.MalformedResponse
            json.parseToJsonElement(body) is JsonObject -> SessionExtensionResult.Success
            else -> SessionExtensionResult.MalformedResponse
        }
    } catch (_: HttpRequestTimeoutException) {
        SessionExtensionResult.Timeout
    } catch (_: ConnectTimeoutException) {
        SessionExtensionResult.Timeout
    } catch (_: SocketTimeoutException) {
        SessionExtensionResult.Timeout
    } catch (_: SerializationException) {
        SessionExtensionResult.MalformedResponse
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        SessionExtensionResult.NetworkFailure
    }).also {
        val failure = when (it) {
            SessionExtensionResult.Success -> null
            SessionExtensionResult.Timeout -> DiagnosticFailure.TIMEOUT
            SessionExtensionResult.NetworkFailure -> DiagnosticFailure.NETWORK
            SessionExtensionResult.SessionExpired -> DiagnosticFailure.SESSION_EXPIRED
            SessionExtensionResult.MalformedResponse -> DiagnosticFailure.MALFORMED
            is SessionExtensionResult.HttpFailure -> DiagnosticFailure.HTTP
        }
        if (failure != null) NativeDiagnostics.report(
            DiagnosticStage.SESSION_EXTEND, failure, statusCode = (it as? SessionExtensionResult.HttpFailure)?.statusCode,
        )
    }

    private suspend fun get(url: String, session: SecretValue, userAgent: KlasUserAgent) =
        client.get(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Cookie, "SESSION=${session.reveal()}")
            header(HttpHeaders.Referrer, KlasUrls.KLAS_FRAME)
            header(HttpHeaders.UserAgent, userAgent.legacyHeaderValue())
            header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
        }

    private fun parseInfo(body: String): SessionInfoResult {
        val element = json.parseToJsonElement(body)
        if (element is JsonObject && element.isEmpty()) {
            return SessionInfoResult.SessionExpired
        }
        val response = json.decodeFromString<SessionInfoResponse>(body)
        return runCatching {
            SessionLeaseInfo(
                logoutCountDownSeconds = response.logoutCountDownSec,
                sessionNotificationSeconds = response.sessionNotiSec,
                remainingSeconds = response.remainingTime,
            )
        }.fold(
            onSuccess = SessionInfoResult::Success,
            onFailure = { SessionInfoResult.MalformedResponse },
        )
    }

    private companion object {
        const val X_REQUESTED_WITH = "X-Requested-With"
        const val XML_HTTP_REQUEST = "XMLHttpRequest"
    }
}

@Serializable
private data class SessionInfoResponse(
    val logoutCountDownSec: Long,
    val sessionNotiSec: Long,
    val remainingTime: Long,
)
