package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue

sealed interface WebAuthPageObservation {
    data object InjectCredential : WebAuthPageObservation
    data class Authenticated(val token: SecretValue) : WebAuthPageObservation
    data class Failed(val failure: AuthFailure) : WebAuthPageObservation
    data object Ignore : WebAuthPageObservation
}

class WebAuthObservationPolicy(
    private val loginUrl: String,
    private val allowedHost: String,
    private val sessionCookieName: String = "SESSION",
) {
    fun pageFinished(
        url: String,
        credentialInjected: Boolean,
        cookieHeader: String?,
    ): WebAuthPageObservation {
        if (url == loginUrl) {
            return if (credentialInjected) {
                WebAuthPageObservation.Failed(AuthFailure.TemporaryPasswordChangeRequired)
            } else {
                WebAuthPageObservation.InjectCredential
            }
        }
        if (!hasAllowedHost(url)) return WebAuthPageObservation.Ignore
        val token = cookieValue(cookieHeader, sessionCookieName)
            ?.takeIf(String::isNotBlank)
            ?: return WebAuthPageObservation.Ignore
        return WebAuthPageObservation.Authenticated(SecretValue.of(token))
    }

    fun alert(message: String?): AuthFailure =
        if (message?.contains(CAPTCHA_MESSAGE) == true) {
            AuthFailure.CaptchaRequired
        } else {
            AuthFailure.InvalidCredentials
        }

    private fun hasAllowedHost(url: String): Boolean {
        val authorityStart = url.indexOf("://")
        if (authorityStart < 0) return false
        val authority = url.substring(authorityStart + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = authority.substringBefore(':').lowercase()
        val normalizedAllowedHost = allowedHost.lowercase()
        return host == normalizedAllowedHost || host.endsWith(".$normalizedAllowedHost")
    }

    private fun cookieValue(header: String?, name: String): String? = header
        ?.split(';')
        ?.asSequence()
        ?.map(String::trim)
        ?.mapNotNull { cookie ->
            val separator = cookie.indexOf('=')
            if (separator <= 0) null else cookie.substring(0, separator) to cookie.substring(separator + 1)
        }
        ?.firstOrNull { it.first == name }
        ?.second

    private companion object {
        const val CAPTCHA_MESSAGE = "자동 입력 방지"
    }
}
