package com.icecream.kwklasplus.core.academic

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class WidgetSyncStatus { READY, NEEDS_LOGIN, RETRY }

@Serializable
data class AcademicWidgetSnapshot(
    val owner: String,
    val term: String,
    val timetable: List<TimetableEntry>? = null,
    val timetableFetchedAt: Long = 0,
    val month: String = "",
    val calendar: List<CalendarEvent>? = null,
    val calendarFetchedAt: Long = 0,
    val calendarStatus: WidgetSyncStatus = WidgetSyncStatus.READY,
)

object AcademicWidgetSnapshotPolicy {
    fun forIdentity(previous: AcademicWidgetSnapshot?, owner: String, term: String): AcademicWidgetSnapshot? = when {
        owner.isBlank() -> null
        previous == null || previous.owner != owner -> AcademicWidgetSnapshot(owner, term)
        previous.term != term -> previous.copy(term = term, timetable = null, timetableFetchedAt = 0)
        else -> previous
    }

    fun applyCalendar(
        current: AcademicWidgetSnapshot,
        result: CalendarSyncResult,
        month: String,
        now: Long,
    ): AcademicWidgetSnapshot = when (result) {
        is CalendarSyncResult.Success -> current.copy(
            month = month,
            calendar = result.events,
            calendarFetchedAt = now,
            calendarStatus = WidgetSyncStatus.READY,
        )
        CalendarSyncResult.NeedsLogin -> current.copy(calendarStatus = WidgetSyncStatus.NEEDS_LOGIN)
        CalendarSyncResult.Retry -> current.copy(calendarStatus = WidgetSyncStatus.RETRY)
    }
}

interface AcademicWidgetSnapshotStore {
    fun read(): AcademicWidgetSnapshot?
    fun write(snapshot: AcademicWidgetSnapshot)
    fun clear()
}

object AcademicWidgetSnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(snapshot: AcademicWidgetSnapshot): String = json.encodeToString(snapshot)
    fun decode(value: String): AcademicWidgetSnapshot = json.decodeFromString(value)
}
