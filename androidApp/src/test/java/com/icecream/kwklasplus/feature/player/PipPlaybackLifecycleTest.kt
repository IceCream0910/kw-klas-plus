package com.icecream.kwklasplus.feature.player

import org.junit.Assert.*
import org.junit.Test

class PipPlaybackLifecycleTest {
    @Test fun pipDismissalClosesEvenIfModeExitArrivesBeforeStop() {
        val lifecycle = PipPlaybackLifecycle()
        lifecycle.onPipModeChanged(true)
        lifecycle.onPipModeChanged(false)
        assertTrue(lifecycle.shouldCloseOnStop(true, false, false))
    }

    @Test fun expansionToResumedActivityDoesNotClosePlayback() {
        val lifecycle = PipPlaybackLifecycle()
        lifecycle.onPipModeChanged(true)
        lifecycle.onPipModeChanged(false)
        lifecycle.onResumed(false)
        assertFalse(lifecycle.shouldCloseOnStop(true, false, false))
    }

    @Test fun screenLockAndConfigurationChangesDoNotMeanPipWasDismissed() {
        val lifecycle = PipPlaybackLifecycle()
        assertFalse(lifecycle.shouldCloseOnStop(true, false, false))
        lifecycle.onPipModeChanged(true)
        assertFalse(lifecycle.shouldCloseOnStop(false, false, false))
        assertFalse(lifecycle.shouldCloseOnStop(true, true, false))
        assertFalse(lifecycle.shouldCloseOnStop(true, false, true))
        lifecycle.onResumed(true)
        assertTrue(lifecycle.shouldCloseOnStop(true, false, false))
    }
}
