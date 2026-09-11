package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import platform.Foundation.NSUserDefaults

interface LibraryAccountSecretStore {
    fun readAccount(account: String): SecretValue?
    fun writeAccount(account: String, value: SecretValue)
    fun removeAccount(account: String)
}

class IosLibrarySessionCache(
    private val secrets: LibraryAccountSecretStore,
    private val defaults: NSUserDefaults,
    private val clock: Clock,
    private val policy: LibraryCachePolicy = LibraryCachePolicy(),
) : LibrarySessionCache {
    override suspend fun readSecret(identity: LibraryCacheIdentity): SecretValue? =
        readValue(secretKey(identity), policy::isSecretValid)

    override suspend fun writeSecret(identity: LibraryCacheIdentity, value: SecretValue) {
        writeValue(secretKey(identity), value, TRACKED_SECRET)
    }

    override suspend fun readAuthKey(identity: LibraryCacheIdentity): SecretValue? =
        readValue(authKey(identity), policy::isAuthKeyValid)

    override suspend fun writeAuthKey(identity: LibraryCacheIdentity, value: SecretValue) {
        writeValue(authKey(identity), value, TRACKED_AUTH)
    }

    override suspend fun clear(identity: LibraryCacheIdentity) {
        clearKeys(secretKey(identity), authKey(identity))
    }

    suspend fun clearTracked() {
        val secretAccount = defaults.stringForKey(TRACKED_SECRET)
        val authAccount = defaults.stringForKey(TRACKED_AUTH)
        val keys = listOfNotNull(secretAccount, authAccount).toTypedArray()
        if (keys.isNotEmpty()) clearKeys(*keys)
    }

    private fun readValue(
        key: String,
        isValid: (Long?, Long) -> Boolean,
    ): SecretValue? {
        val value = secrets.readAccount(key) ?: return null
        val savedAt = readTimestamp(key)
        if (!isValid(savedAt, clock.nowEpochMillis())) {
            clearKeys(key)
            return null
        }
        if (savedAt == null) {
            writeTimestamp(key, clock.nowEpochMillis())
        }
        return value
    }

    private fun writeValue(key: String, value: SecretValue, trackedKey: String) {
        secrets.writeAccount(key, value)
        writeTimestamp(key, clock.nowEpochMillis())
        defaults.setObject(key, trackedKey)
        defaults.synchronize()
    }

    private fun clearKeys(vararg keys: String) {
        keys.forEach { key ->
            runCatching { secrets.removeAccount(key) }
            defaults.removeObjectForKey(timestampKey(key))
            if (defaults.stringForKey(TRACKED_SECRET) == key) {
                defaults.removeObjectForKey(TRACKED_SECRET)
            }
            if (defaults.stringForKey(TRACKED_AUTH) == key) {
                defaults.removeObjectForKey(TRACKED_AUTH)
            }
        }
        defaults.synchronize()
    }

    private fun readTimestamp(key: String): Long? =
        defaults.stringForKey(timestampKey(key))?.toLongOrNull()

    private fun writeTimestamp(key: String, value: Long) {
        defaults.setObject(value.toString(), timestampKey(key))
        defaults.synchronize()
    }

    private fun secretKey(identity: LibraryCacheIdentity) =
        "secret_${identity.realId}_${identity.userInfoHash}"

    private fun authKey(identity: LibraryCacheIdentity) =
        "authKey_${identity.realId}_${identity.userInfoHash}"

    private fun timestampKey(valueKey: String) = "${valueKey}_savedAt"

    companion object {
        const val TRACKED_SECRET = "library_session_secret_account"
        const val TRACKED_AUTH = "library_session_auth_account"
    }
}
