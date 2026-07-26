package com.icecream.kwklasplus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.CredentialPreparationResult
import com.icecream.kwklasplus.core.auth.PlainPassword
import com.icecream.kwklasplus.feature.auth.LoginScreen
import com.icecream.kwklasplus.feature.auth.LoginUiState
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var onboardingWebView: WebView
    private var onboardingVisible by mutableStateOf(true)
    private var studentId by mutableStateOf("")
    private var password by mutableStateOf("")
    private var agreementAccepted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()
        onboardingVisible = savedInstanceState?.getBoolean(STATE_ONBOARDING_VISIBLE) ?: true
        studentId = savedInstanceState?.getString(STATE_STUDENT_ID).orEmpty()
        agreementAccepted = savedInstanceState?.getBoolean(STATE_AGREEMENT_ACCEPTED) ?: false

        onboardingWebView = WebView(this).apply {
            configureAppWebView()
            loadUrl(AppUrls.ONBOARDING)
        }

        setContent {
            KlasPlusTheme {
                LoginScreen(
                    state = LoginUiState(
                        onboardingVisible = onboardingVisible,
                        studentId = studentId,
                        password = password,
                        agreementAccepted = agreementAccepted,
                    ),
                    onboardingWebView = onboardingWebView,
                    onStartClick = { onboardingVisible = false },
                    onStudentIdChange = { value ->
                        if (
                            value.length <= LoginUiState.STUDENT_ID_LENGTH &&
                            value.all(Char::isDigit)
                        ) {
                            studentId = value
                        }
                    },
                    onPasswordChange = { password = it },
                    onAgreementChange = { agreementAccepted = it },
                    onAgreementDetailsClick = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://blog.yuntae.in/11cfc9b9-3eca-8078-96a0-c41c4ca9cb8f"),
                            ),
                        )
                    },
                    onFindIdClick = {
                        openWebRoute(
                            "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindMemberNoPage.do",
                            null,
                        )
                    },
                    onFindPasswordClick = {
                        openWebRoute(
                            "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindPwdPage.do",
                            null,
                        )
                    },
                    onRegisterClick = {
                        openWebRoute(
                            "https://klas.kw.ac.kr/usr/cmn/login/modal/UserFrstModPwdPage.do",
                            null,
                        )
                    },
                    onLoginClick = ::submitLogin,
                )
            }
        }
    }

    private fun submitLogin() {
        if (!agreementAccepted) {
            Toast.makeText(this, "개인정보 수집 및 제공에 동의해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (studentId.length != LoginUiState.STUDENT_ID_LENGTH || password.isEmpty()) {
            Toast.makeText(this, "학번과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        prepareCredential(studentId, password)
    }

    private fun prepareCredential(id: String, plainPassword: String) {
        lifecycleScope.launch {
            when (
                val result = appDependencies.prepareCredentialUseCase.prepare(
                    id,
                    PlainPassword.of(plainPassword),
                )
            ) {
                is CredentialPreparationResult.Success -> openMainActivity()
                is CredentialPreparationResult.Failure -> {
                    val message = when (result.failure) {
                        AuthFailure.Timeout -> "요청 시간이 초과되었습니다. 다시 시도해주세요."
                        AuthFailure.MalformedResponse -> "서버 응답을 처리할 수 없습니다."
                        AuthFailure.Storage -> "로그인 정보를 안전하게 저장하지 못했습니다."
                        else -> "로그인 정보를 확인하는 중 오류가 발생했습니다."
                    }
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openMainActivity() {
        password = ""
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onDestroy() {
        password = ""
        onboardingWebView.stopLoading()
        onboardingWebView.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_ONBOARDING_VISIBLE, onboardingVisible)
        outState.putString(STATE_STUDENT_ID, studentId)
        outState.putBoolean(STATE_AGREEMENT_ACCEPTED, agreementAccepted)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_ONBOARDING_VISIBLE = "onboarding_visible"
        const val STATE_STUDENT_ID = "student_id"
        const val STATE_AGREEMENT_ACCEPTED = "agreement_accepted"
    }
}
