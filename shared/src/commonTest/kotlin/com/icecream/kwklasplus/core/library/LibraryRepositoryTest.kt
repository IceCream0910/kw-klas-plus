package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LibraryRepositoryTest {
    private val credentials = LibraryCredentials("2020123456", "01012345678", SecretValue.of("password"))

    @Test
    fun performsSecretLoginAndQrWorkflowWithLegacyRequestValues() = runBlocking {
        val gateway = FakeGateway()
        val cache = FakeCache()
        val repository = LibraryRepository(gateway, FakeCodec(), cache)

        val result = repository.getQrData(credentials)

        assertIs<LibraryQrResult.Success>(result)
        assertEquals(listOf("secret", "login:A", "qr"), gateway.calls)
        assertEquals("enc:02020123456", gateway.secretRealId)
        assertEquals("enc:2020123456", gateway.loginStudentNumber)
        assertEquals("encrypted:password:secret", gateway.encryptedPassword)
    }

    @Test
    fun reusesCachedSecretAndAuthKey() = runBlocking {
        val gateway = FakeGateway()
        val cache = FakeCache().apply {
            secret = SecretValue.of("cached-secret")
            authKey = SecretValue.of("cached-auth")
        }

        val result = LibraryRepository(gateway, FakeCodec(), cache).getQrData(credentials)

        assertIs<LibraryQrResult.Success>(result)
        assertEquals(listOf("qr"), gateway.calls)
    }

    @Test
    fun clearsBothCachedValuesWhenQrRequestFails() = runBlocking {
        val gateway = FakeGateway(qrResult = LibraryGatewayResult.NetworkFailure)
        val cache = FakeCache().apply {
            secret = SecretValue.of("cached-secret")
            authKey = SecretValue.of("cached-auth")
        }

        val result = LibraryRepository(gateway, FakeCodec(), cache).getQrData(credentials)

        assertEquals(LibraryQrResult.NetworkFailure, result)
        assertEquals(1, cache.clearCount)
        assertEquals(null, cache.secret)
        assertEquals(null, cache.authKey)
    }

    @Test
    fun identityMatchesLegacyJavaHashCode() {
        assertEquals(
            LibraryCacheIdentity("02020123456", "-1973407057"),
            LibraryCacheIdentity.from(credentials),
        )
    }

    @Test
    fun doesNotConvertCancellationIntoEncryptionFailure() {
        val cache = FakeCache().apply { secret = SecretValue.of("secret") }
        val codec = object : LibraryCredentialCodec {
            override fun encode(value: String) = value

            override fun encryptPassword(password: SecretValue, secret: SecretValue): String {
                throw CancellationException("cancelled")
            }
        }

        assertFailsWith<CancellationException> {
            runBlocking { LibraryRepository(FakeGateway(), codec, cache).getQrData(credentials) }
        }
    }

    private class FakeCodec : LibraryCredentialCodec {
        override fun encode(value: String) = "enc:$value"
        override fun encryptPassword(password: SecretValue, secret: SecretValue) =
            "encrypted:${password.reveal()}:${secret.reveal()}"
    }

    private class FakeGateway(
        private val qrResult: LibraryGatewayResult<LibraryQrData> =
            LibraryGatewayResult.Success(LibraryQrData(mapOf("qr" to "value"))),
    ) : LibraryGateway {
        val calls = mutableListOf<String>()
        var secretRealId = ""
        var loginStudentNumber = ""
        var encryptedPassword = ""

        override suspend fun requestSecret(encodedRealId: String): LibraryGatewayResult<String> {
            calls += "secret"
            secretRealId = encodedRealId
            return LibraryGatewayResult.Success("secret")
        }

        override suspend fun login(
            encodedRealId: String,
            encodedStudentNumber: String,
            phoneNumber: String,
            encryptedPassword: String,
            deviceCode: String,
        ): LibraryGatewayResult<String> {
            calls += "login:$deviceCode"
            loginStudentNumber = encodedStudentNumber
            this.encryptedPassword = encryptedPassword
            return LibraryGatewayResult.Success("auth")
        }

        override suspend fun requestQr(
            encodedRealId: String,
            authKey: String,
        ): LibraryGatewayResult<LibraryQrData> {
            calls += "qr"
            return qrResult
        }
    }

    private class FakeCache : LibrarySessionCache {
        var secret: SecretValue? = null
        var authKey: SecretValue? = null
        var clearCount = 0

        override suspend fun readSecret(identity: LibraryCacheIdentity) = secret
        override suspend fun writeSecret(identity: LibraryCacheIdentity, value: SecretValue) {
            secret = value
        }
        override suspend fun readAuthKey(identity: LibraryCacheIdentity) = authKey
        override suspend fun writeAuthKey(identity: LibraryCacheIdentity, value: SecretValue) {
            authKey = value
        }
        override suspend fun clear(identity: LibraryCacheIdentity) {
            clearCount++
            secret = null
            authKey = null
        }
    }
}
