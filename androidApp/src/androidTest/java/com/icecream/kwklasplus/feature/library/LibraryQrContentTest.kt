package com.icecream.kwklasplus.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryQrContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateAndRefreshActionAreExposed() {
        var refreshed = false
        composeRule.setContent {
            KlasPlusTheme {
                LibraryQrContent(
                    state = LibraryQrUiState(loading = true),
                    onRefreshClick = { refreshed = true },
                    onSettingsClick = {},
                    onAddWidgetClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_qr_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("library_qr_refresh").performClick()
        assertTrue(refreshed)
    }
}
