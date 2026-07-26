package com.icecream.kwklasplus.ui.web

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeWebViewHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hostsExistingWebViewAndCoversItWhileLoading() {
        lateinit var hostedWebView: WebView
        composeRule.setContent {
            val context = LocalContext.current
            val webView = remember { WebView(context) }.also {
                hostedWebView = it
            }
            KlasPlusTheme {
                ComposeWebViewHost(
                    webView = webView,
                    isLoading = true,
                )
            }
        }

        composeRule.onNodeWithTag("compose_web_view").assertIsDisplayed()
        composeRule.onNodeWithTag("compose_web_loading").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, hostedWebView.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, hostedWebView.layoutParams.height)
        }
    }

    @Test
    fun hostsRefreshContainerWithoutReplacingItsWebViewChild() {
        composeRule.setContent {
            val context = LocalContext.current
            val refreshLayout = remember {
                SwipeRefreshLayout(context).apply {
                    addView(WebView(context))
                }
            }
            KlasPlusTheme {
                ComposeRefreshableWebViewHost(
                    refreshLayout = refreshLayout,
                    isLoading = false,
                )
            }
        }

        composeRule.onNodeWithTag("compose_refreshable_web_view").assertIsDisplayed()
    }

    @Test
    fun hostsMultiWebViewPlatformContainer() {
        composeRule.setContent {
            val context = LocalContext.current
            val container = remember {
                FrameLayout(context).apply {
                    addView(WebView(context))
                    addView(WebView(context))
                }
            }
            KlasPlusTheme {
                ComposePlatformViewHost(
                    contentView = container,
                    isLoading = false,
                    contentTag = "lecture_web_container",
                )
            }
        }

        composeRule.onNodeWithTag("lecture_web_container").assertIsDisplayed()
    }
}
