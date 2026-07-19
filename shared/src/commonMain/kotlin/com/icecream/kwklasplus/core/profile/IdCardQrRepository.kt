package com.icecream.kwklasplus.core.profile

import com.icecream.kwklasplus.core.bridge.KlasContentOriginPolicy
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

data class IdCardQrRequest(
    val url: String,
    val cookies: SecretValue,
)

sealed interface IdCardQrResult {
    data class Success(val value: String) : IdCardQrResult
    data object UntrustedUrl : IdCardQrResult
    data object NetworkFailure : IdCardQrResult
    data object InvalidResponse : IdCardQrResult
}

class IdCardQrParser(
    private val json: Json = Json,
) {
    fun parse(body: String): String? {
        val match = QR_TEXT_PATTERN.find(body) ?: return null
        return runCatching { json.decodeFromString<String>(match.groupValues[1]) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
    }

    private companion object {
        val QR_TEXT_PATTERN = Regex("""\btext\s*:\s*("(?:\\.|[^"\\])*")""")
    }
}

class IdCardQrRepository(
    private val client: HttpClient,
    private val parser: IdCardQrParser = IdCardQrParser(),
    private val originPolicy: KlasContentOriginPolicy = KlasContentOriginPolicy(),
) {
    suspend fun fetch(request: IdCardQrRequest): IdCardQrResult {
        if (!originPolicy.isTrustedUrl(request.url)) return IdCardQrResult.UntrustedUrl
        return try {
            val response = client.get(request.url) {
                header(HttpHeaders.Cookie, request.cookies.reveal())
            }
            if (!response.status.isSuccess()) {
                IdCardQrResult.NetworkFailure
            } else {
                response.bodyAsText()
                    .takeIf { it.length <= MAXIMUM_RESPONSE_CHARS }
                    ?.let(parser::parse)
                    ?.let(IdCardQrResult::Success)
                    ?: IdCardQrResult.InvalidResponse
            }
        } catch (_: HttpRequestTimeoutException) {
            IdCardQrResult.NetworkFailure
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            IdCardQrResult.NetworkFailure
        }
    }

    private companion object {
        const val MAXIMUM_RESPONSE_CHARS = 512 * 1_024
    }
}
