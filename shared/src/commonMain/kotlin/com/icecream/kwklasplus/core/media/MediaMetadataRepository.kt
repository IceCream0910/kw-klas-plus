package com.icecream.kwklasplus.core.media

import com.icecream.kwklasplus.core.bridge.KlasContentOriginPolicy
import com.icecream.kwklasplus.core.web.HtmlTextParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

sealed interface MediaMetadataResult {
    data class Success(val title: String) : MediaMetadataResult
    data object UntrustedUrl : MediaMetadataResult
    data object NetworkFailure : MediaMetadataResult
    data object InvalidResponse : MediaMetadataResult
}

class MediaMetadataRepository(
    private val client: HttpClient,
    private val htmlTextParser: HtmlTextParser = HtmlTextParser(),
    private val originPolicy: KlasContentOriginPolicy = KlasContentOriginPolicy(),
) {
    suspend fun fetchTitle(url: String): MediaMetadataResult {
        if (!originPolicy.isTrustedUrl(url)) return MediaMetadataResult.UntrustedUrl
        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                MediaMetadataResult.NetworkFailure
            } else {
                response.bodyAsText()
                    .takeIf { it.length <= MAXIMUM_RESPONSE_CHARS }
                    ?.let(htmlTextParser::title)
                    ?.let(MediaMetadataResult::Success)
                    ?: MediaMetadataResult.InvalidResponse
            }
        } catch (_: HttpRequestTimeoutException) {
            MediaMetadataResult.NetworkFailure
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            MediaMetadataResult.NetworkFailure
        }
    }

    private companion object {
        const val MAXIMUM_RESPONSE_CHARS = 1024 * 1_024
    }
}
