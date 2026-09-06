package com.icecream.kwklasplus.core.academic

enum class WidgetDestination(val tab: String, val uri: String) {
    TIMETABLE("timetable", "klasplus://widget/timetable"),
    CALENDAR("calendar", "klasplus://widget/calendar");

    companion object {
        fun fromUri(uri: String?): WidgetDestination? = entries.firstOrNull { it.uri == uri }
    }
}
