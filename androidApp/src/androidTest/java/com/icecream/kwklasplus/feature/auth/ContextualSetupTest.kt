package com.icecream.kwklasplus.feature.auth

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContextualSetupTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("contextual_setup_test", Context.MODE_PRIVATE)

    @Test fun lockCompletionCannotConsumeNotificationRecommendation() {
        preferences.edit().clear().commit()
        val setup = ContextualSetup(preferences)
        compose.runOnUiThread {
            setup.offer(SetupSuggestion.AppLock, false)
            setup.displayed(SetupSuggestion.AppLock)
            setup.dismiss()
            setup.offer(SetupSuggestion.Notifications, false)
            assertEquals(SetupSuggestion.Notifications, setup.suggestion)
            assertFalse(preferences.getBoolean("setup_prompt_seen_notifications", false))
        }
        preferences.edit().clear().commit()
    }

    @Test fun floatingCardDoesNotBlockBackgroundAndCloseIsPersistent() {
        preferences.edit().clear().commit()
        val setup = ContextualSetup(preferences)
        var backgroundClicks = 0
        compose.setContent {
            KlasPlusTheme {
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { backgroundClicks++ }, modifier = Modifier.align(Alignment.TopCenter)) { Text("배경 버튼") }
                    SetupSuggestionCard(setup, {}, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.runOnIdle { setup.offer(SetupSuggestion.Library, false) }
        compose.onNodeWithText("출입증을 설정할까요?").assertIsDisplayed()
        compose.onNodeWithText("배경 버튼").performClick()
        compose.runOnIdle { assertEquals(1, backgroundClicks) }
        compose.onNodeWithContentDescription("추천 설정 닫기").performClick()
        compose.runOnIdle {
            val recreated = ContextualSetup(preferences)
            recreated.offer(SetupSuggestion.Library, false)
            assertNull(recreated.suggestion)
        }
        preferences.edit().clear().commit()
    }
}
