package com.icecream.kwklasplus.widget

import android.graphics.Bitmap
import android.util.LruCache
import com.icecream.kwklasplus.core.academic.AcademicWidgetSnapshot
import java.time.LocalDate

internal data class WidgetBitmapKey(
    val kind: AcademicWidgetKind,
    val snapshot: AcademicWidgetSnapshot,
    val date: String,
    val width: Int,
    val height: Int,
    val dark: Boolean,
    val resolution: WidgetBitmapResolution,
) {
    companion object {
        fun create(kind: AcademicWidgetKind, snapshot: AcademicWidgetSnapshot, date: String,
                   width: Int, height: Int, dark: Boolean, resolution: WidgetBitmapResolution) =
            WidgetBitmapKey(kind, snapshot.copy(
                timetable = if (kind == AcademicWidgetKind.TIMETABLE) snapshot.timetable else null,
                calendar = if (kind == AcademicWidgetKind.CALENDAR) snapshot.calendar else null,
                month = if (kind == AcademicWidgetKind.CALENDAR) snapshot.month else "",
                timetableFetchedAt = 0, calendarFetchedAt = 0,
                calendarStatus = com.icecream.kwklasplus.core.academic.WidgetSyncStatus.READY,
            ), date, width, height, dark, resolution)
    }
}

internal object WidgetBitmapCache {
    private var cache: LruCache<WidgetBitmapKey, Bitmap>? = null

    @Synchronized
    fun get(kind: AcademicWidgetKind, snapshot: AcademicWidgetSnapshot, width: Int, height: Int,
            dark: Boolean, resolution: WidgetBitmapResolution, budget: Int): Bitmap {
        val limit = (budget.toLong() * 3).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        val images = cache?.takeIf { it.maxSize() == limit } ?: object : LruCache<WidgetBitmapKey, Bitmap>(limit) {
            override fun sizeOf(key: WidgetBitmapKey, value: Bitmap) = value.allocationByteCount
        }.also { cache = it }
        val key = WidgetBitmapKey.create(kind, snapshot, LocalDate.now().toString(), width, height, dark, resolution)
        return images.get(key) ?: AcademicWidgetDrawing.draw(kind, snapshot, width, height, dark, resolution)
            .also { images.put(key, it) }
    }

    @Synchronized fun clear() { cache?.evictAll() }
}
