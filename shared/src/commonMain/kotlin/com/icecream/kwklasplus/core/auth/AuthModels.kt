package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Session

data class StoredCredential(
    val accountId: String,
    val encryptedPassword: SecretValue,
) {
    init {
        require(accountId.isNotBlank())
    }
}

interface CredentialStore {
    suspend fun load(): StoredCredential?
    suspend fun save(credential: StoredCredential)
    suspend fun clear()
}

sealed interface AuthFailure {
    data object Network : AuthFailure
    data object Timeout : AuthFailure
    data object InvalidCredentials : AuthFailure
    data object MalformedResponse : AuthFailure
    data object Storage : AuthFailure
    data object CaptchaRequired : AuthFailure
    data object TemporaryPasswordChangeRequired : AuthFailure
    data object UserCancelled : AuthFailure
}

sealed interface AuthState {
    data object Initializing : AuthState
    data object SignedOut : AuthState
    data class AwaitingWebLogin(val credential: StoredCredential) : AuthState
    data class SignedIn(val session: Session) : AuthState
    data class RecoverableFailure(val failure: AuthFailure) : AuthState
    data class UserActionRequired(val failure: AuthFailure) : AuthState
}

sealed interface AuthEvent {
    data class Bootstrap(
        val credential: StoredCredential?,
        val session: Session?,
        val nowEpochMillis: Long,
    ) : AuthEvent

    data class CredentialEncrypted(val credential: StoredCredential) : AuthEvent
    data class SessionObserved(val session: Session) : AuthEvent
    data class LoginFailed(val failure: AuthFailure) : AuthEvent
    data object Logout : AuthEvent
}
