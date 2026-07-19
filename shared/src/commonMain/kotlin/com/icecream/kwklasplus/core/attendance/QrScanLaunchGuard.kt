package com.icecream.kwklasplus.core.attendance

class QrScanLaunchGuard {
    private var isActive = false

    fun tryAcquire(): Boolean {
        if (isActive) return false
        isActive = true
        return true
    }

    fun release() {
        isActive = false
    }
}
