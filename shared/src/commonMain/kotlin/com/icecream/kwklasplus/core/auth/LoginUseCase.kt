package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Session
import com.icecream.kwklasplus.core.session.SessionCoordinator
import com.icecream.kwklasplus.core.session.SessionResult

class PlainPassword private constructor(private val value: SecretValue) {
    fun reveal(): String = value.reveal()

    override fun toString(): String = "[REDACTED]"

    companion object {
        fun of(value: String): PlainPassword = PlainPassword(SecretValue.of(value))
    }
}

sealed interface PasswordEncryptionResult {
    data class Success(val encryptedPassword: SecretValue) : PasswordEncryptionResult
    data class Failure(val failure: AuthFailure) : PasswordEncryptionResult
}

fun interface PasswordEncryptionApi {
    suspend fun encrypt(accountId: String, password: PlainPassword): PasswordEncryptionResult
}

sealed interface WebAuthResult {
    data class SessionObserved(val token: SecretValue) : WebAuthResult
    data class Failure(val failure: AuthFailure) : WebAuthResult
}

fun interface WebAuthDriver {
    suspend fun authenticate(credential: StoredCredential): WebAuthResult
}

sealed interface LoginResult {
    data class Authenticated(val session: Session) : LoginResult
    data class UserActionRequired(val failure: AuthFailure) : LoginResult
    data class Failed(val failure: AuthFailure) : LoginResult
}

sealed interface CredentialPreparationResult {
    data class Success(val credential: StoredCredential) : CredentialPreparationResult
    data class Failure(val failure: AuthFailure) : CredentialPreparationResult
}

class PrepareCredentialUseCase(
    private val encryptionApi: PasswordEncryptionApi,
    private val credentialStore: CredentialStore,
) {
    suspend fun prepare(accountId: String, password: PlainPassword): CredentialPreparationResult {
        if (accountId.isBlank()) {
            return CredentialPreparationResult.Failure(AuthFailure.InvalidCredentials)
        }
        return when (val encryption = encryptionApi.encrypt(accountId, password)) {
            is PasswordEncryptionResult.Failure -> CredentialPreparationResult.Failure(
                encryption.failure,
            )
            is PasswordEncryptionResult.Success -> {
                val credential = StoredCredential(accountId, encryption.encryptedPassword)
                try {
                    credentialStore.save(credential)
                    CredentialPreparationResult.Success(credential)
                } catch (_: Throwable) {
                    CredentialPreparationResult.Failure(AuthFailure.Storage)
                }
            }
        }
    }
}

class LoginUseCase(
    private val prepareCredential: PrepareCredentialUseCase,
    private val credentialStore: CredentialStore,
    private val webAuthDriver: WebAuthDriver,
    private val sessionCoordinator: SessionCoordinator,
) {
    constructor(
        encryptionApi: PasswordEncryptionApi,
        credentialStore: CredentialStore,
        webAuthDriver: WebAuthDriver,
        sessionCoordinator: SessionCoordinator,
    ) : this(
        PrepareCredentialUseCase(encryptionApi, credentialStore),
        credentialStore,
        webAuthDriver,
        sessionCoordinator,
    )

    suspend fun login(accountId: String, password: PlainPassword): LoginResult {
        return when (val prepared = prepareCredential.prepare(accountId, password)) {
            is CredentialPreparationResult.Failure -> failure(prepared.failure)
            is CredentialPreparationResult.Success -> authenticate(prepared.credential)
        }
    }

    suspend fun resume(credential: StoredCredential): LoginResult = authenticate(credential)

    private suspend fun authenticate(credential: StoredCredential): LoginResult =
        when (val webResult = webAuthDriver.authenticate(credential)) {
            is WebAuthResult.Failure -> {
                if (webResult.failure == AuthFailure.InvalidCredentials) {
                    try {
                        credentialStore.clearPassword()
                        LoginResult.Failed(AuthFailure.InvalidCredentials)
                    } catch (_: Throwable) {
                        LoginResult.Failed(AuthFailure.Storage)
                    }
                } else {
                    failure(webResult.failure)
                }
            }
            is WebAuthResult.SessionObserved -> when (val session = sessionCoordinator.observe(webResult.token)) {
                is SessionResult.Active -> LoginResult.Authenticated(session.session)
                else -> LoginResult.Failed(AuthFailure.Storage)
            }
        }

    private fun failure(failure: AuthFailure): LoginResult = when (failure) {
        AuthFailure.CaptchaRequired,
        AuthFailure.TemporaryPasswordChangeRequired,
        AuthFailure.UserCancelled,
        -> LoginResult.UserActionRequired(failure)

        else -> LoginResult.Failed(failure)
    }
}
