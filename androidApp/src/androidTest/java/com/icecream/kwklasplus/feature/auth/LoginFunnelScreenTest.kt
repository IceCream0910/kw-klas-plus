package com.icecream.kwklasplus.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class LoginFunnelScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun completionEntersHomeAfterDelayWithoutCta() {
        var finished = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KlasPlusTheme {
                LoginFunnelScreen(
                    state = funnelState(LoginFunnelStep.Complete),
                    onStudentIdChange = {}, onPasswordChange = {}, onLibraryPasswordChange = {}, onLibraryPhoneChange = {},
                    onPrivacyChange = {}, onTermsChange = {}, onContinue = {}, onConfigureAppLock = {}, onConfigureNotifications = {},
                    onBack = {}, onSaveLibrary = {}, onSkipLibrary = {}, onPrivacyDetails = {}, onTermsDetails = {}, onFindId = {}, onFindPassword = {}, onFinish = { finished++ },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("login_submit").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1_800)
        rule.runOnIdle { org.junit.Assert.assertEquals(0, finished) }
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { org.junit.Assert.assertEquals(1, finished) }
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun loginSecurityLinkFollowsExplanationAndSheetClosesFromFooter() {
        rule.setContent {
            KlasPlusTheme { funnel(funnelState(LoginFunnelStep.StudentId), {}, onContinue = {}) }
        }
        val description = rule.onNodeWithText("로그인 정보는 이 기기에서 학교 KLAS 서버로 직접 전달되며 KLAS+ 서버에 저장되지 않아요.")
            .fetchSemanticsNode().boundsInRoot
        val link = rule.onNodeWithTag("login_security_link")
        org.junit.Assert.assertTrue(link.fetchSemanticsNode().boundsInRoot.top >= description.bottom)
        link.performClick()
        rule.onNodeWithTag("login_security_close").assertIsDisplayed().performClick()
        rule.onNodeWithTag("login_security_close").assertDoesNotExist()
        rule.onNodeWithTag("login_student_id").assertIsDisplayed()
    }

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
    fun authenticatedSetupShowsAgreementsWithoutOptionalStages() {
        var completed = 0
        val initialStep = LoginFunnelStatus.initialStep(LoginFunnelStatus.SETUP)
        var state by mutableStateOf(funnelState(initialStep))
        rule.setContent {
            KlasPlusTheme {
                funnel(state, { state = it }, onContinue = { completed++ })
            }
        }
        rule.onNodeWithTag("funnel_skip_library").assertDoesNotExist()
        rule.onNodeWithTag("funnel_skip_app_lock").assertDoesNotExist()
        rule.onNodeWithTag("funnel_skip_notifications").assertDoesNotExist()
        rule.onNodeWithTag("login_submit").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(1, completed) }
    }

    @Test
    fun notificationPermissionOnlyStartsOnCtaAndCanBeSkipped() {
        var configured = 0
        var skipped = 0
        var locks = 0
        var state by mutableStateOf(funnelState(LoginFunnelStep.AppLock))
        rule.setContent {
            KlasPlusTheme {
                funnel(state, {},
                    onContinue = { skipped++; state = state.copy(step = LoginFunnelStep.Notifications) }, onConfigureNotifications = { configured++ }, onConfigureAppLock = { locks++ })
            }
        }
        rule.runOnIdle { org.junit.Assert.assertEquals(0, configured) }
        rule.onNodeWithTag("login_submit").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(1, locks) }
        rule.onNodeWithTag("funnel_skip_app_lock").performClick()
        rule.onNodeWithTag("login_submit").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(1, configured) }
        rule.onNodeWithTag("funnel_skip_notifications").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(2, skipped) }
    }

    @Test
    fun enabledNotificationStageContinuesWithoutReopeningPermissionSheet() {
        var continued = 0
        var configured = 0
        rule.setContent {
            KlasPlusTheme {
                funnel(funnelState(LoginFunnelStep.Notifications).copy(deadlineNotificationsEnabled = true), {},
                    onContinue = { continued++ }, onConfigureNotifications = { configured++ })
            }
        }
        val screenshot = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "deadline-onboarding-preview.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithTag("login_submit").performClick()
        rule.runOnIdle {
            org.junit.Assert.assertEquals(1, continued)
            org.junit.Assert.assertEquals(0, configured)
        }
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
        onConfigureNotifications: () -> Unit = {},
        onConfigureAppLock: () -> Unit = {},
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
            onConfigureNotifications = onConfigureNotifications,
            onConfigureAppLock = onConfigureAppLock,
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
