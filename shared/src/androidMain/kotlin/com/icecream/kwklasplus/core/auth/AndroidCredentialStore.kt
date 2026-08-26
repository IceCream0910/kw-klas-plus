package com.icecream.kwklasplus.core.auth

import android.content.SharedPreferences
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.migration.LegacySecretSource
import com.icecream.kwklasplus.core.migration.SecureStoreMigrator
import com.icecream.kwklasplus.core.migration.androidLoginCredentialMigrations
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore

class AndroidCredentialStore(
    private val preferences: SharedPreferences,
    private val secureStore: SecureStore,
    private val migrator: SecureStoreMigrator,
    private val legacySource: LegacySecretSource,
) : CredentialStore {
    override suspend fun load(): StoredCredential? {
        migrator.migrate(androidLoginCredentialMigrations)
        val accountId = preferences.getString(LegacyPreferenceKeys.KW_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val password = secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD) ?: return null
        return StoredCredential(accountId, password)
    }

    override suspend fun loadAccountId(): String? =
        preferences.getString(LegacyPreferenceKeys.KW_ID, null)?.takeIf(String::isNotBlank)

    override suspend fun save(credential: StoredCredential) {
        val previousPassword = secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD)
        val previousAccountId = preferences.getString(LegacyPreferenceKeys.KW_ID, null)
        try {
            secureStore.write(SecureKey.ENCRYPTED_KLAS_PASSWORD, credential.encryptedPassword)
            check(
                secureStore.read(SecureKey.ENCRYPTED_KLAS_PASSWORD) == credential.encryptedPassword,
            )
            check(
                preferences.edit()
                    .putString(LegacyPreferenceKeys.KW_ID, credential.accountId)
                    .commit(),
            )
            preferences.edit().remove(LegacyPreferenceKeys.KW_PASSWORD).apply()
        } catch (cause: Throwable) {
            runCatching {
                if (previousPassword == null) {
                    secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD)
                } else {
                    secureStore.write(SecureKey.ENCRYPTED_KLAS_PASSWORD, previousPassword)
                }
            }
            preferences.edit().apply {
                if (previousAccountId == null) {
                    remove(LegacyPreferenceKeys.KW_ID)
                } else {
                    putString(LegacyPreferenceKeys.KW_ID, previousAccountId)
                }
                commit()
            }
            throw cause
        }
        androidLoginCredentialMigrations.forEach { migration ->
            runCatching { legacySource.remove(migration.source) }
        }
    }

    override suspend fun clearPassword() {
        var failure: Throwable? = null
        runCatching { secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD) }
            .onFailure { failure = it }
        if (!preferences.edit().remove(LegacyPreferenceKeys.KW_PASSWORD).commit() && failure == null) {
            failure = IllegalStateException("credential password clear failed")
        }
        androidLoginCredentialMigrations.forEach { migration ->
            runCatching { legacySource.remove(migration.source) }
                .onFailure { if (failure == null) failure = it }
        }
        failure?.let { throw it }
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { secureStore.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD) }
            .onFailure { failure = it }
        if (!preferences.edit()
                .remove(LegacyPreferenceKeys.KW_ID)
                .remove(LegacyPreferenceKeys.KW_PASSWORD)
                .commit()
        ) {
            if (failure == null) failure = IllegalStateException("credential preferences clear failed")
        }
        failure?.let { throw it }
    }
}
