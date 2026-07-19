package com.icecream.kwklasplus.core.lock

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.AndroidKeystoreSecureStore
import com.icecream.kwklasplus.core.security.SecretValue

class AndroidAppLockSecretStore(
    private val secureStore: AndroidKeystoreSecureStore,
    private val legacyPreferences: SharedPreferences,
) {

    fun readHash(): String? = read(SecureKey.APP_LOCK_HASH, HASH_KEY)

    fun readSalt(): String? = read(SecureKey.APP_LOCK_SALT, SALT_KEY)

    fun write(hash: String, salt: String) {
        val previousHash = secureStore.readNow(SecureKey.APP_LOCK_HASH)
        val previousSalt = secureStore.readNow(SecureKey.APP_LOCK_SALT)
        secureStore.writeNow(SecureKey.APP_LOCK_HASH, SecretValue.of(hash))
        try {
            secureStore.writeNow(SecureKey.APP_LOCK_SALT, SecretValue.of(salt))
            check(readHash() == hash && readSalt() == salt)
            legacyPreferences.edit().remove(HASH_KEY).remove(SALT_KEY).apply()
        } catch (cause: Throwable) {
            restore(SecureKey.APP_LOCK_HASH, previousHash)
            restore(SecureKey.APP_LOCK_SALT, previousSalt)
            throw cause
        }
    }

    fun clear() {
        var failure: Throwable? = null
        runCatching { secureStore.removeNow(SecureKey.APP_LOCK_HASH) }
            .onFailure { failure = it }
        runCatching { secureStore.removeNow(SecureKey.APP_LOCK_SALT) }
            .onFailure { if (failure == null) failure = it }
        legacyPreferences.edit().remove(HASH_KEY).remove(SALT_KEY).apply()
        failure?.let { throw it }
    }

    private fun read(key: SecureKey, legacyKey: String): String? {
        secureStore.readNow(key)?.let { return it.reveal() }
        val legacyValue = legacyPreferences.getString(legacyKey, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            secureStore.writeNow(key, SecretValue.of(legacyValue))
            check(secureStore.readNow(key)?.reveal() == legacyValue)
            legacyPreferences.edit().remove(legacyKey).apply()
            legacyValue
        }.getOrElse { legacyValue }
    }

    private fun restore(key: SecureKey, value: SecretValue?) {
        runCatching {
            if (value == null) secureStore.removeNow(key) else secureStore.writeNow(key, value)
        }
    }

    private companion object {
        const val HASH_KEY = "p_w_h"
        const val SALT_KEY = "p_w_s"
    }
}
