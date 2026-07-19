package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

@Serializable
internal data class PasswordEncryptionRequest(val loginPwd: String)

@Serializable
internal data class PasswordEncryptionResponse(val loginPwd: String)

class KlasAuthRepository(
    private val client: HttpClient,
) : PasswordEncryptionApi {
    override suspend fun encrypt(
        accountId: String,
        password: PlainPassword,
    ): PasswordEncryptionResult {
        if (accountId.isBlank()) return PasswordEncryptionResult.Failure(AuthFailure.InvalidCredentials)
        return try {
            val response = client.post(KlasUrls.KLAS_PASSWORD_ENCRYPT) {
                contentType(ContentType.Application.Json)
                setBody(PasswordEncryptionRequest(password.reveal()))
            }
            if (!response.status.isSuccess()) {
                PasswordEncryptionResult.Failure(AuthFailure.Network)
            } else {
                val encryptedPassword = response.body<PasswordEncryptionResponse>().loginPwd
                if (encryptedPassword.isBlank()) {
                    PasswordEncryptionResult.Failure(AuthFailure.MalformedResponse)
                } else {
                    PasswordEncryptionResult.Success(SecretValue.of(encryptedPassword))
                }
            }
        } catch (_: HttpRequestTimeoutException) {
            PasswordEncryptionResult.Failure(AuthFailure.Timeout)
        } catch (_: ContentConvertException) {
            PasswordEncryptionResult.Failure(AuthFailure.MalformedResponse)
        } catch (_: SerializationException) {
            PasswordEncryptionResult.Failure(AuthFailure.MalformedResponse)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            PasswordEncryptionResult.Failure(AuthFailure.Network)
        }
    }
}
