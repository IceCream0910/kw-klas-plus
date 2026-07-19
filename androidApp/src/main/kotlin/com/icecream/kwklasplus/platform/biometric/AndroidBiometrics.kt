package com.icecream.kwklasplus.platform.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.icecream.kwklasplus.core.platform.BiometricPurpose
import com.icecream.kwklasplus.core.platform.Biometrics
import com.icecream.kwklasplus.core.platform.PlatformActionResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidBiometrics(
    private val activity: FragmentActivity,
) : Biometrics {
    override suspend fun authenticate(purpose: BiometricPurpose): PlatformActionResult {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> return PlatformActionResult.PermissionRequired
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            -> return PlatformActionResult.Unsupported
            else -> return PlatformActionResult.Failed("biometric_unavailable")
        }

        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(PlatformActionResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            -> PlatformActionResult.Cancelled
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> PlatformActionResult.PermissionRequired
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            -> PlatformActionResult.Unsupported
                            else -> PlatformActionResult.Failed("biometric_authentication_failed")
                        }
                        continuation.resume(result)
                    }
                },
            )
            val title = when (purpose) {
                BiometricPurpose.UNLOCK_APP -> "앱 잠금 해제"
                BiometricPurpose.ENABLE_BIOMETRICS -> "생체인증 사용"
                BiometricPurpose.DISABLE_APP_LOCK -> "앱 잠금 해제"
            }
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText("취소")
                    .build(),
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
