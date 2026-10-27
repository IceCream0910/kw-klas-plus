package com.icecream.kwklasplus.core.web

import com.icecream.kwklasplus.core.academic.AcademicTermDisplay
import com.icecream.kwklasplus.core.legacy.KlasUrls
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductWebUrlsTest {
    @Test
    fun homeTabUrlsMatchAndroidHomeActivity() {
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/feed?yearHakgi=2026,1",
            ProductWebUrls.homeTab("feed", "2026,1"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/timetableTab?yearHakgi=2026,1",
            ProductWebUrls.homeTab("timetable", "2026,1"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/calendar?yearHakgi=2026,1",
            ProductWebUrls.homeTab("calendar", "2026,1"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/profile",
            ProductWebUrls.homeTab("menu", "2026,1"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/feed?yearHakgi=2026,1",
            ProductWebUrls.homeTab("unknown", "2026,1"),
        )
    }

    @Test
    fun boardAndTaskUrlsMatchAndroidActivities() {
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/boardList?title=%EA%B3%B5%EC%A7%80",
            ProductWebUrls.boardList("공지"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/boardView?boardNo=1&masterNo=2",
            ProductWebUrls.boardView("1", "2"),
        )
        assertEquals(
            "${KlasUrls.KLAS_BASE}/std/lis/evltn/TaskStdPage.do",
            ProductWebUrls.task("/std/lis/evltn/TaskStdPage.do"),
        )
        assertEquals("${KlasUrls.KLAS_PLUS_BASE}/notReady", ProductWebUrls.notReady())
    }

    @Test
    fun boardQueryValuesArePercentEncoded() {
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/boardList?title=%EA%B0%95%EC%9D%98%20%EA%B3%B5%EC%A7%80%EC%82%AC%ED%95%AD",
            ProductWebUrls.boardList("강의 공지사항"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/boardList?title=Q%26A%20C%23%201%2B1%20100%25",
            ProductWebUrls.boardList("Q&A C# 1+1 100%"),
        )
        assertEquals(
            "${KlasUrls.KLAS_PLUS_BASE}/boardView?boardNo=1%262&masterNo=a%23b",
            ProductWebUrls.boardView("1&2", "a#b"),
        )
    }

    @Test
    fun yearHakgiButtonTextMatchesAndroid() {
        assertEquals("2026년도 1학기", AcademicTermDisplay.buttonText("2026,1"))
        assertEquals("2026년도 여름학기", AcademicTermDisplay.buttonText("2026,3"))
        assertEquals("2025년도 겨울학기", AcademicTermDisplay.buttonText("2025,4"))
    }
}
