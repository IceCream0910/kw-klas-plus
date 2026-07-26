package com.icecream.kwklasplus.ui.modal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SelectionBottomSheetContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionEmitsSelection() {
        var selected = false
        composeRule.setContent {
            KlasPlusTheme {
                SelectionBottomSheetContent(
                    title = "학기 선택",
                    options = listOf(SelectionOption("2026년도 1학기") { selected = true }),
                )
            }
        }

        composeRule.onNodeWithTag("selection_bottom_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_option_0").performClick()
        assertTrue(selected)
    }
}
