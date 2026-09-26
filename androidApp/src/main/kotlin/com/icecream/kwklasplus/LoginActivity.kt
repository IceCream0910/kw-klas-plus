package com.icecream.kwklasplus

import com.icecream.kwklasplus.widget.WidgetNavigation
import com.icecream.kwklasplus.widget.AcademicWidgets
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.CredentialPreparationResult
import com.icecream.kwklasplus.core.auth.LoginResult
import com.icecream.kwklasplus.core.auth.PlainPassword
import com.icecream.kwklasplus.feature.auth.LoginScreen
import com.icecream.kwklasplus.feature.auth.LoginFunnelScreen
import com.icecream.kwklasplus.feature.auth.LoginFunnelStep
import com.icecream.kwklasplus.feature.auth.LoginFunnelUiState
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus
import com.icecream.kwklasplus.feature.auth.LoginUiState
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class LoginActivity : AppCompatActivity() {
    private var onboardingVisible by mutableStateOf(true)
    private var studentId by mutableStateOf("")
    private var password by mutableStateOf("")
    private var funnelStep by mutableStateOf(LoginFunnelStep.StudentId)
    private var libraryPassword by mutableStateOf("")
    private var libraryPhone by mutableStateOf("")
    private var privacyAccepted by mutableStateOf(false)
    private var termsAccepted by mutableStateOf(false)
    private var canReturnToOnboarding = false
    private var funnelError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()
        onboardingVisible = savedInstanceState?.getBoolean(STATE_ONBOARDING_VISIBLE) ?: true
        studentId = savedInstanceState?.getString(STATE_STUDENT_ID).orEmpty()
        val persistedStatus = appPreferences.getString(LoginFunnelStatus.KEY, null)
        funnelStep = savedInstanceState?.getString(STATE_FUNNEL_STEP)
            ?.let { runCatching { LoginFunnelStep.valueOf(it) }.getOrNull() }
            ?: LoginFunnelStatus.initialStep(persistedStatus)
        if (funnelStep == LoginFunnelStep.Authenticating) funnelStep = LoginFunnelStep.Password
        if (persistedStatus != LoginFunnelStatus.SETUP && funnelStep in listOf(
                LoginFunnelStep.Library, LoginFunnelStep.Agreements,
                LoginFunnelStep.Complete,
            )) funnelStep = LoginFunnelStatus.initialStep(persistedStatus)
        privacyAccepted = savedInstanceState?.getBoolean(STATE_PRIVACY_ACCEPTED) ?: false
        termsAccepted = savedInstanceState?.getBoolean(STATE_TERMS_ACCEPTED) ?: false
        canReturnToOnboarding = savedInstanceState?.getBoolean(STATE_CAN_RETURN_TO_ONBOARDING) ?: false
        libraryPhone = (savedInstanceState?.getString(STATE_LIBRARY_PHONE)
            ?: appPreferences.getString(AppPrefs.LIBRARY_PHONE, "").orEmpty())
            .filter { it in '0'..'9' }
        if (funnelStep == LoginFunnelStep.Library || funnelStep == LoginFunnelStep.Password) {
            onboardingVisible = false
        }

        setContent {
            KlasPlusTheme {
                if (onboardingVisible) LoginScreen(
                    state = LoginUiState(
                        onboardingVisible = onboardingVisible,
                        studentId = studentId,
                        password = password,
                        agreementAccepted = false,
                    ),
                    onStartClick = { canReturnToOnboarding = true; onboardingVisible = false },
                    onStudentIdChange = { value ->
                        if (
                            value.length <= LoginUiState.STUDENT_ID_LENGTH &&
                            value.all(Char::isDigit)
                        ) {
                            studentId = value
                        }
                    },
                    onPasswordChange = { password = it },
                    onAgreementChange = {},
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
                ) else LoginFunnelScreen(
                    state = LoginFunnelUiState(
                        step = funnelStep,
                        studentId = studentId,
                        password = password,
                        libraryPassword = libraryPassword,
                        libraryPhone = libraryPhone,
                        privacyAccepted = privacyAccepted,
                        termsAccepted = termsAccepted,
                        error = funnelError,
                    ),
                    onStudentIdChange = ::updateStudentId,
                    onPasswordChange = { password = it; funnelError = null },
                    onLibraryPasswordChange = { libraryPassword = it },
                    onLibraryPhoneChange = { value -> libraryPhone = value.filter { it in '0'..'9' } },
                    onPrivacyChange = { privacyAccepted = it },
                    onTermsChange = { termsAccepted = it },
                    onContinue = ::continueFunnel,
                    onBack = ::backFunnel,
                    onSkipLibrary = { libraryPassword = ""; funnelStep = LoginFunnelStep.Agreements },
                    onSaveLibrary = ::saveLibrary,
                    onPrivacyDetails = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://klasplus.yuntae.in/privacy")))
                    },
                    onTermsDetails = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://klasplus.yuntae.in/tos")))
                    },
                    onFindId = { openWebRoute("https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindMemberNoPage.do", null) },
                    onFindPassword = { openWebRoute("https://klas.kw.ac.kr/usr/cmn/login/modal/UserFindPwdPage.do", null) },
                    onFinish = ::finishFunnel,
                    canReturnToOnboarding = canReturnToOnboarding,
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (onboardingVisible) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else if ((funnelStep == LoginFunnelStep.StudentId && canReturnToOnboarding) ||
                    funnelStep == LoginFunnelStep.Password || funnelStep == LoginFunnelStep.Agreements ||
                    funnelStep == LoginFunnelStep.Complete) {
                    backFunnel()
                } else if (funnelStep != LoginFunnelStep.Authenticating) {
                    moveTaskToBack(true)
                }
            }
        })

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val savedAccountId = runCatching {
                    appDependencies.credentialStore.loadAccountId()
                }.getOrNull()
                if (!savedAccountId.isNullOrBlank()) {
                    studentId = savedAccountId
                    onboardingVisible = false
                }
            }
        }
    }

    private fun submitLogin() {
        if (password.isEmpty()) return
        if (studentId.length != LoginUiState.STUDENT_ID_LENGTH) {
            funnelError = "학번을 확인해 주세요."
            return
        }
        if (!appPreferences.edit().putString(LoginFunnelStatus.KEY, LoginFunnelStatus.AUTHENTICATING).commit()) {
            funnelError = "로그인 상태를 저장하지 못했어요. 다시 시도해 주세요."
            return
        }
        AcademicWidgets.renderAll(this)
        funnelStep = LoginFunnelStep.Authenticating
        val plainPassword = PlainPassword.of(password)
        password = ""
        prepareCredential(studentId, plainPassword)
    }

    private fun updateStudentId(input: String) {
        if (input.length > LoginUiState.STUDENT_ID_LENGTH || !input.all(Char::isDigit)) return
        val shouldAdvance = LoginFunnelStatus.shouldAdvanceToPassword(studentId, input, funnelStep)
        studentId = input
        funnelError = null
        if (shouldAdvance) {
            funnelStep = LoginFunnelStep.Password
        }
    }

    private fun prepareCredential(id: String, plainPassword: PlainPassword) {
        lifecycleScope.launch {
            try {
                when (val result = appDependencies.prepareCredentialUseCase.prepare(id, plainPassword)) {
                is CredentialPreparationResult.Success -> {
                    when (val login = appDependencies.loginUseCase(appDependencies.httpAuthDriver()).resume(result.credential)) {
                        is LoginResult.Authenticated -> {
                            if (!appPreferences.edit().putString(LoginFunnelStatus.KEY, LoginFunnelStatus.SETUP).commit()) {
                                failLogin("로그인 상태를 저장하지 못했어요. 다시 시도해 주세요.")
                                return@launch
                            }
                            AcademicWidgets.renderAll(this@LoginActivity)
                            funnelStep = LoginFunnelStep.Library
                            funnelError = null
                        }
                        is LoginResult.UserActionRequired -> failLogin(
                            "KLAS에서 CAPTCHA 또는 임시 비밀번호 변경이 필요해요. 학교 사이트에서 조치를 마친 뒤 다시 시도해 주세요."
                        )
                        is LoginResult.Failed -> failLogin(authFailureMessage(login.failure))
                    }
                }
                is CredentialPreparationResult.Failure -> {
                    failLogin(authFailureMessage(result.failure))
                }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failLogin("KLAS 인증을 완료하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    private fun authFailureMessage(failure: AuthFailure): String = when (failure) {
        AuthFailure.InvalidCredentials -> "학번 또는 비밀번호를 확인해 주세요."
        AuthFailure.Timeout -> "요청 시간이 초과되었어요. 다시 시도해 주세요."
        AuthFailure.MalformedResponse -> "KLAS 응답을 처리할 수 없어요."
        AuthFailure.Storage -> "로그인 정보를 안전하게 저장하지 못했어요."
        else -> "KLAS 인증에 실패했어요. 다시 시도해 주세요."
    }

    private fun failLogin(message: String) {
        password = ""
        funnelError = message
        funnelStep = LoginFunnelStep.Password
    }

    private fun continueFunnel() {
        when (funnelStep) {
            LoginFunnelStep.StudentId -> funnelStep = LoginFunnelStep.Password
            LoginFunnelStep.Password -> submitLogin()
            LoginFunnelStep.Agreements -> {
                privacyAccepted = true
                termsAccepted = true
                funnelStep = LoginFunnelStep.Complete
            }
            LoginFunnelStep.Complete -> finishFunnel()
            else -> Unit
        }
    }

    private fun backFunnel() {
        funnelStep = when (funnelStep) {
            LoginFunnelStep.StudentId -> {
                if (canReturnToOnboarding) {
                    canReturnToOnboarding = false
                    onboardingVisible = true
                }
                funnelStep
            }
            LoginFunnelStep.Password -> LoginFunnelStep.StudentId
            LoginFunnelStep.Agreements -> LoginFunnelStep.Library
            LoginFunnelStep.Complete -> LoginFunnelStep.Agreements
            else -> funnelStep
        }
    }

    private fun saveLibrary() {
        if (studentId.isBlank() || libraryPassword.isBlank() || libraryPhone.isBlank()) return
        val secureSaved = encryptedPreferences.edit()
            .putString(AppPrefs.LIBRARY_PASSWORD, libraryPassword)
            .commit()
        if (!secureSaved) {
            funnelError = "출입증 정보를 안전하게 저장하지 못했어요."
            return
        }
        val settingsSaved = appPreferences.edit()
            .putString(AppPrefs.LIBRARY_STD_NUMBER, studentId)
            .putString(AppPrefs.LIBRARY_PHONE, libraryPhone)
            .remove(AppPrefs.LIBRARY_PASSWORD)
            .commit()
        if (!settingsSaved) {
            funnelError = "출입증 설정을 저장하지 못했어요."
            return
        }
        libraryPassword = ""
        funnelError = null
        funnelStep = LoginFunnelStep.Agreements
    }

    private fun finishFunnel() {
        if (
            funnelStep != LoginFunnelStep.Complete ||
            !privacyAccepted || !termsAccepted ||
            appPreferences.getString(LoginFunnelStatus.KEY, null) != LoginFunnelStatus.SETUP
        ) return
        if (!appPreferences.edit().putString(LoginFunnelStatus.KEY, LoginFunnelStatus.COMPLETE).commit()) {
            funnelError = "설정 완료 상태를 저장하지 못했어요. 다시 시도해 주세요."
            return
        }
        appDependencies.academicWidgets.foreground()
        openMainActivity()
    }

    private fun openMainActivity() {
        password = ""
        libraryPassword = ""
        finish()
        startActivity(WidgetNavigation.forward(intent, Intent(this, MainActivity::class.java)))
    }

    override fun onDestroy() {
        password = ""
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_ONBOARDING_VISIBLE, onboardingVisible)
        outState.putString(STATE_STUDENT_ID, studentId)
        outState.putBoolean(STATE_PRIVACY_ACCEPTED, privacyAccepted)
        outState.putBoolean(STATE_TERMS_ACCEPTED, termsAccepted)
        outState.putBoolean(STATE_CAN_RETURN_TO_ONBOARDING, canReturnToOnboarding)
        outState.putString(STATE_FUNNEL_STEP, funnelStep.name)
        outState.putString(STATE_LIBRARY_PHONE, libraryPhone)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_ONBOARDING_VISIBLE = "onboarding_visible"
        const val STATE_STUDENT_ID = "student_id"
        const val STATE_PRIVACY_ACCEPTED = "privacy_accepted"
        const val STATE_TERMS_ACCEPTED = "terms_accepted"
        const val STATE_CAN_RETURN_TO_ONBOARDING = "can_return_to_onboarding"
        const val STATE_FUNNEL_STEP = "funnel_step"
        const val STATE_LIBRARY_PHONE = "library_phone_input"
    }
}
