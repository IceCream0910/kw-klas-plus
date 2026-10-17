package com.icecream.kwklasplus.core.network

import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

object KlasApiContract {
    const val BASE = "https://klas.kw.ac.kr"
    const val MAXIMUM_RESPONSE_BYTES = 4 * 1024 * 1024
    val methods = setOf("requestKlasApi", "readKlasApiChunk", "cancelKlasApi")
    fun path(value: String): String? {
        val page = value.startsWith("page:")
        val target = if (page) value.removePrefix("page:") else value
        if (target.length > 8192 || target.any { it <= ' ' || it == '\u007f' || it == '\\' }) return null
        val path = when {
            target.startsWith("$BASE/") -> target.removePrefix(BASE)
            target.startsWith("/") -> target
            else -> return null
        }
        if (path.startsWith("//") || '#' in path) return null
        val pathname = path.substringBefore('?')
        if ('%' in pathname || pathname.split('/').any { it == "." || it == ".." }) return null
        return if (page) "page:$path" else path
    }
    fun validPayload(payload: String): Boolean {
        if (payload.length > 48 * 1024) return false
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in payload) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 16) return false
                '}', ']' -> if (--depth < 0) return false
            }
        }
        return !quoted && depth == 0
    }
    fun isMutation(path: String) = !path.startsWith("page:")
}

sealed interface KlasApiResult {
    data class Success(val body: String, val session: SecretValue, val context: String?) : KlasApiResult
    data class Failure(val code: String, val status: Int? = null) : KlasApiResult
}

class KlasApiCore(
    client: HttpClient,
    private val session: suspend () -> SecretValue?,
    private val userAgent: () -> KlasUserAgent,
    private val context: () -> String? = { null },
) {
    private val http = client.config { followRedirects = false }
    private val slots = Semaphore(4)

    suspend fun currentSession(): SecretValue? = session()
    fun currentContext(): String? = context()

    suspend fun request(endpoint: String, payload: String): KlasApiResult {
        val operation = KlasApiContract.path(endpoint) ?: return KlasApiResult.Failure("INVALID_ENDPOINT")
        val isPage = operation.startsWith("page:")
        val path = operation.removePrefix("page:")
        if (!KlasApiContract.validPayload(payload)) return KlasApiResult.Failure("INVALID_PAYLOAD")
        val body = try { Json.parseToJsonElement(payload) } catch (_: Exception) {
            return KlasApiResult.Failure("INVALID_PAYLOAD")
        }
        if (body !is JsonObject && body != JsonNull) return KlasApiResult.Failure("INVALID_PAYLOAD")
        val token = session() ?: return KlasApiResult.Failure("SESSION_REQUIRED")
        val requestContext = context()
        return slots.withPermit {
            if (session() != token) return@withPermit KlasApiResult.Failure("STALE_SESSION")
            if (context() != requestContext) return@withPermit KlasApiResult.Failure("STALE_CONTEXT")
            try {
                http.prepareRequest(KlasApiContract.BASE + path) {
                    method = if (isPage) HttpMethod.Get else HttpMethod.Post
                    if (!isPage) contentType(ContentType.Application.Json)
                    header(HttpHeaders.Accept, "application/json, text/plain, */*")
                    header(HttpHeaders.Cookie, "SESSION=${token.reveal()};")
                    header(HttpHeaders.UserAgent, userAgent().legacyHeaderValue())
                    if (!isPage && body != JsonNull) setBody(body.toString())
                }
                .execute { response ->
                    if (session() != token) return@execute KlasApiResult.Failure("STALE_SESSION")
                    if (response.status.value in 300..399) return@execute KlasApiResult.Failure("AUTH_ACTION_REQUIRED", response.status.value)
                    if (response.status.value == 401) return@execute KlasApiResult.Failure("SESSION_EXPIRED", 401)
                    if (response.status.value == 403) return@execute KlasApiResult.Failure("ACCESS_DENIED", 403)
                    if (!response.status.isSuccess()) return@execute KlasApiResult.Failure("HTTP_FAILURE", response.status.value)
                    val channel = response.bodyAsChannel()
                    val bytes = ByteArray(KlasApiContract.MAXIMUM_RESPONSE_BYTES + 1)
                    var size = 0
                    while (size < bytes.size) {
                        val count = channel.readAvailable(bytes, size, bytes.size - size)
                        if (count == -1) break
                        size += count
                    }
                    if (size > KlasApiContract.MAXIMUM_RESPONSE_BYTES) {
                        channel.cancel(null)
                        return@execute KlasApiResult.Failure("RESPONSE_TOO_LARGE")
                    }
                    if (session() != token) return@execute KlasApiResult.Failure("STALE_SESSION")
                    if (context() != requestContext) return@execute KlasApiResult.Failure("STALE_CONTEXT")
                    val text = bytes.decodeToString(endIndex = size)
                    if (!isPage && text.trimStart().startsWith("<")) return@execute KlasApiResult.Failure("AUTH_ACTION_REQUIRED")
                    if (!isPage) try { Json.parseToJsonElement(text) } catch (_: Exception) {
                        return@execute KlasApiResult.Failure("MALFORMED_RESPONSE")
                    }
                    val resultBody = if (isPage) JsonPrimitive(text).toString() else text
                    if (resultBody.encodeToByteArray().size > KlasApiContract.MAXIMUM_RESPONSE_BYTES) return@execute KlasApiResult.Failure("RESPONSE_TOO_LARGE")
                    KlasApiResult.Success(resultBody, token, requestContext)
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: HttpRequestTimeoutException) {
                KlasApiResult.Failure(if (KlasApiContract.isMutation(path)) "OUTCOME_UNKNOWN" else "TIMEOUT")
            } catch (_: Exception) {
                KlasApiResult.Failure(if (KlasApiContract.isMutation(path)) "OUTCOME_UNKNOWN" else "NETWORK_FAILURE")
            }
        }
    }
}
