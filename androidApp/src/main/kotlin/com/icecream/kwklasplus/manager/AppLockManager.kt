package com.icecream.kwklasplus.manager

import android.content.Context
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.encryptedPreferences

object AppLockManager {
    private const val APP_LOCK_ENABLED_KEY = "a_l_e"
    private const val BIOMETRIC_ENABLED_KEY = "b_m_e"

    @Volatile
    var isUnlocked: Boolean = false

    fun isAppLockEnabled(context: Context): Boolean {
        return context.encryptedPreferences.getBoolean(APP_LOCK_ENABLED_KEY, false)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return context.encryptedPreferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    fun hasPassword(context: Context): Boolean {
        return context.appDependencies.appLockSecretStore.readHash() != null
    }

    fun verifyPassword(context: Context, input: String): Boolean {
        val secrets = context.appDependencies.appLockSecretStore
        val savedHash = secrets.readHash() ?: return false
        val savedSalt = secrets.readSalt() ?: return false
        return context.appDependencies.appLockCredentialCodec.verify(input, savedHash, savedSalt)
    }

    fun savePassword(context: Context, password: String) {
        val codec = context.appDependencies.appLockCredentialCodec
        val salt = codec.generateSalt()
        val hash = codec.hash(password, salt)
        context.appDependencies.appLockSecretStore.write(hash, salt)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.encryptedPreferences.edit().putBoolean(APP_LOCK_ENABLED_KEY, enabled).apply()
        if (!enabled) {
            context.appDependencies.appLockSecretStore.clear()
            context.encryptedPreferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, false).apply()
            isUnlocked = false
        }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.encryptedPreferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }
}
