package com.icecream.kwklasplus.feature.attendance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class QrCheckInLoadingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAuthenticationProgress() {
        composeRule.setContent {
            KlasPlusTheme {
                QrCheckInLoadingScreen()
            }
        }

        composeRule.onNodeWithTag("qr_check_in_loading").assertIsDisplayed()
        composeRule.onNodeWithText("인증 중").assertIsDisplayed()
    }
}
