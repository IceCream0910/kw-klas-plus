package com.icecream.kwklasplus.core.academic

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ACADEMIC_WIDGET_SCHEMA_VERSION = 1

@Serializable
data class AcademicWidgetClass(
    val title: String,
    val day: Int,
    val startTime: String,
    val endTime: String,
    val info: String,
    val color: TimetableColor,
)

@Serializable
data class AcademicWidgetEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val color: String,
)

@Serializable
data class AcademicWidgetDisplay(
    val schemaVersion: Int = ACADEMIC_WIDGET_SCHEMA_VERSION,
    val owner: String,
    val term: String,
    val month: String = "",
    val classes: List<AcademicWidgetClass>? = null,
    val timetableFetchedAt: Long = 0,
    val events: List<AcademicWidgetEvent>? = null,
    val calendarFetchedAt: Long = 0,
    val calendarStatus: WidgetSyncStatus = WidgetSyncStatus.READY,
)

object AcademicWidgetDisplayFactory {
    fun from(snapshot: AcademicWidgetSnapshot): AcademicWidgetDisplay {
        val colors = TimetableColorPolicy.assign(snapshot.timetable.orEmpty().map(TimetableEntry::title))
        return AcademicWidgetDisplay(
            schemaVersion = ACADEMIC_WIDGET_SCHEMA_VERSION,
            owner = snapshot.owner,
            term = snapshot.term,
            month = snapshot.month,
            classes = snapshot.timetable?.map { entry ->
                AcademicWidgetClass(
                    title = entry.title,
                    day = entry.day,
                    startTime = entry.startTime,
                    endTime = entry.endTime,
                    info = entry.info,
                    color = TimetableColorPolicy.assignedColor(entry.title, colors),
                )
            },
            timetableFetchedAt = snapshot.timetableFetchedAt,
            events = snapshot.calendar?.map { event ->
                AcademicWidgetEvent(event.id, event.title, event.start, event.end, event.color)
            },
            calendarFetchedAt = snapshot.calendarFetchedAt,
            calendarStatus = snapshot.calendarStatus,
        )
    }
}

object AcademicWidgetDisplayCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(display: AcademicWidgetDisplay): String = json.encodeToString(display)

    fun decode(value: String): AcademicWidgetDisplay? = runCatching {
        json.decodeFromString<AcademicWidgetDisplay>(value)
    }.getOrNull()?.takeIf { it.schemaVersion == ACADEMIC_WIDGET_SCHEMA_VERSION && it.owner.isNotBlank() }
}
