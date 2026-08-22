package com.icecream.kwklasplus.core.web

import com.icecream.kwklasplus.core.legacy.KlasUrls

object ProductWebUrls {
    fun homeTab(tab: String, yearHakgi: String): String = when (tab) {
        "timetable" -> "${KlasUrls.KLAS_PLUS_BASE}/timetableTab?yearHakgi=$yearHakgi"
        "calendar" -> "${KlasUrls.KLAS_PLUS_BASE}/calendar?yearHakgi=$yearHakgi"
        "menu" -> "${KlasUrls.KLAS_PLUS_BASE}/profile"
        else -> "${KlasUrls.KLAS_PLUS_BASE}/feed?yearHakgi=$yearHakgi"
    }

    fun boardList(title: String): String = "${KlasUrls.KLAS_PLUS_BASE}/boardList?title=$title"

    fun boardView(boardNumber: String, masterNumber: String): String =
        "${KlasUrls.KLAS_PLUS_BASE}/boardView?boardNo=$boardNumber&masterNo=$masterNumber"

    fun task(path: String): String = KlasUrls.KLAS_BASE + path

    fun notReady(): String = "${KlasUrls.KLAS_PLUS_BASE}/notReady"
}
