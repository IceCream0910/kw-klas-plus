package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.session.Session
import com.icecream.kwklasplus.core.session.SessionCoordinator
import com.icecream.kwklasplus.core.session.SessionStore
import com.icecream.kwklasplus.core.session.WebCookieStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LoginUseCaseTest {
    @Test
    fun encryptsStoresAndAuthenticatesWithoutExposingPlainPassword() = runLoginTest {
        val credentialStore = FakeCredentialStore()
        val sessionStore = FakeSessionStore()
        val cookieStore = FakeCookieStore()
        val encrypted = SecretValue.of("encrypted-password")
        val token = SecretValue.of("session-token")
        var receivedPassword: PlainPassword? = null
        val useCase = useCase(
            credentialStore = credentialStore,
            sessionStore = sessionStore,
            cookieStore = cookieStore,
            encryptionApi = PasswordEncryptionApi { _, password ->
                receivedPassword = password
                PasswordEncryptionResult.Success(encrypted)
            },
            webAuthDriver = WebAuthDriver { WebAuthResult.SessionObserved(token) },
        )

        val result = useCase.login("2026000000", PlainPassword.of("plain-password"))

        assertIs<LoginResult.Authenticated>(result)
        assertEquals(StoredCredential("2026000000", encrypted), credentialStore.credential)
        assertEquals(token, sessionStore.session?.token)
        assertEquals(token, cookieStore.token)
        assertEquals("[REDACTED]", receivedPassword.toString())
    }

    @Test
    fun encryptionFailureDoesNotPersistCredentialOrStartWebLogin() = runLoginTest {
        val credentialStore = FakeCredentialStore()
        var webLoginStarted = false
        val useCase = useCase(
            credentialStore = credentialStore,
            encryptionApi = PasswordEncryptionApi { _, _ ->
                PasswordEncryptionResult.Failure(AuthFailure.Network)
            },
            webAuthDriver = WebAuthDriver {
                webLoginStarted = true
                WebAuthResult.Failure(AuthFailure.Network)
            },
        )

        val result = useCase.login("2026000000", PlainPassword.of("plain-password"))

        assertEquals(LoginResult.Failed(AuthFailure.Network), result)
        assertNull(credentialStore.credential)
        assertEquals(false, webLoginStarted)
    }

    @Test
    fun captchaAndTemporaryPasswordRemainUserActions() = runLoginTest {
        val captchaUseCase = useCase(
            webAuthDriver = WebAuthDriver { WebAuthResult.Failure(AuthFailure.CaptchaRequired) },
        )
        val temporaryPasswordUseCase = useCase(
            webAuthDriver = WebAuthDriver {
                WebAuthResult.Failure(AuthFailure.TemporaryPasswordChangeRequired)
            },
        )

        val captcha = captchaUseCase.login("2026000000", PlainPassword.of("plain-password"))
        val temporary = temporaryPasswordUseCase.login("2026000000", PlainPassword.of("plain-password"))

        assertEquals(LoginResult.UserActionRequired(AuthFailure.CaptchaRequired), captcha)
        assertEquals(
            LoginResult.UserActionRequired(AuthFailure.TemporaryPasswordChangeRequired),
            temporary,
        )
    }

    @Test
    fun malformedAccountAndCredentialStorageFailureAreDistinct() = runLoginTest {
        val invalidAccount = useCase().login(" ", PlainPassword.of("plain-password"))
        val storageFailure = useCase(
            credentialStore = FakeCredentialStore(failOnSave = true),
        ).login("2026000000", PlainPassword.of("plain-password"))

        assertEquals(LoginResult.Failed(AuthFailure.InvalidCredentials), invalidAccount)
        assertEquals(LoginResult.Failed(AuthFailure.Storage), storageFailure)
    }

    @Test
    fun resumeUsesStoredCredentialWithoutEncryptingOrSavingAgain() = runLoginTest {
        val stored = StoredCredential("2026000000", SecretValue.of("encrypted"))
        val credentialStore = FakeCredentialStore().apply { credential = stored }
        var encryptionCalled = false
        var receivedCredential: StoredCredential? = null
        val useCase = useCase(
            credentialStore = credentialStore,
            encryptionApi = PasswordEncryptionApi { _, _ ->
                encryptionCalled = true
                PasswordEncryptionResult.Failure(AuthFailure.Network)
            },
            webAuthDriver = WebAuthDriver { credential ->
                receivedCredential = credential
                WebAuthResult.SessionObserved(SecretValue.of("session"))
            },
        )

        assertIs<LoginResult.Authenticated>(useCase.resume(stored))
        assertEquals(false, encryptionCalled)
        assertEquals(stored, receivedCredential)
    }

    @Test
    fun webTimeoutAndSessionStorageFailureRemainDistinct() = runLoginTest {
        val credential = StoredCredential("2026000000", SecretValue.of("encrypted"))
        val timeout = useCase(
            webAuthDriver = WebAuthDriver { WebAuthResult.Failure(AuthFailure.Timeout) },
        ).resume(credential)
        val storage = useCase(
            sessionStore = FakeSessionStore(failOnSave = true),
        ).resume(credential)

        assertEquals(LoginResult.Failed(AuthFailure.Timeout), timeout)
        assertEquals(LoginResult.Failed(AuthFailure.Storage), storage)
    }

    @Test
    fun invalidCredentialsClearOnlyEncryptedPassword() = runLoginTest {
        val stored = StoredCredential("2026000000", SecretValue.of("invalid-encrypted-password"))
        val credentialStore = FakeCredentialStore().apply { credential = stored }
        val useCase = useCase(
            credentialStore = credentialStore,
            webAuthDriver = WebAuthDriver { WebAuthResult.Failure(AuthFailure.InvalidCredentials) },
        )

        val result = useCase.resume(stored)

        assertEquals(LoginResult.Failed(AuthFailure.InvalidCredentials), result)
        assertNull(credentialStore.credential)
        assertEquals("2026000000", credentialStore.loadAccountId())
    }

    @Test
    fun invalidCredentialPasswordClearFailureIsStorageFailure() = runLoginTest {
        val stored = StoredCredential("2026000000", SecretValue.of("invalid-encrypted-password"))
        val credentialStore = FakeCredentialStore(failOnClearPassword = true).apply {
            credential = stored
        }
        val useCase = useCase(
            credentialStore = credentialStore,
            webAuthDriver = WebAuthDriver { WebAuthResult.Failure(AuthFailure.InvalidCredentials) },
        )

        assertEquals(LoginResult.Failed(AuthFailure.Storage), useCase.resume(stored))
        assertEquals(stored, credentialStore.credential)
    }

    @Test
    fun networkFailureKeepsStoredCredentialForRetry() = runLoginTest {
        val stored = StoredCredential("2026000000", SecretValue.of("encrypted-password"))
        val credentialStore = FakeCredentialStore().apply { credential = stored }
        val useCase = useCase(
            credentialStore = credentialStore,
            webAuthDriver = WebAuthDriver { WebAuthResult.Failure(AuthFailure.Network) },
        )

        assertEquals(LoginResult.Failed(AuthFailure.Network), useCase.resume(stored))
        assertEquals(stored, credentialStore.credential)
    }

    private fun useCase(
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
        sessionStore: FakeSessionStore = FakeSessionStore(),
        cookieStore: FakeCookieStore = FakeCookieStore(),
        encryptionApi: PasswordEncryptionApi = PasswordEncryptionApi {
            _, _ -> PasswordEncryptionResult.Success(SecretValue.of("encrypted-password"))
        },
        webAuthDriver: WebAuthDriver = WebAuthDriver {
            WebAuthResult.SessionObserved(SecretValue.of("session-token"))
        },
    ) = LoginUseCase(
        encryptionApi,
        credentialStore,
        webAuthDriver,
        SessionCoordinator(sessionStore, cookieStore, Clock { 10_000L }),
    )

    private class FakeCredentialStore(
        private val failOnSave: Boolean = false,
        private val failOnClearPassword: Boolean = false,
    ) : CredentialStore {
        var accountId: String? = null
        var credential: StoredCredential? = null
            set(value) {
                field = value
                if (value != null) accountId = value.accountId
            }
        override suspend fun load(): StoredCredential? = credential
        override suspend fun loadAccountId(): String? = accountId
        override suspend fun save(credential: StoredCredential) {
            if (failOnSave) error("storage failure")
            this.credential = credential
        }
        override suspend fun clearPassword() {
            if (failOnClearPassword) error("password clear failure")
            credential = null
        }
        override suspend fun clear() {
            credential = null
            accountId = null
        }
    }

    private class FakeSessionStore(
        private val failOnSave: Boolean = false,
    ) : SessionStore {
        var session: Session? = null
        override suspend fun load(): Session? = session
        override suspend fun save(session: Session) {
            if (failOnSave) error("session storage failure")
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeCookieStore : WebCookieStore {
        var token: SecretValue? = null
        override suspend fun setSessionCookie(token: SecretValue) {
            this.token = token
        }
        override suspend fun clearSessionCookie() {
            token = null
        }
    }
}

private fun <T> runLoginTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
