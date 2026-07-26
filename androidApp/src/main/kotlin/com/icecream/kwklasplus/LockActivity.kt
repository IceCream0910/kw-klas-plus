package com.icecream.kwklasplus

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.icecream.kwklasplus.feature.lock.LockScreen
import com.icecream.kwklasplus.feature.lock.LockScreenUiState
import com.icecream.kwklasplus.manager.AppLockManager
import com.icecream.kwklasplus.platform.biometric.AndroidBiometricAvailability
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme

class LockActivity : AppCompatActivity() {
    enum class Mode {
        UNLOCK, SET, CHANGE, VERIFY
    }

    private lateinit var mode: Mode
    private val inputBuffer = StringBuilder()
    private var oldPasswordForChange: String? = null
    private var firstNewPasswordForSet: String? = null
    private var title by mutableStateOf("")
    private var description by mutableStateOf("")
    private var enteredDigits by mutableIntStateOf(0)
    private var biometricVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(MODE_EXTRA)
            ?.let { value -> Mode.entries.firstOrNull { it.name == value } }
            ?: Mode.UNLOCK

        updateModeUi()
        setContent {
            KlasPlusTheme {
                LockScreen(
                    state = LockScreenUiState(
                        title = title,
                        description = description,
                        enteredDigits = enteredDigits,
                        biometricVisible = biometricVisible,
                    ),
                    onNumberClick = { onNumberClick(it.toString()) },
                    onDeleteClick = ::onDeleteClick,
                    onBiometricClick = ::showBiometricPrompt,
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == Mode.UNLOCK) {
                    moveTaskToBack(true)
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })

        if (mode == Mode.UNLOCK && biometricVisible) {
            showBiometricPrompt()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when {
        keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            onNumberClick((keyCode - KeyEvent.KEYCODE_0).toString())
            true
        }
        keyCode == KeyEvent.KEYCODE_DEL -> {
            onDeleteClick()
            true
        }
        keyCode == KeyEvent.KEYCODE_ENTER -> {
            onConfirmClick()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun onNumberClick(value: String) {
        if (inputBuffer.length >= LockScreenUiState.PIN_LENGTH) return
        inputBuffer.append(value)
        enteredDigits = inputBuffer.length
        if (inputBuffer.length == LockScreenUiState.PIN_LENGTH) {
            onConfirmClick()
        }
    }

    private fun onDeleteClick() {
        if (inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        enteredDigits = inputBuffer.length
    }

    private fun onConfirmClick() {
        if (inputBuffer.length != LockScreenUiState.PIN_LENGTH) {
            showToast("비밀번호 6자리를 입력해주세요.")
            return
        }
        checkPassword()
    }

    private fun updateModeUi() {
        when (mode) {
            Mode.UNLOCK -> {
                title = getString(R.string.app_lock_title)
                description = "비밀번호 6자리를 입력해주세요."
                biometricVisible = AppLockManager.isBiometricEnabled(this) &&
                    AndroidBiometricAvailability.canAuthenticate(this)
            }
            Mode.SET -> {
                title = "비밀번호 설정"
                description = if (firstNewPasswordForSet == null) {
                    "새로운 비밀번호 6자리를 입력해주세요."
                } else {
                    "다시 한번 입력해주세요."
                }
                biometricVisible = false
            }
            Mode.CHANGE -> {
                title = "비밀번호 변경"
                description = when {
                    oldPasswordForChange == null -> "현재 비밀번호를 입력해주세요."
                    firstNewPasswordForSet == null -> "새로운 비밀번호 6자리를 입력해주세요."
                    else -> "다시 한번 입력해주세요."
                }
                biometricVisible = false
            }
            Mode.VERIFY -> {
                title = "비밀번호 확인"
                description = "기존 비밀번호를 입력해주세요."
                biometricVisible = false
            }
        }
        clearInput()
    }

    private fun checkPassword() {
        val input = inputBuffer.toString()
        when (mode) {
            Mode.UNLOCK -> verifyUnlock(input)
            Mode.SET -> setPassword(input)
            Mode.CHANGE -> changePassword(input)
            Mode.VERIFY -> verifyExistingPassword(input)
        }
    }

    private fun verifyUnlock(input: String) {
        if (AppLockManager.verifyPassword(this, input)) {
            unlockSuccess()
        } else {
            showToast("비밀번호가 일치하지 않습니다.")
            clearInput()
        }
    }

    private fun setPassword(input: String) {
        if (firstNewPasswordForSet == null) {
            firstNewPasswordForSet = input
            updateModeUi()
            return
        }
        if (input != firstNewPasswordForSet) {
            showToast("비밀번호가 일치하지 않습니다. 처음부터 다시 시도해주세요.")
            firstNewPasswordForSet = null
            updateModeUi()
            return
        }
        AppLockManager.savePassword(this, input)
        AppLockManager.setAppLockEnabled(this, true)
        showToast("비밀번호가 설정되었습니다.")
        completePasswordUpdate()
    }

    private fun changePassword(input: String) {
        if (oldPasswordForChange == null) {
            if (AppLockManager.verifyPassword(this, input)) {
                oldPasswordForChange = input
                updateModeUi()
            } else {
                showToast("현재 비밀번호가 일치하지 않습니다.")
                clearInput()
            }
            return
        }
        if (firstNewPasswordForSet == null) {
            firstNewPasswordForSet = input
            updateModeUi()
            return
        }
        if (input != firstNewPasswordForSet) {
            showToast("비밀번호가 일치하지 않습니다. 새로운 비밀번호부터 다시 입력해주세요.")
            firstNewPasswordForSet = null
            updateModeUi()
            return
        }
        AppLockManager.savePassword(this, input)
        showToast("비밀번호가 변경되었습니다.")
        completePasswordUpdate()
    }

    private fun verifyExistingPassword(input: String) {
        if (AppLockManager.verifyPassword(this, input)) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            showToast("비밀번호가 일치하지 않습니다.")
            clearInput()
        }
    }

    private fun completePasswordUpdate() {
        if (AndroidBiometricAvailability.canAuthenticate(this)) {
            showBiometricPromptForEnabling()
        } else {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun clearInput() {
        inputBuffer.clear()
        enteredDigits = 0
    }

    private fun showBiometricPrompt() {
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(result)
                    unlockSuccess()
                }
            },
        )
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_lock_biometric_prompt_title))
                .setNegativeButtonText("비밀번호로 인증")
                .build(),
        )
    }

    private fun showBiometricPromptForEnabling() {
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(result)
                    AppLockManager.setBiometricEnabled(this@LockActivity, true)
                    showToast("생체인증이 활성화되었습니다.")
                    setResult(Activity.RESULT_OK)
                    finish()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            },
        )
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("생체인증 활성화")
                .setSubtitle("생체인증을 사용하려면 인증이 필요합니다.")
                .setNegativeButtonText("나중에 설정")
                .build(),
        )
    }

    private fun unlockSuccess() {
        AppLockManager.isUnlocked = true
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MODE_EXTRA = "MODE"
    }
}
