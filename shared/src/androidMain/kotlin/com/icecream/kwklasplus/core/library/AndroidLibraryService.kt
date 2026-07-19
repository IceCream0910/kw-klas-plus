package com.icecream.kwklasplus.core.library

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.CancellationException

class AndroidLibraryService(
    gateway: LibraryGateway,
    cachePreferences: SharedPreferences,
    encryptedCachePreferences: SharedPreferences,
    clock: Clock = Clock(System::currentTimeMillis),
) {
    private val repository = LibraryRepository(
        gateway = gateway,
        codec = AndroidLibraryCredentialCodec(),
        cache = AndroidLibrarySessionCache(
            cachePreferences,
            encryptedCachePreferences,
            clock,
        ),
    )

    suspend fun getLibraryQrData(
        studentNumber: String,
        phoneNumber: String,
        password: String,
    ): LibraryQrResult = try {
        repository.getQrData(
            LibraryCredentials(studentNumber, phoneNumber, SecretValue.of(password)),
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        LibraryQrResult.InvalidResponse
    }

    suspend fun clearCache(studentNumber: String, phoneNumber: String, password: String) {
        repository.clear(
            LibraryCredentials(studentNumber, phoneNumber, SecretValue.of(password)),
        )
    }
}

internal class AndroidLibrarySessionCache(
    private val preferences: SharedPreferences,
    private val encryptedPreferences: SharedPreferences,
    private val clock: Clock,
    private val policy: LibraryCachePolicy = LibraryCachePolicy(),
) : LibrarySessionCache {
    override suspend fun readSecret(identity: LibraryCacheIdentity): SecretValue? =
        readValue(secretKey(identity), policy::isSecretValid)

    override suspend fun writeSecret(identity: LibraryCacheIdentity, value: SecretValue) {
        writeValue(secretKey(identity), value)
    }

    override suspend fun readAuthKey(identity: LibraryCacheIdentity): SecretValue? =
        readValue(authKey(identity), policy::isAuthKeyValid)

    override suspend fun writeAuthKey(identity: LibraryCacheIdentity, value: SecretValue) {
        writeValue(authKey(identity), value)
    }

    override suspend fun clear(identity: LibraryCacheIdentity) {
        clearKeys(secretKey(identity), authKey(identity))
    }

    private fun readValue(
        key: String,
        isValid: (Long?, Long) -> Boolean,
    ): SecretValue? {
        var value = encryptedPreferences.getString(key, null)
        if (value == null) {
            value = preferences.getString(key, null)
            if (value != null) writeValue(key, SecretValue.of(value))
        }
        if (value == null) return null

        val savedAt = encryptedPreferences
            .takeIf { it.contains(timestampKey(key)) }
            ?.getLong(timestampKey(key), -1)
        if (!isValid(savedAt, clock.nowEpochMillis())) {
            clearKeys(key)
            return null
        }
        if (savedAt == null) {
            encryptedPreferences.edit()
                .putLong(timestampKey(key), clock.nowEpochMillis())
                .apply()
        }
        return SecretValue.of(value)
    }

    private fun writeValue(key: String, value: SecretValue) {
        encryptedPreferences.edit()
            .putString(key, value.reveal())
            .putLong(timestampKey(key), clock.nowEpochMillis())
            .apply()
        preferences.edit().remove(key).remove(timestampKey(key)).apply()
    }

    private fun clearKeys(vararg keys: String) {
        encryptedPreferences.edit().apply {
            keys.forEach { key -> remove(key).remove(timestampKey(key)) }
            apply()
        }
        preferences.edit().apply {
            keys.forEach { key -> remove(key).remove(timestampKey(key)) }
            apply()
        }
    }

    private fun secretKey(identity: LibraryCacheIdentity) =
        "secret_${identity.realId}_${identity.userInfoHash}"

    private fun authKey(identity: LibraryCacheIdentity) =
        "authKey_${identity.realId}_${identity.userInfoHash}"

    private fun timestampKey(valueKey: String) = "${valueKey}_savedAt"
}
