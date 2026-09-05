package com.icecream.kwklasplus.feature.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.webkit.WebChromeClient
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withText
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.icecream.kwklasplus.VideoPlayerActivity
import org.junit.Assert.*
import org.junit.Test

class VideoPlayerTransitionTest {
    @Test
    fun pipCloseImmediatelyReleasesPlaybackAndDetachesWebViews() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoPlayerActivity::class.java)
        ActivityScenario.launch<VideoPlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.withSinglePlayback {}
                activity.isPlaying = true
                activity.hasPlaybackSession = true
                activity.closePlaybackFromPip()
                assertFalse(activity.ownsPlayback())
                assertFalse(activity.isPlaying)
                assertFalse(activity.hasPlaybackSession)
                assertNull(activity.VideoWebView.parent)
                assertNull(activity.KLASWebView.parent)
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun firstLectureReservationDoesNotPromptBeforePlayerIsReady() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoPlayerActivity::class.java)
        ActivityScenario.launch<VideoPlayerActivity>(intent).use { scenario ->
            val accepted = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.withSinglePlayback {}
                assertFalse(activity.hasPlaybackSession)
                activity.withSinglePlayback { accepted.countDown() }
            }
            onView(withText("지금 재생 중인 강의를 종료하고 선택한 강의를 재생할까요?"))
                .check(doesNotExist())
            assertTrue(accepted.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun replacementRequiresConfirmationEvenForPausedPlayback() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoPlayerActivity::class.java)
        ActivityScenario.launch<VideoPlayerActivity>(intent).use { scenario ->
            var replaced = false
            val stopped = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.withSinglePlayback {}
                activity.isPlaying = false
                activity.hasPlaybackSession = true
                activity.withSinglePlayback { replaced = true }
            }
            onView(withText("취소")).perform(click())
            scenario.onActivity { activity ->
                assertFalse(replaced)
                assertTrue(activity.ownsPlayback())
                activity.withSinglePlayback { replaced = true; stopped.countDown() }
            }
            onView(withText("종료하고 재생")).perform(click())
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                assertTrue(replaced)
                assertTrue(activity.ownsPlayback())
                assertEquals("about:blank", activity.VideoWebView.url)
            }
        }
    }

    @Test
    fun pipCallbacksPreserveFullscreenAndItsOrientationUntilExplicitExit() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoPlayerActivity::class.java)
        ActivityScenario.launch<VideoPlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val inlineOrientation = activity.requestedOrientation
                val video = activity.VideoWebView
                val customView = View(activity)
                val chrome = video.webChromeClient!!
                chrome.onShowCustomView(customView, WebChromeClient.CustomViewCallback {})
                val fullscreenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                assertEquals(fullscreenOrientation, activity.requestedOrientation)
                activity.onPictureInPictureModeChanged(true, activity.resources.configuration)
                activity.onPictureInPictureModeChanged(false, activity.resources.configuration)
                assertEquals(fullscreenOrientation, activity.requestedOrientation)
                assertNotNull(customView.parent)
                assertSame(video, activity.VideoWebView)
                chrome.onHideCustomView()
                assertEquals(inlineOrientation, activity.requestedOrientation)
                assertNull(customView.parent)
            }
        }
    }
}
