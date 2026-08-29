package com.icecream.kwklasplus.core.network

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class KlasSessionLeaseHttpGateway(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionLeaseGateway {
    override suspend fun fetchInfo(
        session: SecretValue,
        userAgent: KlasUserAgent,
    ): SessionInfoResult = try {
        val response = get(KlasUrls.KLAS_SESSION_INFO, session, userAgent)
        val body = response.bodyAsText()
        when {
            response.status.value == 401 || response.status.value == 403 ->
                SessionInfoResult.SessionExpired
            !response.status.isSuccess() -> SessionInfoResult.HttpFailure(response.status.value)
            body.isLoginHtml() -> SessionInfoResult.SessionExpired
            else -> parseInfo(body)
        }
    } catch (_: HttpRequestTimeoutException) {
        SessionInfoResult.Timeout
    } catch (_: ConnectTimeoutException) {
        SessionInfoResult.Timeout
    } catch (_: SocketTimeoutException) {
        SessionInfoResult.Timeout
    } catch (_: SerializationException) {
        SessionInfoResult.MalformedResponse
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        SessionInfoResult.NetworkFailure
    }

    override suspend fun extend(
        session: SecretValue,
        userAgent: KlasUserAgent,
    ): SessionExtensionResult = try {
        val response = get(KlasUrls.KLAS_SESSION_UPDATE, session, userAgent)
        val body = response.bodyAsText()
        when {
            response.status.value == 401 || response.status.value == 403 ->
                SessionExtensionResult.SessionExpired
            !response.status.isSuccess() -> SessionExtensionResult.HttpFailure(response.status.value)
            body.isLoginHtml() -> SessionExtensionResult.SessionExpired
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

    private fun String.isLoginHtml(): Boolean =
        contains("<!DOCTYPE html", ignoreCase = true) ||
            contains("<html", ignoreCase = true)

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
