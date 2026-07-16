package com.icecream.kwklasplus.utils

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.security.MessageDigest
import java.security.SecureRandom
import android.content.Context

object SecurityUtils {
    private const val ALGORITHM = "SHA-256"

    fun canUseBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricErrorMessage(context: Context): String? {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "이 기기는 생체 인증을 지원하지 않어요."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "현재 생체 인증 센서를 사용할 수 없어요."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "등록된 생체 정보가 없습니다. 기기 설정에서 생체 정보를 등록해주세요."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "보안 업데이트가 필요하여 생체 인증을 사용할 수 없어요."
            else -> "현재 생체 인증을 사용할 수 없어요."
        }
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val combined = password + salt
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(input: String, savedHash: String, savedSalt: String): Boolean {
        val hashOfInput = hashPassword(input, savedSalt)
        return hashOfInput == savedHash
    }
}
