package com.icecream.kwklasplus.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingSwipesAndLoginOpensForm() {
        var onboardingVisible by mutableStateOf(true)
        composeRule.setContent {
            KlasPlusTheme {
                LoginScreen(
                    state = LoginUiState(onboardingVisible, "", "", false),
                    onStartClick = { onboardingVisible = false },
                    onStudentIdChange = {},
                    onPasswordChange = {},
                    onAgreementChange = {},
                    onAgreementDetailsClick = {},
                    onFindIdClick = {},
                    onFindPasswordClick = {},
                    onRegisterClick = {},
                    onLoginClick = {},
                )
            }
        }
        composeRule.onNodeWithText("불편했던 KLAS를\n더 편리하게.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_image").assertIsDisplayed()
        val imageBottom = composeRule.onNodeWithTag("onboarding_image")
            .getUnclippedBoundsInRoot().bottom
        val titleTop = composeRule.onNodeWithText("불편했던 KLAS를\n더 편리하게.")
            .getUnclippedBoundsInRoot().top
        val noteBottom = composeRule.onNodeWithText(
            "⚠️ KLAS+는 개인이 개발한 것으로, 학교의 공식 앱이 아닙니다."
        ).getUnclippedBoundsInRoot().bottom
        val loginTop = composeRule.onNodeWithTag("login_start").getUnclippedBoundsInRoot().top
        assertTrue(loginTop - noteBottom <= 80.dp)
        assertTrue(titleTop - imageBottom <= 32.dp)
        composeRule.onNodeWithTag("login_onboarding").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("남아있는 할 일을\n한 눈에.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_image").assertIsDisplayed()
        composeRule.onNodeWithTag("login_start").performClick()
        composeRule.onNodeWithTag("login_form").assertIsDisplayed()
    }

    @Test
    fun agreementEnablesLoginForCompleteCredentials() {
        var agreementAccepted by mutableStateOf(false)

        composeRule.setContent {
            KlasPlusTheme {
                LoginScreen(
                    state = LoginUiState(
                        onboardingVisible = false,
                        studentId = "2026000001",
                        password = "secret",
                        agreementAccepted = agreementAccepted,
                    ),
                    onStartClick = {},
                    onStudentIdChange = {},
                    onPasswordChange = {},
                    onAgreementChange = { agreementAccepted = it },
                    onAgreementDetailsClick = {},
                    onFindIdClick = {},
                    onFindPasswordClick = {},
                    onRegisterClick = {},
                    onLoginClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("login_password").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_agreement_label").performClick()
        composeRule.onNodeWithTag("login_submit").assertIsEnabled()
    }
}
