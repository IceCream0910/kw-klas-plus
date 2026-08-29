package com.icecream.kwklasplus.core.lock

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.IosKeychainSecureStore
import com.icecream.kwklasplus.core.security.SecretValue

class IosAppLockSecretStore(
    private val secureStore: IosKeychainSecureStore,
) {
    fun readHash(): String? = secureStore.readNow(SecureKey.APP_LOCK_HASH)?.reveal()

    fun readSalt(): String? = secureStore.readNow(SecureKey.APP_LOCK_SALT)?.reveal()

    fun write(hash: String, salt: String) {
        val previousHash = secureStore.readNow(SecureKey.APP_LOCK_HASH)
        val previousSalt = secureStore.readNow(SecureKey.APP_LOCK_SALT)
        secureStore.writeNow(SecureKey.APP_LOCK_HASH, SecretValue.of(hash))
        try {
            secureStore.writeNow(SecureKey.APP_LOCK_SALT, SecretValue.of(salt))
            check(readHash() == hash && readSalt() == salt)
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
        failure?.let { throw it }
    }

    private fun restore(key: SecureKey, value: SecretValue?) {
        runCatching {
            if (value == null) secureStore.removeNow(key) else secureStore.writeNow(key, value)
        }
    }
}
