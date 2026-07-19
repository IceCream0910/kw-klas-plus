package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.session.SessionPolicy

class AuthStateMachine(
    private val sessionPolicy: SessionPolicy = SessionPolicy(),
) {
    fun reduce(state: AuthState, event: AuthEvent): AuthState = when (event) {
        is AuthEvent.Bootstrap -> bootstrap(event)
        is AuthEvent.CredentialEncrypted -> AuthState.AwaitingWebLogin(event.credential)
        is AuthEvent.SessionObserved -> AuthState.SignedIn(event.session)
        is AuthEvent.LoginFailed -> failure(event.failure)
        AuthEvent.Logout -> AuthState.SignedOut
    }

    private fun bootstrap(event: AuthEvent.Bootstrap): AuthState {
        val session = event.session
        if (session != null && sessionPolicy.isUsable(session, event.nowEpochMillis)) {
            return AuthState.SignedIn(session)
        }
        return event.credential?.let(AuthState::AwaitingWebLogin) ?: AuthState.SignedOut
    }

    private fun failure(failure: AuthFailure): AuthState = when (failure) {
        AuthFailure.CaptchaRequired,
        AuthFailure.TemporaryPasswordChangeRequired,
        AuthFailure.UserCancelled,
        -> AuthState.UserActionRequired(failure)

        else -> AuthState.RecoverableFailure(failure)
    }
}
