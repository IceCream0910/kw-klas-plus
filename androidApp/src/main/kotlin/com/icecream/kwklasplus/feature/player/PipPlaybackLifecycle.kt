package com.icecream.kwklasplus.feature.player

internal class PipPlaybackLifecycle {
    private var enteredPip = false

    fun onPipModeChanged(inPip: Boolean) {
        if (inPip) enteredPip = true
    }

    fun onResumed(inPip: Boolean) {
        if (!inPip) enteredPip = false
    }

    fun shouldCloseOnStop(interactive: Boolean, locked: Boolean, changingConfiguration: Boolean): Boolean =
        enteredPip && interactive && !locked && !changingConfiguration
}
