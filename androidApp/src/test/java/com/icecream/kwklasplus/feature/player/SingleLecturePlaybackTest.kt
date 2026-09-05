package com.icecream.kwklasplus.feature.player

import org.junit.Assert.*
import org.junit.Test

class SingleLecturePlaybackTest {
    @Test fun replacementKeepsOwnerUntilOldPlaybackStops() {
        val gate = SingleLecturePlayback<Any>()
        val a = Any()
        val b = Any()
        assertTrue(gate.begin(a, null))
        assertTrue(gate.complete(a))
        assertTrue(gate.begin(b, a))
        assertSame(a, gate.owner)
        assertFalse(gate.begin(Any(), a))
        assertTrue(gate.complete(b))
        gate.release(a)
        assertSame(b, gate.owner)
    }

    @Test fun cancellationAndStaleConfirmationNeverReplaceCurrentOwner() {
        val gate = SingleLecturePlayback<Any>()
        val a = Any()
        val b = Any()
        gate.begin(a, null)
        gate.complete(a)
        gate.begin(b, a)
        gate.cancel(b)
        assertSame(a, gate.owner)
        assertFalse(gate.complete(b))
        assertFalse(gate.begin(b, null))
        assertTrue(gate.begin(a, a))
        assertTrue(gate.complete(a))
    }
}
