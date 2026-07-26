package com.icecream.kwklasplus.feature.startup

import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Rule
import org.junit.Test

class AuthenticationLoadingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsCurrentMessageAndKeepsAuthenticationWebViewAttached() {
        val webView = WebView(ApplicationProvider.getApplicationContext())

        composeRule.setContent {
            KlasPlusTheme {
                AuthenticationLoadingScreen(
                    webView = webView,
                    message = "조금만 더 기다려주세요",
                )
            }
        }

        composeRule.onNodeWithTag("authentication_loading").assertIsDisplayed()
        composeRule.onNodeWithText("조금만 더 기다려주세요").assertIsDisplayed()
        composeRule.onNodeWithTag("authentication_web_view").fetchSemanticsNode()
    }
}
