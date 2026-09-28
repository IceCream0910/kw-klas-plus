package com.icecream.kwklasplus.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class LoginFunnelScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun studentIdAndPasswordAreSeparateSteps() {
        var state by mutableStateOf(funnelState(LoginFunnelStep.StudentId))
        rule.setContent {
            KlasPlusTheme {
                funnel(
                    state = state,
                    update = { updated ->
                        state = if (state.step == LoginFunnelStep.StudentId &&
                            state.studentId.length < LoginUiState.STUDENT_ID_LENGTH &&
                            updated.studentId.length == LoginUiState.STUDENT_ID_LENGTH
                        ) updated.copy(step = LoginFunnelStep.Password) else updated
                    },
                    onContinue = { state = state.copy(step = LoginFunnelStep.Password) },
                )
            }
        }
        rule.onNodeWithTag("login_submit").assertIsNotEnabled()
        rule.onNodeWithTag("login_student_id").performTextInput("2020123456")
        rule.onNodeWithTag("login_password").assertIsDisplayed()
    }

    @Test
    fun skippingLibraryOffersDirectConsent() {
        var state by mutableStateOf(funnelState(LoginFunnelStep.Library).copy(studentId = "2020123456"))
        rule.setContent {
            KlasPlusTheme {
                funnel(
                    state = state,
                    update = { state = it },
                    onContinue = { state = state.copy(step = LoginFunnelStep.Complete) },
                    onSkipLibrary = { state = state.copy(step = LoginFunnelStep.Agreements) },
                )
            }
        }
        rule.onNodeWithTag("funnel_skip_library").performClick()
        rule.onNodeWithTag("login_submit").assertIsEnabled()
        rule.onNodeWithTag("login_agreement").performClick()
        rule.onNodeWithTag("login_terms_agreement").performClick()
        rule.onNodeWithTag("login_submit").assertIsEnabled()
    }

    private fun funnelState(step: LoginFunnelStep) = LoginFunnelUiState(
        step = step,
        studentId = "",
        password = "",
        libraryPassword = "",
        libraryPhone = "",
        privacyAccepted = false,
        termsAccepted = false,
        error = null,
    )

    @androidx.compose.runtime.Composable
    private fun funnel(
        state: LoginFunnelUiState,
        update: (LoginFunnelUiState) -> Unit,
        onContinue: () -> Unit,
        onSkipLibrary: () -> Unit = {},
    ) {
        LoginFunnelScreen(
            state = state,
            onStudentIdChange = { update(state.copy(studentId = it)) },
            onPasswordChange = { update(state.copy(password = it)) },
            onLibraryPasswordChange = { update(state.copy(libraryPassword = it)) },
            onLibraryPhoneChange = { update(state.copy(libraryPhone = it)) },
            onPrivacyChange = { update(state.copy(privacyAccepted = it)) },
            onTermsChange = { update(state.copy(termsAccepted = it)) },
            onContinue = onContinue,
            onBack = {},
            onSkipLibrary = onSkipLibrary,
            onSaveLibrary = {},
            onPrivacyDetails = {},
            onTermsDetails = {},
            onFindId = {},
            onFindPassword = {},
            onFinish = {},
        )
    }
}
