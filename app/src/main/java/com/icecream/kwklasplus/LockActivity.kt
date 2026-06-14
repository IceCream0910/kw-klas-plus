package com.icecream.kwklasplus

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.icecream.kwklasplus.manager.AppLockManager
import com.icecream.kwklasplus.utils.SecurityUtils

class LockActivity : AppCompatActivity() {

    enum class Mode {
        UNLOCK, SET, CHANGE, VERIFY
    }

    private lateinit var mode: Mode
    private var inputBuffer = StringBuilder()
    private var oldPasswordForChange: String? = null
    private var firstNewPasswordForSet: String? = null
    
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var indicators: List<View>
    private lateinit var btnBiometric: MaterialButton
    private lateinit var btnOK: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        applyEdgeToEdgeInsets()

        mode = intent.getStringExtra("MODE")?.let { Mode.valueOf(it) } ?: Mode.UNLOCK

        tvTitle = findViewById(R.id.tvTitle)
        tvDescription = findViewById(R.id.tvDescription)
        indicators = listOf(
            findViewById(R.id.indicator1), findViewById(R.id.indicator2),
            findViewById(R.id.indicator3), findViewById(R.id.indicator4),
            findViewById(R.id.indicator5), findViewById(R.id.indicator6)
        )
        btnBiometric = findViewById(R.id.btnBiometric)
        btnOK = findViewById(R.id.btnOK)

        setupKeypad()
        updateModeUI()

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

        if (mode == Mode.UNLOCK && AppLockManager.isBiometricEnabled(this)) {
            showBiometricPrompt()
        }
    }

    private fun setupKeypad() {
        val buttons = listOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        buttons.forEach { (id, value) ->
            findViewById<MaterialButton>(id).setOnClickListener { onNumberClick(value) }
        }

        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener { onDeleteClick() }
        btnOK.setOnClickListener { onOKClick() }
        
        btnBiometric.setOnClickListener { showBiometricPrompt() }
    }

    private fun onNumberClick(value: String) {
        if (inputBuffer.length < 6) {
            inputBuffer.append(value)
            updateIndicators()
            
            // Strictly 6 digits auto-submit
            if (inputBuffer.length == 6) {
                onOKClick()
            }
        }
    }

    private fun onDeleteClick() {
        if (inputBuffer.isNotEmpty()) {
            inputBuffer.deleteCharAt(inputBuffer.length - 1)
            updateIndicators()
        }
    }

    private fun onOKClick() {
        if (inputBuffer.length != 6) {
            Toast.makeText(this, "비밀번호 6자리를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        checkPassword()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            onNumberClick((keyCode - KeyEvent.KEYCODE_0).toString())
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            onDeleteClick()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            onOKClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateIndicators() {
        indicators.forEachIndexed { index, view ->
            if (index < inputBuffer.length) {
                view.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.md_theme_primary))
            } else {
                view.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.md_theme_outlineVariant))
            }
        }
        btnOK.isEnabled = inputBuffer.length == 6
    }

    private fun updateModeUI() {
        when (mode) {
            Mode.UNLOCK -> {
                tvTitle.text = getString(R.string.app_lock_title)
                tvDescription.text = "비밀번호 6자리를 입력해주세요."
                btnBiometric.visibility = if (AppLockManager.isBiometricEnabled(this)) View.VISIBLE else View.GONE
            }
            Mode.SET -> {
                tvTitle.text = "비밀번호 설정"
                tvDescription.text = firstNewPasswordForSet?.let { "다시 한번 입력해주세요." } ?: "새로운 비밀번호 6자리를 입력해주세요."
                btnBiometric.visibility = View.GONE
            }
            Mode.CHANGE -> {
                tvTitle.text = "비밀번호 변경"
                tvDescription.text = when {
                    oldPasswordForChange == null -> "현재 비밀번호를 입력해주세요."
                    firstNewPasswordForSet == null -> "새로운 비밀번호 6자리를 입력해주세요."
                    else -> "다시 한번 입력해주세요."
                }
                btnBiometric.visibility = View.GONE
            }
            Mode.VERIFY -> {
                tvTitle.text = "비밀번호 확인"
                tvDescription.text = "기존 비밀번호를 입력해주세요."
                btnBiometric.visibility = View.GONE
            }
        }
        inputBuffer.clear()
        updateIndicators()
    }

    private fun checkPassword() {
        val input = inputBuffer.toString()

        when (mode) {
            Mode.UNLOCK -> {
                if (AppLockManager.verifyPassword(this, input)) {
                    unlockSuccess()
                } else {
                    Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    inputBuffer.clear()
                    updateIndicators()
                }
            }
            Mode.SET -> {
                if (firstNewPasswordForSet == null) {
                    firstNewPasswordForSet = input
                    updateModeUI()
                } else {
                    if (input == firstNewPasswordForSet) {
                        AppLockManager.savePassword(this, input)
                        AppLockManager.setAppLockEnabled(this, true)
                        Toast.makeText(this, "비밀번호가 설정되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        if (SecurityUtils.canUseBiometric(this)) {
                            showBiometricPromptForEnabling()
                        } else {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "비밀번호가 일치하지 않습니다. 처음부터 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                        firstNewPasswordForSet = null
                        updateModeUI()
                    }
                }
            }
            Mode.CHANGE -> {
                if (oldPasswordForChange == null) {
                    if (AppLockManager.verifyPassword(this, input)) {
                        oldPasswordForChange = input
                        updateModeUI()
                    } else {
                        Toast.makeText(this, "현재 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                        inputBuffer.clear()
                        updateIndicators()
                    }
                } else if (firstNewPasswordForSet == null) {
                    firstNewPasswordForSet = input
                    updateModeUI()
                } else {
                    if (input == firstNewPasswordForSet) {
                        AppLockManager.savePassword(this, input)
                        Toast.makeText(this, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        if (SecurityUtils.canUseBiometric(this)) {
                            showBiometricPromptForEnabling()
                        } else {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "비밀번호가 일치하지 않습니다. 새로운 비밀번호부터 다시 입력해주세요.", Toast.LENGTH_SHORT).show()
                        firstNewPasswordForSet = null
                        updateModeUI()
                    }
                }
            }
            Mode.VERIFY -> {
                if (AppLockManager.verifyPassword(this, input)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    inputBuffer.clear()
                    updateIndicators()
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_biometric_prompt_title))
            .setNegativeButtonText("비밀번호로 인증")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showBiometricPromptForEnabling() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLockManager.setBiometricEnabled(this@LockActivity, true)
                    Toast.makeText(this@LockActivity, "생체인증이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or fails, biometric remains disabled but password setup is finished
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("생체인증 활성화")
            .setSubtitle("생체인증을 사용하려면 인증이 필요합니다.")
            .setNegativeButtonText("나중에 설정")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun unlockSuccess() {
        AppLockManager.isUnlocked = true
        setResult(Activity.RESULT_OK)
        finish()
    }
}
