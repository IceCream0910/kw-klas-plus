package com.icecream.kwklasplus.core.attendance

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrScanLaunchGuardTest {
    @Test
    fun ignoresDuplicateLaunchUntilReleased() {
        val guard = QrScanLaunchGuard()

        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())

        guard.release()

        assertTrue(guard.tryAcquire())
    }
}
