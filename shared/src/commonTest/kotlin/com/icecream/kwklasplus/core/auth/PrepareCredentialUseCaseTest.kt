package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PrepareCredentialUseCaseTest {
    @Test
    fun encryptsAndPersistsCredential() = runPrepareCredentialTest {
        val store = FakeCredentialStore()
        val encrypted = SecretValue.of("encrypted")
        val useCase = PrepareCredentialUseCase(
            PasswordEncryptionApi { _, _ -> PasswordEncryptionResult.Success(encrypted) },
            store,
        )

        val result = useCase.prepare("2026000000", PlainPassword.of("plain"))

        val credential = assertIs<CredentialPreparationResult.Success>(result).credential
        assertEquals(StoredCredential("2026000000", encrypted), credential)
        assertEquals(credential, store.credential)
    }

    @Test
    fun encryptionFailureDoesNotWriteCredential() = runPrepareCredentialTest {
        val store = FakeCredentialStore()
        val useCase = PrepareCredentialUseCase(
            PasswordEncryptionApi { _, _ -> PasswordEncryptionResult.Failure(AuthFailure.Timeout) },
            store,
        )

        assertEquals(
            CredentialPreparationResult.Failure(AuthFailure.Timeout),
            useCase.prepare("2026000000", PlainPassword.of("plain")),
        )
        assertNull(store.credential)
    }

    @Test
    fun storageFailureIsDistinct() = runPrepareCredentialTest {
        val useCase = PrepareCredentialUseCase(
            PasswordEncryptionApi {
                _, _ -> PasswordEncryptionResult.Success(SecretValue.of("encrypted"))
            },
            FakeCredentialStore(failSave = true),
        )

        assertEquals(
            CredentialPreparationResult.Failure(AuthFailure.Storage),
            useCase.prepare("2026000000", PlainPassword.of("plain")),
        )
    }

    private class FakeCredentialStore(
        private val failSave: Boolean = false,
    ) : CredentialStore {
        var credential: StoredCredential? = null
        override suspend fun load() = credential
        override suspend fun loadAccountId(): String? = credential?.accountId
        override suspend fun save(credential: StoredCredential) {
            if (failSave) error("save failed")
            this.credential = credential
        }
        override suspend fun clearPassword() {
            credential = null
        }
        override suspend fun clear() {
            credential = null
        }
    }
}

private fun <T> runPrepareCredentialTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
