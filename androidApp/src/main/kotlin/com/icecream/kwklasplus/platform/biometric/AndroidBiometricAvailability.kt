package com.icecream.kwklasplus.platform.biometric

import android.content.Context
import androidx.biometric.BiometricManager

object AndroidBiometricAvailability {
    fun canAuthenticate(context: Context): Boolean =
        authenticationStatus(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun errorMessage(context: Context): String? = when (authenticationStatus(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "이 기기는 생체 인증을 지원하지 않아요."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "현재 생체 인증 센서를 사용할 수 없어요."
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "등록된 생체 정보가 없습니다. 기기 설정에서 생체 정보를 등록해주세요."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "보안 업데이트가 필요하여 생체 인증을 사용할 수 없어요."
        else -> "현재 생체 인증을 사용할 수 없어요."
    }

    private fun authenticationStatus(context: Context): Int = BiometricManager.from(context)
        .canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
        )
}
