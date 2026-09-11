package com.icecream.kwklasplus.feature.library

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
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
    fun loadingStateDisablesRefresh() {
        var refreshed = false
        composeRule.setContent {
            KlasPlusTheme {
                LibraryQrContent(
                    state = LibraryQrUiState(loading = true),
                    onRefreshClick = { refreshed = true },
                    onSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("library_qr_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("library_qr_refresh").assertIsNotEnabled()
        assertTrue(!refreshed)
    }

    @Test
    fun widgetErrorExposesSettingsAndRetry() {
        var settingsOpened = false
        var refreshed = false
        composeRule.setContent {
            KlasPlusTheme {
                LibraryQrContent(
                    state = LibraryQrUiState(loading = false),
                    onRefreshClick = { refreshed = true },
                    onSettingsClick = { settingsOpened = true },
                )
            }
        }
        composeRule.onNodeWithText("도서관 출입증 정보를 가져올 수 없습니다. 설정에서 입력한 정보가 올바른지 확인한 후 다시 시도해주세요.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("library_qr_settings").performClick()
        composeRule.onNodeWithTag("library_qr_refresh").performClick()
        assertTrue(settingsOpened)
        assertTrue(refreshed)
    }
}
