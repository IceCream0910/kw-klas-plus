package com.icecream.kwklasplus.manager

import android.content.Context
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.encryptedPreferences

object AppLockManager {
    private const val K_E = "a_l_e" // isAppLockEnabled
    private const val K_B = "b_m_e" // isBiometricEnabled

    @Volatile
    var isUnlocked: Boolean = false

    fun isAppLockEnabled(context: Context): Boolean {
        return context.encryptedPreferences.getBoolean(K_E, false)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return context.encryptedPreferences.getBoolean(K_B, false)
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
        context.encryptedPreferences.edit().putBoolean(K_E, enabled).apply()
        if (!enabled) {
            // 잠금 비활성화 시 데이터 초기화
            context.appDependencies.appLockSecretStore.clear()
            context.encryptedPreferences.edit().putBoolean(K_B, false).apply()
            isUnlocked = false
        }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.encryptedPreferences.edit().putBoolean(K_B, enabled).apply()
    }
}
