package com.icecream.kwklasplus.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosDeadlineDateParsersTest {
    @Test
    fun parsesLegacyOffsetAssignmentFormat() {
        val parsed = IosDeadlineDateParsers.assignment.parseEpochMillis("2026-07-17T12:34:56.789+0900")
        assertEquals(1_784_259_296_789L, parsed)
    }

    @Test
    fun parsesLocalDateTime() {
        val online = IosDeadlineDateParsers.onlineLecture.parseEpochMillis("2026-07-17 12:34:59")
        val assignment = IosDeadlineDateParsers.assignment.parseEpochMillis("2026-07-17 12:34:59")
        assertEquals(online, assignment)
        assertEquals(true, online != null)
    }

    @Test
    fun invalidDateIsRejected() {
        assertNull(IosDeadlineDateParsers.assignment.parseEpochMillis("invalid"))
    }
}
