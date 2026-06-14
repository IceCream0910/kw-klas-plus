package com.icecream.kwklasplus.manager

import android.content.Context
import com.icecream.kwklasplus.encryptedPreferences
import com.icecream.kwklasplus.utils.SecurityUtils

/**
 * Manages App Lock settings and authentication state.
 * All data is stored in EncryptedSharedPreferences, which uses Android KeyStore.
 */
object AppLockManager {
    private const val K_E = "a_l_e" // isAppLockEnabled
    private const val K_H = "p_w_h" // appLockPasswordHash
    private const val K_S = "p_w_s" // appLockPasswordSalt
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
        return context.encryptedPreferences.getString(K_H, null) != null
    }

    fun verifyPassword(context: Context, input: String): Boolean {
        val prefs = context.encryptedPreferences
        val savedHash = prefs.getString(K_H, null) ?: return false
        val savedSalt = prefs.getString(K_S, null) ?: return false
        return SecurityUtils.verifyPassword(input, savedHash, savedSalt)
    }

    fun savePassword(context: Context, password: String) {
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(password, salt)
        context.encryptedPreferences.edit().apply {
            putString(K_H, hash)
            putString(K_S, salt)
            apply()
        }
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.encryptedPreferences.edit().putBoolean(K_E, enabled).apply()
        if (!enabled) {
            // Reset password and biometric settings when disabling
            context.encryptedPreferences.edit().apply {
                remove(K_H)
                remove(K_S)
                putBoolean(K_B, false)
                apply()
            }
            isUnlocked = false
        }
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.encryptedPreferences.edit().putBoolean(K_B, enabled).apply()
    }
}
