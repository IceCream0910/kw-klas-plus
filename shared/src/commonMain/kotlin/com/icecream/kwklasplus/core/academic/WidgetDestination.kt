package com.icecream.kwklasplus.core.academic

enum class WidgetDestination(val tab: String, val uri: String, val iosUri: String) {
    TIMETABLE("timetable", "klasplus://widget/timetable", "kwklasplus://widget/timetable"),
    CALENDAR("calendar", "klasplus://widget/calendar", "kwklasplus://widget/calendar");

    companion object {
        fun fromUri(uri: String?): WidgetDestination? =
            entries.firstOrNull { it.uri == uri || it.iosUri == uri }
    }
}
