package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthStateMachineTest {
    private val machine = AuthStateMachine()
    private val credential = StoredCredential("2026000000", SecretValue.of("encrypted-password"))
    private val session = Session(SecretValue.of("session-token"), 1_000L)

    @Test
    fun bootstrapUsesValidSessionBeforeStoredCredential() {
        val state = machine.reduce(
            AuthState.Initializing,
            AuthEvent.Bootstrap(credential, session, 3_600_999L),
        )

        assertEquals(AuthState.SignedIn(session), state)
    }

    @Test
    fun bootstrapDoesNotExpireSessionByFixedLocalAge() {
        val state = machine.reduce(
            AuthState.Initializing,
            AuthEvent.Bootstrap(credential, session, 3_601_000L),
        )

        assertEquals(AuthState.SignedIn(session), state)
    }

    @Test
    fun bootstrapWithoutCredentialSignsOut() {
        val state = machine.reduce(
            AuthState.Initializing,
            AuthEvent.Bootstrap(null, null, 1_000L),
        )

        assertEquals(AuthState.SignedOut, state)
    }

    @Test
    fun captchaAndTemporaryPasswordRequireExplicitUserAction() {
        val captcha = machine.reduce(
            AuthState.Initializing,
            AuthEvent.LoginFailed(AuthFailure.CaptchaRequired),
        )
        val temporaryPassword = machine.reduce(
            AuthState.Initializing,
            AuthEvent.LoginFailed(AuthFailure.TemporaryPasswordChangeRequired),
        )

        assertIs<AuthState.UserActionRequired>(captcha)
        assertIs<AuthState.UserActionRequired>(temporaryPassword)
    }

    @Test
    fun networkTimeoutAndMalformedResponseRemainDistinct() {
        val failures = listOf(
            AuthFailure.Network,
            AuthFailure.Timeout,
            AuthFailure.MalformedResponse,
        ).map {
            machine.reduce(AuthState.Initializing, AuthEvent.LoginFailed(it))
        }

        assertEquals(
            listOf(
                AuthState.RecoverableFailure(AuthFailure.Network),
                AuthState.RecoverableFailure(AuthFailure.Timeout),
                AuthState.RecoverableFailure(AuthFailure.MalformedResponse),
            ),
            failures,
        )
    }

    @Test
    fun logoutAlwaysClearsAuthenticationState() {
        assertEquals(AuthState.SignedOut, machine.reduce(AuthState.SignedIn(session), AuthEvent.Logout))
    }
}
