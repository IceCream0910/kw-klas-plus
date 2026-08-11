package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import platform.Foundation.NSUserDefaults

class IosCredentialStore(
    private val secureStore: SecureStore,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : CredentialStore {
    override suspend fun load(): StoredCredential? {
        val accountId = defaults.stringForKey(LegacyPreferenceKeys.KW_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val password = secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD) ?: return null
        return StoredCredential(accountId, password)
    }

    override suspend fun save(credential: StoredCredential) {
        val previousPassword = secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD)
        val previousAccountId = defaults.stringForKey(LegacyPreferenceKeys.KW_ID)
        try {
            secureStore.write(SecureKey.ENCRYPTED_KLAS_PASSWORD, credential.encryptedPassword)
            check(
                secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD) == credential.encryptedPassword,
            )
            defaults.setObject(credential.accountId, LegacyPreferenceKeys.KW_ID)
            defaults.removeObjectForKey(LegacyPreferenceKeys.KW_PASSWORD)
            check(defaults.synchronize()) {
                "Failed to persist ${LegacyPreferenceKeys.KW_ID}"
            }
        } catch (cause: Throwable) {
            runCatching {
                if (previousPassword == null) {
                    secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD)
                } else {
                    secureStore.write(SecureKey.ENCRYPTED_KLAS_PASSWORD, previousPassword)
                }
            }
            if (previousAccountId == null) {
                defaults.removeObjectForKey(LegacyPreferenceKeys.KW_ID)
            } else {
                defaults.setObject(previousAccountId, LegacyPreferenceKeys.KW_ID)
            }
            defaults.synchronize()
            throw cause
        }
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD) }
            .onFailure { failure = it }
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_ID)
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_PASSWORD)
        if (!defaults.synchronize() && failure == null) {
            failure = IllegalStateException("credential preferences clear failed")
        }
        failure?.let { throw it }
    }
}
