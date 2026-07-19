package com.icecream.kwklasplus.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebAuthObservationPolicyTest {
    private val loginUrl = "https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do"
    private val policy = WebAuthObservationPolicy(loginUrl, "kw.ac.kr")

    @Test
    fun firstLoginPageInjectsCredentialOnlyOnce() {
        assertEquals(
            WebAuthPageObservation.InjectCredential,
            policy.pageFinished(loginUrl, credentialInjected = false, cookieHeader = null),
        )
        assertEquals(
            WebAuthPageObservation.Failed(AuthFailure.TemporaryPasswordChangeRequired),
            policy.pageFinished(loginUrl, credentialInjected = true, cookieHeader = null),
        )
    }

    @Test
    fun extractsSessionCookieWithoutLosingEqualsCharacters() {
        val result = policy.pageFinished(
            "https://klas.kw.ac.kr/std/cmn/frame/Frame.do",
            credentialInjected = true,
            cookieHeader = "other=value; SESSION=abc==; theme=dark",
        )

        assertEquals("abc==", assertIs<WebAuthPageObservation.Authenticated>(result).token.reveal())
    }

    @Test
    fun ignoresExternalAndLookalikeHosts() {
        assertEquals(
            WebAuthPageObservation.Ignore,
            policy.pageFinished(
                "https://kw.ac.kr.attacker.example/path",
                credentialInjected = true,
                cookieHeader = "SESSION=secret",
            ),
        )
        assertEquals(
            WebAuthPageObservation.Ignore,
            policy.pageFinished(
                "https://example.com/path",
                credentialInjected = true,
                cookieHeader = "SESSION=secret",
            ),
        )
    }

    @Test
    fun acceptsAllowedSubdomainsAndIgnoresMissingSession() {
        assertIs<WebAuthPageObservation.Authenticated>(
            policy.pageFinished(
                "https://klas.kw.ac.kr/path",
                credentialInjected = true,
                cookieHeader = "SESSION=token",
            ),
        )
        assertEquals(
            WebAuthPageObservation.Ignore,
            policy.pageFinished(
                "https://klas.kw.ac.kr/path",
                credentialInjected = true,
                cookieHeader = "other=value",
            ),
        )
    }

    @Test
    fun classifiesCaptchaSeparatelyFromOtherAlerts() {
        assertEquals(AuthFailure.CaptchaRequired, policy.alert("자동 입력 방지 문자를 입력하세요"))
        assertEquals(AuthFailure.InvalidCredentials, policy.alert("아이디 또는 비밀번호 오류"))
    }
}
