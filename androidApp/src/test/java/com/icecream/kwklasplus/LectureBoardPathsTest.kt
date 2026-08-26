package com.icecream.kwklasplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LectureBoardPathsTest {
    @Test
    fun eachBoardPathBecomesAvailableIndependently() {
        val paths = LectureBoardPaths()

        paths.update(notice = "notice", pds = "")

        assertEquals("notice", paths.pathFor("notice"))
        assertNull(paths.pathFor("pds"))
    }

    @Test
    fun partialUpdatesDoNotEraseAResolvedPath() {
        val paths = LectureBoardPaths()

        paths.update(notice = "notice", pds = "")
        paths.update(notice = "", pds = "pds")

        assertEquals("notice", paths.pathFor("notice"))
        assertEquals("pds", paths.pathFor("pds"))
    }

    @Test
    fun unknownBoardTypeIsRejected() {
        val paths = LectureBoardPaths()

        assertTrue(paths.isSupportedType("notice"))
        assertTrue(paths.isSupportedType("pds"))
        assertFalse(paths.isSupportedType("unknown"))
        assertNull(paths.pathFor("unknown"))
    }
}
