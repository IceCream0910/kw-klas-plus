package com.icecream.kwklasplus.core.lock

import platform.Foundation.NSUserDefaults

class IosAppLockStore(
    private val defaults: NSUserDefaults,
    private val secretStore: IosAppLockSecretStore,
    private val codec: AppLockCredentialCodec,
) {
    var isUnlocked: Boolean = false

    fun isEnabled(): Boolean = defaults.boolForKey(ENABLED_KEY)

    fun isBiometricEnabled(): Boolean = defaults.boolForKey(BIOMETRIC_KEY)

    fun hasPassword(): Boolean = secretStore.readHash() != null

    fun verifyPassword(input: String): Boolean {
        val savedHash = secretStore.readHash() ?: return false
        val savedSalt = secretStore.readSalt() ?: return false
        return codec.verify(input, savedHash, savedSalt)
    }

    fun savePassword(password: String) {
        val salt = codec.generateSalt()
        val hash = codec.hash(password, salt)
        secretStore.write(hash, salt)
    }

    fun setEnabled(enabled: Boolean) {
        defaults.setBool(enabled, ENABLED_KEY)
        defaults.synchronize()
        if (!enabled) {
            secretStore.clear()
            defaults.setBool(false, BIOMETRIC_KEY)
            defaults.synchronize()
            isUnlocked = false
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        defaults.setBool(enabled, BIOMETRIC_KEY)
        defaults.synchronize()
    }

    fun currentSettings(): AppLockSettings = AppLockSettings(
        enabled = isEnabled(),
        biometricEnabled = isBiometricEnabled(),
        hasPassword = hasPassword(),
    )

    fun currentState(): AppLockState = AppLockState(
        enabled = isEnabled(),
        unlocked = isUnlocked,
    )

    private companion object {
        const val ENABLED_KEY = "a_l_e"
        const val BIOMETRIC_KEY = "b_m_e"
    }
}
