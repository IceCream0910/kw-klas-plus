package com.icecream.kwklasplus.feature.auth

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun agreementEnablesLoginForCompleteCredentials() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
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
                    onboardingWebView = webView,
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
