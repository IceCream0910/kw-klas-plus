package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.CancellationException

data class LibraryCredentials(
    val studentNumber: String,
    val phoneNumber: String,
    val password: SecretValue,
)

data class LibraryCacheIdentity(
    val realId: String,
    val userInfoHash: String,
) {
    companion object {
        fun from(credentials: LibraryCredentials): LibraryCacheIdentity {
            val source = credentials.studentNumber + credentials.phoneNumber + credentials.password.reveal()
            return LibraryCacheIdentity(
                realId = "0${credentials.studentNumber}",
                userInfoHash = source.legacyJavaStringHashCode().toString(),
            )
        }
    }
}

data class LibraryQrData(
    val values: Map<String, String>,
)

sealed interface LibraryGatewayResult<out T> {
    data class Success<T>(val value: T) : LibraryGatewayResult<T>
    data object NetworkFailure : LibraryGatewayResult<Nothing>
    data object InvalidResponse : LibraryGatewayResult<Nothing>
    data object AuthenticationFailure : LibraryGatewayResult<Nothing>
}

interface LibraryGateway {
    suspend fun requestSecret(encodedRealId: String): LibraryGatewayResult<String>

    suspend fun login(
        encodedRealId: String,
        encodedStudentNumber: String,
        phoneNumber: String,
        encryptedPassword: String,
        deviceCode: String,
    ): LibraryGatewayResult<String>

    suspend fun requestQr(
        encodedRealId: String,
        authKey: String,
    ): LibraryGatewayResult<LibraryQrData>
}

interface LibraryCredentialCodec {
    fun encode(value: String): String
    fun encryptPassword(password: SecretValue, secret: SecretValue): String
}

interface LibrarySessionCache {
    suspend fun readSecret(identity: LibraryCacheIdentity): SecretValue?
    suspend fun writeSecret(identity: LibraryCacheIdentity, value: SecretValue)
    suspend fun readAuthKey(identity: LibraryCacheIdentity): SecretValue?
    suspend fun writeAuthKey(identity: LibraryCacheIdentity, value: SecretValue)
    suspend fun clear(identity: LibraryCacheIdentity)
}

sealed interface LibraryQrResult {
    data class Success(val data: LibraryQrData) : LibraryQrResult
    data object NetworkFailure : LibraryQrResult
    data object InvalidResponse : LibraryQrResult
    data object AuthenticationFailure : LibraryQrResult
    data object EncryptionFailure : LibraryQrResult
}

class LibraryRepository(
    private val gateway: LibraryGateway,
    private val codec: LibraryCredentialCodec,
    private val cache: LibrarySessionCache,
    private val deviceCode: String = "A",
) {
    suspend fun getQrData(credentials: LibraryCredentials): LibraryQrResult {
        val identity = LibraryCacheIdentity.from(credentials)
        val secret = cache.readSecret(identity) ?: when (
            val result = gateway.requestSecret(codec.encode(identity.realId))
        ) {
            is LibraryGatewayResult.Success -> SecretValue.of(result.value).also {
                cache.writeSecret(identity, it)
            }
            else -> return fail(identity, result)
        }

        val authKey = cache.readAuthKey(identity) ?: run {
            val encryptedPassword = try {
                codec.encryptPassword(credentials.password, secret)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                cache.clear(identity)
                return LibraryQrResult.EncryptionFailure
            }
            when (
                val result = gateway.login(
                    encodedRealId = codec.encode(identity.realId),
                    encodedStudentNumber = codec.encode(credentials.studentNumber),
                    phoneNumber = credentials.phoneNumber,
                    encryptedPassword = encryptedPassword,
                    deviceCode = deviceCode,
                )
            ) {
                is LibraryGatewayResult.Success -> SecretValue.of(result.value).also {
                    cache.writeAuthKey(identity, it)
                }
                else -> return fail(identity, result)
            }
        }

        return when (val result = gateway.requestQr(codec.encode(identity.realId), authKey.reveal())) {
            is LibraryGatewayResult.Success -> LibraryQrResult.Success(result.value)
            else -> fail(identity, result)
        }
    }

    suspend fun clear(credentials: LibraryCredentials) {
        cache.clear(LibraryCacheIdentity.from(credentials))
    }

    private suspend fun fail(
        identity: LibraryCacheIdentity,
        result: LibraryGatewayResult<*>,
    ): LibraryQrResult {
        cache.clear(identity)
        return when (result) {
            LibraryGatewayResult.NetworkFailure -> LibraryQrResult.NetworkFailure
            LibraryGatewayResult.InvalidResponse -> LibraryQrResult.InvalidResponse
            LibraryGatewayResult.AuthenticationFailure -> LibraryQrResult.AuthenticationFailure
            is LibraryGatewayResult.Success -> LibraryQrResult.InvalidResponse
        }
    }
}

internal fun String.legacyJavaStringHashCode(): Int {
    var hash = 0
    forEach { character -> hash = 31 * hash + character.code }
    return hash
}
