package com.icecream.kwklasplus.core.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcademicTermKeyTest {
    @Test
    fun parsesLegacyYearSemesterValue() {
        assertEquals(AcademicTermKey("2026", "1"), AcademicTermKey.parse("2026,1"))
        assertEquals(AcademicTermKey("2026", "3"), AcademicTermKey.parse(" 2026, 3 "))
    }

    @Test
    fun rejectsIncompleteLegacyValue() {
        assertNull(AcademicTermKey.parse(""))
        assertNull(AcademicTermKey.parse("2026"))
        assertNull(AcademicTermKey.parse("2026,"))
    }
}
