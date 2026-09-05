package com.icecream.kwklasplus.feature.player

import android.widget.FrameLayout
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoPlayerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pipRoundTripKeepsMediaAttachedAndRestoresControls() {
        val pip = mutableStateOf(false)
        lateinit var container: FrameLayout
        composeRule.setContent {
            val context = LocalContext.current
            container = remember { FrameLayout(context) }
            KlasPlusTheme {
                VideoPlayerScreen(
                    webContainer = container,
                    state = VideoPlayerUiState(),
                    isPictureInPicture = pip.value,
                    onSeek = {}, onPlayPauseClick = {}, onBackwardClick = {},
                    onForwardClick = {}, onMuteClick = {}, onFullscreenClick = {},
                    onPictureInPictureClick = {}, onSpeedClick = {},
                    onCloseClick = {}, onLectureTimeClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("video_player_controls").assertExists()
        composeRule.runOnIdle { pip.value = true }
        composeRule.onNodeWithTag("video_player_controls").assertDoesNotExist()
        composeRule.onNodeWithTag("video_web_container").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(container.isAttachedToWindow)
            pip.value = false
        }
        composeRule.onNodeWithTag("video_player_controls").assertExists()
        composeRule.runOnIdle { assertTrue(container.isAttachedToWindow) }
    }

    @Test
    fun playPauseControlEmitsAction() {
        var clicked = false
        composeRule.setContent {
            val context = LocalContext.current
            val container = remember { FrameLayout(context) }
            KlasPlusTheme {
                VideoPlayerScreen(
                    webContainer = container,
                    state = VideoPlayerUiState(isPlaying = false),
                    onSeek = {},
                    onPlayPauseClick = { clicked = true },
                    onBackwardClick = {},
                    onForwardClick = {},
                    onMuteClick = {},
                    onFullscreenClick = {},
                    onPictureInPictureClick = {},
                    onSpeedClick = {},
                    onCloseClick = {},
                    onLectureTimeClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("video_web_container").assertIsDisplayed()
        composeRule.onNodeWithTag("video_play_pause").performClick()
        assertTrue(clicked)
    }
}
