package com.icecream.kwklasplus.core.academic

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidDeadlineDateParsersTest {
    @Test
    fun parsesLegacyOffsetAssignmentFormat() {
        assertEquals(
            Instant.parse("2026-07-17T03:34:56.789Z").toEpochMilli(),
            AndroidDeadlineDateParsers.assignment.parseEpochMillis(
                "2026-07-17T12:34:56.789+0900",
            ),
        )
    }

    @Test
    fun parsesLegacyLocalDateTimeFormatsInDeviceTimezone() {
        val expected = LocalDateTime.of(2026, 7, 17, 12, 34, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(
            expected,
            AndroidDeadlineDateParsers.onlineLecture.parseEpochMillis("2026-07-17 12:34:59"),
        )
        assertEquals(
            expected,
            AndroidDeadlineDateParsers.assignment.parseEpochMillis("2026-07-17 12:34:59"),
        )
    }

    @Test
    fun invalidDateIsRejected() {
        assertNull(AndroidDeadlineDateParsers.assignment.parseEpochMillis("invalid"))
    }
}
