package com.icecream.kwklasplus.feature.lock

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LockScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keypadEmitsInputAndLeavesConfirmationPositionEmpty() {
        var lastNumber = -1
        composeRule.setContent {
            KlasPlusTheme {
                LockScreen(
                    state = LockScreenUiState(
                        title = "앱 잠금",
                        description = "비밀번호를 입력해주세요.",
                        enteredDigits = 5,
                        biometricVisible = false,
                    ),
                    onNumberClick = { lastNumber = it },
                    onDeleteClick = {},
                    onBiometricClick = {},
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag("pin_confirm").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("pin_1").performClick()
        assertEquals(1, lastNumber)
    }
}
