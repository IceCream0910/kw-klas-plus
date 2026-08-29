package com.icecream.kwklasplus.feature.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class AuthenticationLoadingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsCurrentMessage() {
        composeRule.setContent {
            KlasPlusTheme {
                AuthenticationLoadingScreen(
                    message = "조금만 더 기다려주세요",
                )
            }
        }

        composeRule.onNodeWithTag("authentication_loading").assertIsDisplayed()
        composeRule.onNodeWithText("조금만 더 기다려주세요").assertIsDisplayed()
    }
}
