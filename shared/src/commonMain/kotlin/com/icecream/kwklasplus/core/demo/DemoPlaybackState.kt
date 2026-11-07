package com.icecream.kwklasplus.core.demo

object DemoPlaybackState {
    var position: Double = 0.0
        private set
    var duration: Double = 0.0
        private set
    val progress: Double get() = if (duration > 0) position / duration * 100 else 0.0
    fun record(position: Double, duration: Double) {
        if (!DemoSession.active || !position.isFinite() || !duration.isFinite() || duration <= 0) return
        this.duration = duration
        this.position = position.coerceIn(0.0, duration)
    }
    fun reset() { position = 0.0; duration = 0.0 }
}
