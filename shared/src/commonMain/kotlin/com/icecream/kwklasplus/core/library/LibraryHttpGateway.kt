package com.icecream.kwklasplus.core.library

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class LibraryHttpGateway(
    private val client: HttpClient,
    private val parser: LibraryXmlParser = LibraryXmlParser(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : LibraryGateway {
    override suspend fun requestSecret(encodedRealId: String): LibraryGatewayResult<String> =
        postForm(
            path = "/mobile/MA/xml_user_key.php",
            parameters = Parameters.build { append("user_id", encodedRealId) },
        ) { body ->
            parser.parseValue(body, "sec_key")
                ?.takeIf(String::isNotBlank)
                ?.let(LibraryGatewayResult<String>::Success)
                ?: LibraryGatewayResult.InvalidResponse
        }

    override suspend fun login(
        encodedRealId: String,
        encodedStudentNumber: String,
        phoneNumber: String,
        encryptedPassword: String,
        deviceCode: String,
    ): LibraryGatewayResult<String> = postForm(
        path = "/mobile/MA/xml_login_and.php",
        parameters = Parameters.build {
            append("real_id", encodedRealId)
            append("rid", encodedStudentNumber)
            append("device_gb", deviceCode)
            append("tel_no", phoneNumber)
            append("pass_wd", encryptedPassword)
        },
    ) { body ->
        parser.parseValue(body, "auth_key")
            ?.takeIf(String::isNotBlank)
            ?.let(LibraryGatewayResult<String>::Success)
            ?: LibraryGatewayResult.AuthenticationFailure
    }

    override suspend fun requestQr(
        encodedRealId: String,
        authKey: String,
    ): LibraryGatewayResult<LibraryQrData> = postForm(
        path = "/mobile/MA/xml_userInfo_auth.php",
        parameters = Parameters.build {
            append("real_id", encodedRealId)
            append("auth_key", authKey)
            append("new_check", "Y")
        },
    ) { body ->
        parser.parseFlatValues(body)
            .takeIf(Map<*, *>::isNotEmpty)
            ?.let(::LibraryQrData)
            ?.let(LibraryGatewayResult<LibraryQrData>::Success)
            ?: LibraryGatewayResult.InvalidResponse
    }

    private suspend fun <T> postForm(
        path: String,
        parameters: Parameters,
        transform: (String) -> LibraryGatewayResult<T>,
    ): LibraryGatewayResult<T> = try {
        val response = client.post("$baseUrl$path") {
            setBody(FormDataContent(parameters))
        }
        if (!response.status.isSuccess()) {
            LibraryGatewayResult.NetworkFailure
        } else {
            response.bodyAsText()
                .takeIf { it.isNotBlank() && it.length <= MAXIMUM_RESPONSE_CHARS }
                ?.let(transform)
                ?: LibraryGatewayResult.InvalidResponse
        }
    } catch (_: HttpRequestTimeoutException) {
        LibraryGatewayResult.NetworkFailure
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        LibraryGatewayResult.NetworkFailure
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://mobileid.kw.ac.kr"
        const val MAXIMUM_RESPONSE_CHARS = 512 * 1_024
    }
}
