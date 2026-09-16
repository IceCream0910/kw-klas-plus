package com.icecream.kwklasplus.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcademicWidgetDisplayTest {
    @Test fun encodeOmitsSecretsAndKeepsAssignedColors() {
        val snapshot = AcademicWidgetSnapshot(
            owner = "abc123",
            term = "2026,2",
            timetable = listOf(TimetableEntry("자료구조", 0, "9:0", "10:0", "새빛관 301/교수", "A")),
            timetableFetchedAt = 10,
            month = "2026-09",
            calendar = listOf(CalendarEvent("1", "회의", "2026-09-10T12:00", "2026-09-10T13:00", "개인일정", "#ff0000", "")),
            calendarFetchedAt = 20,
        )
        val encoded = AcademicWidgetDisplayCodec.encode(AcademicWidgetDisplayFactory.from(snapshot))
        assertFalse(encoded.contains("SESSION", ignoreCase = true))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("kwPWD"))
        val decoded = AcademicWidgetDisplayCodec.decode(encoded)!!
        assertEquals("abc123", decoded.owner)
        assertEquals("자료구조", decoded.classes?.single()?.title)
        assertEquals("default-v1", decoded.classes?.single()?.color?.palette)
        assertEquals("회의", decoded.events?.single()?.title)
    }

    @Test fun decodeRejectsCorruptVersionMismatchAndBlankOwner() {
        assertNull(AcademicWidgetDisplayCodec.decode("{"))
        assertNull(AcademicWidgetDisplayCodec.decode("""{"schemaVersion":2,"owner":"abc","term":"2026,2"}"""))
        assertNull(AcademicWidgetDisplayCodec.decode("""{"schemaVersion":1,"owner":"","term":"2026,2"}"""))
        val valid = AcademicWidgetDisplayCodec.encode(AcademicWidgetDisplay(owner = "abc", term = "2026,2"))
        assertTrue(AcademicWidgetDisplayCodec.decode(valid)?.owner == "abc")
    }
}
