package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun interface LoginTokenEncryptor {
    fun encrypt(publicKey: String, payload: String): String?
}

internal class KlasHttpAuthDriver(
    private val client: HttpClient,
    private val cookieStorage: CookiesStorage,
    private val tokenEncryptor: LoginTokenEncryptor,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : WebAuthDriver {
    override suspend fun authenticate(credential: StoredCredential): WebAuthResult {
        return try {
            val loginPage = client.get(KlasUrls.KLAS_LOGIN) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (!loginPage.status.isSuccess()) return networkFailure()

            val security = postJson(KlasUrls.KLAS_LOGIN_SECURITY, EmptyRequest())
            if (!security.status.isSuccess()) return networkFailure()
            val publicKey = json.decodeFromString<LoginSecurityResponse>(security.bodyAsText())
                .publicKey
                .takeIf(String::isNotBlank)
                ?: return malformedFailure()
            val payload = json.encodeToString(
                LoginPayload(
                    loginId = credential.accountId,
                    loginPwd = credential.encryptedPassword.reveal(),
                ),
            )
            val loginToken = runCatching { tokenEncryptor.encrypt(publicKey, payload) }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: return malformedFailure()

            val captcha = postJson(
                KlasUrls.KLAS_LOGIN_CAPTCHA,
                LoginCaptchaRequest(loginToken),
            )
            if (!captcha.status.isSuccess()) return networkFailure()
            val failureCount = captcha.bodyAsText().trim().toIntOrNull()
                ?: return malformedFailure()
            if (failureCount > CAPTCHA_THRESHOLD) {
                return WebAuthResult.Failure(AuthFailure.CaptchaRequired)
            }

            val confirm = postJson(
                KlasUrls.KLAS_LOGIN_CONFIRM,
                LoginConfirmRequest(loginToken),
            )
            if (!confirm.status.isSuccess()) return networkFailure()
            val confirmation = json.decodeFromString<LoginConfirmResponse>(confirm.bodyAsText())
            val errorCount = confirmation.errorCount ?: return malformedFailure()
            if (errorCount != 0) {
                return WebAuthResult.Failure(AuthFailure.InvalidCredentials)
            }
            val details = confirmation.response ?: return malformedFailure()
            if (
                details.frstPwdAt == REQUIRED ||
                details.certOpt == REQUIRED ||
                details.twoFactorAt == REQUIRED
            ) {
                return WebAuthResult.Failure(AuthFailure.TemporaryPasswordChangeRequired)
            }
            if (details.gradAt == REQUIRED) {
                return WebAuthResult.Failure(AuthFailure.InvalidCredentials)
            }

            val token = cookieStorage.get(Url(KlasUrls.KLAS_BASE))
                .firstOrNull { it.name == SESSION_COOKIE_NAME }
                ?.value
                ?.takeIf(String::isNotBlank)
                ?: return malformedFailure()
            WebAuthResult.SessionObserved(SecretValue.of(token))
        } catch (_: HttpRequestTimeoutException) {
            WebAuthResult.Failure(AuthFailure.Timeout)
        } catch (_: ConnectTimeoutException) {
            WebAuthResult.Failure(AuthFailure.Timeout)
        } catch (_: SocketTimeoutException) {
            WebAuthResult.Failure(AuthFailure.Timeout)
        } catch (_: SerializationException) {
            malformedFailure()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            networkFailure()
        }
    }

    private suspend inline fun <reified T> postJson(url: String, body: T) = client.post(url) {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Origin, KlasUrls.KLAS_BASE)
        header(HttpHeaders.Referrer, "${KlasUrls.KLAS_BASE}/")
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
        setBody(json.encodeToString(body))
    }

    private fun networkFailure() = WebAuthResult.Failure(AuthFailure.Network)

    private fun malformedFailure() = WebAuthResult.Failure(AuthFailure.MalformedResponse)

    private companion object {
        const val CAPTCHA_THRESHOLD = 2
        const val REQUIRED = "Y"
        const val SESSION_COOKIE_NAME = "SESSION"
        const val X_REQUESTED_WITH = "X-Requested-With"
        const val XML_HTTP_REQUEST = "XMLHttpRequest"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 " +
                "Whale/3.25.232.19 Safari/537.36"
    }
}

@Serializable
private class EmptyRequest

@Serializable
private data class LoginSecurityResponse(val publicKey: String = "")

@Serializable
private data class LoginPayload(
    val loginId: String,
    val loginPwd: String,
    val loginTp: String = "MST",
)

@Serializable
private data class LoginCaptchaRequest(
    val loginToken: String,
    val captcha: String = "",
)

@Serializable
private data class LoginConfirmRequest(
    val loginToken: String,
    val captcha: String = "",
    val redirectUrl: String = "",
    val pushToken: String = "",
)

@Serializable
private data class LoginConfirmResponse(
    val errorCount: Int? = null,
    val response: LoginConfirmDetails? = null,
)

@Serializable
private data class LoginConfirmDetails(
    val frstPwdAt: String? = null,
    val certOpt: String? = null,
    val gradAt: String? = null,
    val twoFactorAt: String? = null,
)
