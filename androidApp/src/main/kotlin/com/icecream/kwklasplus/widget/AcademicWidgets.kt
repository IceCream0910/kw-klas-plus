package com.icecream.kwklasplus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.icecream.kwklasplus.*
import com.icecream.kwklasplus.core.academic.*
import java.time.*
import java.time.format.DateTimeFormatter

enum class AcademicWidgetKind { TIMETABLE, CALENDAR }
enum class AcademicWidgetVariant { COMPACT, EXPANDED }

data class AcademicWidgetSpec(val kind: AcademicWidgetKind, val variant: AcademicWidgetVariant)

private const val ACTION_REFRESH_CALENDAR_WIDGET = "com.icecream.kwklasplus.widget.REFRESH_CALENDAR"

open class AcademicWidgetProvider(private val spec: AcademicWidgetSpec) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { AcademicWidgets.render(context, manager, it, spec) }
        AcademicWidgetScheduler.schedule(context)
        if (spec.kind == AcademicWidgetKind.CALENDAR) {
            val pending = goAsync()
            context.appDependencies.academicWidgets.refreshCalendarNow { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        AcademicWidgets.render(context, manager, id, spec)
        WidgetDisplayRefresh.schedule(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) = AcademicWidgetScheduler.schedule(context)

    override fun onDisabled(context: Context) = AcademicWidgetScheduler.schedule(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (spec.kind == AcademicWidgetKind.CALENDAR && intent.action == ACTION_REFRESH_CALENDAR_WIDGET) {
            val pending = goAsync()
            context.appDependencies.academicWidgets.refreshCalendarNow { pending.finish() }
            return
        }
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            context.appDependencies.academicWidgets.render()
            AcademicWidgetScheduler.schedule(context)
            AcademicWidgetScheduler.scheduleDate(context)
            if (spec.kind == AcademicWidgetKind.CALENDAR) context.appDependencies.academicWidgets.requestCalendar()
        }
    }
}

class TimetableWidget : AcademicWidgetProvider(AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.COMPACT))
class TimetableExpandedWidget : AcademicWidgetProvider(AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.EXPANDED))
class CalendarWidget : AcademicWidgetProvider(AcademicWidgetSpec(AcademicWidgetKind.CALENDAR, AcademicWidgetVariant.COMPACT))
class CalendarExpandedWidget : AcademicWidgetProvider(AcademicWidgetSpec(AcademicWidgetKind.CALENDAR, AcademicWidgetVariant.EXPANDED))

internal data class WidgetRow(val title: String, val detail: String, val relative: String = "",
    val progress: Int = -1, val colorKey: String = title, val color: TimetableColor? = null)
internal data class WidgetPresentation(
    val title: String, val summary: String, val status: String, val empty: String,
    val rows: List<WidgetRow>, val snapshot: AcademicWidgetSnapshot?, val ready: Boolean,
    val agendaDate: String = "", val todayEmpty: Boolean = false,
)

object AcademicWidgets {
    private const val TWO_COLUMN_PORTRAIT_MAX_DP = 170
    private const val TWO_COLUMN_LANDSCAPE_MAX_DP = 340

    internal fun isTwoColumnsOrLess(context: Context, width: Int): Boolean {
        val maxWidth = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            TWO_COLUMN_LANDSCAPE_MAX_DP else TWO_COLUMN_PORTRAIT_MAX_DP
        return width < maxWidth
    }

    private val specs = AcademicWidgetKind.entries.flatMap { kind ->
        AcademicWidgetVariant.entries.map { variant -> AcademicWidgetSpec(kind, variant) }
    }
    private fun provider(spec: AcademicWidgetSpec) = when (spec) {
        AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.COMPACT) -> TimetableWidget::class.java
        AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.EXPANDED) -> TimetableExpandedWidget::class.java
        AcademicWidgetSpec(AcademicWidgetKind.CALENDAR, AcademicWidgetVariant.COMPACT) -> CalendarWidget::class.java
        else -> CalendarExpandedWidget::class.java
    }
    private fun ids(context: Context, spec: AcademicWidgetSpec) = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, provider(spec)))
    fun hasCalendar(context: Context) = specs.any { it.kind == AcademicWidgetKind.CALENDAR && ids(context, it).isNotEmpty() }
    fun hasAny(context: Context) = specs.any { ids(context, it).isNotEmpty() }

    fun renderAll(context: Context, compactOnly: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        specs.forEach { spec ->
            if (compactOnly && spec != AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.COMPACT)) return@forEach
            ids(context, spec).forEach { id ->
                runCatching { render(context, manager, id, spec) }
            }
        }
    }

    internal fun presentation(context: Context, kind: AcademicWidgetKind): WidgetPresentation {
        val today = LocalDate.now()
        val snapshot = context.appDependencies.academicWidgets.snapshot()
        val calendar = kind == AcademicWidgetKind.CALENDAR
        val title = if (calendar) "${today.monthValue}월" else "시간표"
        val signedOut = context.appPreferences.getString(AppPrefs.KW_ID, "").isNullOrBlank()
        val loading = calendar && context.appDependencies.academicWidgets.calendarLoading
        val ready = !signedOut && if (calendar) snapshot?.calendar != null &&
            snapshot.month == YearMonth.now().toString() else snapshot?.timetable != null
        if (!ready) return WidgetPresentation(title, "", "앱을 눌러 확인", when {
            signedOut -> "로그인해주세요"
            loading -> "일정을 불러오고 있어요."
            calendar && snapshot?.calendarStatus == WidgetSyncStatus.NEEDS_LOGIN -> "로그인 후 다시 확인해주세요"
            calendar && snapshot?.calendarStatus == WidgetSyncStatus.RETRY -> "일정을 불러오지 못했어요.\n앱을 열어 다시 확인해주세요."
            calendar -> "이번 달 일정을 불러오는 중이에요"
            else -> "앱에서 시간표를 불러와주세요"
        }, emptyList(), null, false)
        snapshot!!
        if (!calendar) {
            val now = LocalTime.now()
            val colors = TimetableColorPolicy.assign(snapshot.timetable.orEmpty().map(TimetableEntry::title))
            val classes = WidgetClassPolicy.present(snapshot.timetable.orEmpty(), today.dayOfWeek.value - 1, now.hour * 60 + now.minute)
            return WidgetPresentation(title, "", snapshot.term.replace(",", "년 ") + "학기",
                "오늘 수업이 없어요", classes.map { item ->
                    val relative = when (item.phase) {
                        WidgetClassPhase.CURRENT -> "지금"
                        WidgetClassPhase.UPCOMING -> if (item.minutesUntilStart >= 60)
                            "${item.minutesUntilStart / 60}시간 ${item.minutesUntilStart % 60}분 남음" else "${item.minutesUntilStart}분 남음"
                        WidgetClassPhase.FINISHED -> "종료"
                    }
                    WidgetRow(item.entry.title,
                        if (item.phase == WidgetClassPhase.CURRENT) "${time(item.entry.endTime)}까지" else time(item.entry.startTime),
                        relative, if (item.phase == WidgetClassPhase.CURRENT) item.progress else -1,
                        color = TimetableColorPolicy.assignedColor(item.entry.title, colors))
                }, snapshot, true)
        }

        val agenda = AcademicWidgetPolicy.agenda(snapshot.calendar.orEmpty(), today.toString())
        val fetched = Instant.ofEpochMilli(snapshot.calendarFetchedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
        val delayed = System.currentTimeMillis() - snapshot.calendarFetchedAt > 2 * 60 * 60 * 1000
        val status = when {
            loading -> "업데이트 중"
            snapshot.calendarStatus == WidgetSyncStatus.NEEDS_LOGIN -> "로그인 필요"
            snapshot.calendarStatus == WidgetSyncStatus.RETRY -> "갱신 지연"
            delayed -> "갱신 지연"
            else -> ""
        }
        return WidgetPresentation(title, "오늘은 ${agenda.todayCount}개의\n일정이 있어요.",
            "$status ${fetched} 업데이트".trim(), "일정 없음", agenda.events.map {
                WidgetRow(it.title, if (it.start.substring(11) == "00:00" && it.end.substring(11) == "23:59")
                    "종일" else it.start.substring(11))
            }, snapshot, true, agenda.date, agenda.todayCount == 0)
    }

    private fun time(value: String) = value.split(':').joinToString(":") { it.padStart(2, '0') }

    @Suppress("DEPRECATION")
    fun render(context: Context, manager: AppWidgetManager, id: Int, spec: AcademicWidgetSpec) {
        val options = manager.getAppWidgetOptions(id)
        val presentation = presentation(context, spec.kind)
        val size = currentSize(context, options)
        val views = view(context, id, spec, size.first, size.second, presentation)

        manager.updateAppWidget(id, views)
        if (spec == AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.EXPANDED) &&
            AcademicWidgetPolicy.weekends(presentation.snapshot?.timetable.orEmpty()).isNotEmpty())
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_weekend_list)
        if (spec.variant == AcademicWidgetVariant.COMPACT &&
            (spec.kind == AcademicWidgetKind.CALENDAR || !isTwoColumnsOrLess(context, size.first)))
            manager.notifyAppWidgetViewDataChanged(id, R.id.academic_widget_list)
    }

    @Suppress("DEPRECATION")
    internal fun view(context: Context, id: Int, spec: AcademicWidgetSpec, width: Int, height: Int,
                     data: WidgetPresentation): RemoteViews {
        val kind = spec.kind
        val layout = if (spec.variant == AcademicWidgetVariant.EXPANDED) AcademicWidgetLayout.FULL
            else if (kind == AcademicWidgetKind.TIMETABLE && isTwoColumnsOrLess(context, width))
                AcademicWidgetLayout.COMPACT else AcademicWidgetLayout.SUMMARY
        if (layout != AcademicWidgetLayout.FULL) return smallView(context, id, kind, width, height, data, layout)
        val views = RemoteViews(context.packageName, R.layout.academic_widget)
        val open = openApp(context, id, kind)
        views.setOnClickPendingIntent(R.id.academic_widget_root, open)
        views.setPendingIntentTemplate(R.id.academic_widget_list, open)
        views.setTextViewText(R.id.academic_widget_title, data.title)
        views.setTextViewText(R.id.academic_widget_summary, data.summary)
        views.setTextViewText(R.id.academic_widget_status, data.status)
        views.setViewVisibility(R.id.academic_widget_refresh, if (kind == AcademicWidgetKind.CALENDAR) View.VISIBLE else View.GONE)
        if (kind == AcademicWidgetKind.CALENDAR)
            views.setOnClickPendingIntent(R.id.academic_widget_refresh, refreshCalendar(context, id, spec))
        views.setTextViewText(R.id.academic_widget_empty, data.empty)
        val full = layout == AcademicWidgetLayout.FULL && data.ready
        views.setViewVisibility(R.id.academic_widget_body, if (full) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.academic_widget_grid, if (full) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.academic_widget_summary,
            if (layout == AcademicWidgetLayout.SUMMARY && data.ready) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.academic_widget_status, if (height >= 100) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.academic_widget_title, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (layout == AcademicWidgetLayout.COMPACT) 11f else 16f)
        views.setViewVisibility(R.id.academic_widget_list, View.GONE)
        views.setViewVisibility(R.id.academic_widget_empty, View.VISIBLE)
        val weekends = full && kind == AcademicWidgetKind.TIMETABLE &&
            AcademicWidgetPolicy.weekends(data.snapshot?.timetable.orEmpty()).isNotEmpty()
        views.setViewVisibility(R.id.widget_weekend_section, if (weekends) View.VISIBLE else View.GONE)
        if (weekends) {
            views.setPendingIntentTemplate(R.id.widget_weekend_list, open)
            views.setRemoteAdapter(R.id.widget_weekend_list, Intent(context, AcademicWidgetListService::class.java).apply {
                putExtra("kind", kind.name)
                putExtra("weekends", true)
                putExtra("width", width)
                putExtra("height", 70)
                this.data = android.net.Uri.parse("klas-widget://weekends/$id/$width")
            })
        }
        if (full) {
            val dark = android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val gridWidth = (width - 32).coerceAtLeast(1)
            val gridHeight = (height - 80 - if (weekends) 88 else 0).coerceAtLeast(1)
            val resolution = WidgetBitmapResolution.calculate(gridWidth, gridHeight, metrics.density, metrics.widthPixels, metrics.heightPixels, 1)
            views.setImageViewBitmap(R.id.academic_widget_grid,
                WidgetBitmapCache.get(kind, data.snapshot!!, gridWidth, gridHeight, dark, resolution, metrics.widthPixels * metrics.heightPixels * 4))
            val description = if (kind == AcademicWidgetKind.CALENDAR) data.snapshot.calendar.orEmpty().joinToString("; ") {
                "${it.start.take(10)} ${it.title}"
            } else AcademicWidgetPolicy.weekdays(data.snapshot.timetable.orEmpty()).joinToString("; ") {
                "${listOf("월", "화", "수", "목", "금").getOrNull(it.day)} ${it.startTime} ${it.title}"
            }
            views.setContentDescription(R.id.academic_widget_grid, description.ifBlank { data.empty })
        }
        return views
    }
    internal fun currentSize(context: Context, options: Bundle): Pair<Int, Int> {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (landscape) options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 140) to
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
        else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140) to
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140)
    }

    fun hasSmallTimetable(context: Context): Boolean =
        ids(context, AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.COMPACT)).isNotEmpty()

    private fun openApp(context: Context, id: Int, kind: AcademicWidgetKind): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, WidgetEntryActivity::class.java).setData(android.net.Uri.parse(WidgetDestination.valueOf(kind.name).uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun refreshCalendar(context: Context, id: Int, spec: AcademicWidgetSpec): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, provider(spec)).setAction(ACTION_REFRESH_CALENDAR_WIDGET),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("DEPRECATION")
    private fun smallView(context: Context, id: Int, kind: AcademicWidgetKind, width: Int, height: Int,
                          data: WidgetPresentation, layout: AcademicWidgetLayout): RemoteViews {
        val calendar = kind == AcademicWidgetKind.CALENDAR
        val focus = !calendar && layout == AcademicWidgetLayout.COMPACT
        val focusLayouts = intArrayOf(R.layout.widget_focus_0, R.layout.widget_focus_1, R.layout.widget_focus_2,
            R.layout.widget_focus_3, R.layout.widget_focus_4, R.layout.widget_focus_5,
            R.layout.widget_focus_6, R.layout.widget_focus_7)
        val views = RemoteViews(context.packageName, if (focus) focusLayouts[data.rows.firstOrNull()?.color?.slot
            ?: WidgetClassPolicy.colorIndex(data.rows.firstOrNull()?.title.orEmpty())] else R.layout.academic_widget_agenda)
        val open = openApp(context, id, kind)
        views.setOnClickPendingIntent(R.id.academic_widget_root, open)
        val today = LocalDate.now()
        if (focus) {
            val row = data.rows.firstOrNull()
            views.setTextViewText(R.id.widget_focus_label, row?.relative?.let { if (it.endsWith("남음")) "다음 수업" else it } ?: "오늘")
            views.setTextViewText(R.id.widget_focus_title, row?.title ?: data.empty)
            views.setTextViewText(R.id.widget_focus_detail, row?.let { if (it.relative.endsWith("남음")) it.relative else it.detail }.orEmpty())
            views.setViewVisibility(R.id.widget_focus_progress, if (row != null && row.progress >= 0) View.VISIBLE else View.GONE)
            views.setProgressBar(R.id.widget_focus_progress, 100, row?.progress ?: 0, false)
            if (height < 100) {
                views.setViewPadding(R.id.academic_widget_root, dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                views.setTextViewTextSize(R.id.widget_focus_label, android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
                views.setTextViewTextSize(R.id.widget_focus_detail, android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            return views
        }
        views.setViewVisibility(R.id.widget_date_header, if (calendar) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_date_rail, if (calendar) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_date_header, today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)))
        views.setTextViewText(R.id.widget_weekday, today.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale.KOREAN)))
        views.setTextViewText(R.id.widget_day, today.dayOfMonth.toString())
        views.setTextViewText(R.id.academic_widget_empty, data.empty)
        val later = calendar && data.ready && data.todayEmpty && data.rows.isNotEmpty()
        views.setViewVisibility(R.id.widget_today_empty, if (later) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_next_date, if (later) View.VISIBLE else View.GONE)
        if (later) views.setTextViewText(R.id.widget_next_date, LocalDate.parse(data.agendaDate)
            .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)))
        views.setTextViewText(R.id.academic_widget_status, data.status)
        views.setViewVisibility(R.id.academic_widget_status, if (calendar && height >= 180) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.academic_widget_refresh, if (calendar) View.VISIBLE else View.GONE)
        if (calendar) views.setOnClickPendingIntent(R.id.academic_widget_refresh,
            refreshCalendar(context, id, AcademicWidgetSpec(kind, AcademicWidgetVariant.COMPACT)))
        views.setPendingIntentTemplate(R.id.academic_widget_list, open)
        views.setRemoteAdapter(R.id.academic_widget_list, Intent(context, AcademicWidgetListService::class.java).apply {
            putExtra("kind", kind.name)
            putExtra("width", width)
            putExtra("height", height)
            this.data = android.net.Uri.parse("klas-widget://list/${kind.name}/$id/$width/$height")
        })
        views.setEmptyView(R.id.academic_widget_list, R.id.academic_widget_empty)
        if (width < 170) {
            views.setViewPadding(R.id.academic_widget_root, dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            views.setTextViewTextSize(R.id.widget_date_header, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            if (!calendar) {
                views.setViewVisibility(R.id.widget_date_rail, View.GONE)
                views.setViewVisibility(R.id.widget_date_header, View.VISIBLE)
            }
        }
        if (height < 100) views.setViewPadding(R.id.academic_widget_root, dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        if (height < 100 && later) {
            views.setViewVisibility(R.id.widget_today_empty, View.GONE)
            views.setViewVisibility(R.id.widget_next_date, View.GONE)
            views.setTextViewText(R.id.widget_date_header, LocalDate.parse(data.agendaDate).format(DateTimeFormatter.ofPattern("M/d E", java.util.Locale.KOREAN)) + " · 가까운 일정")
        }
        return views
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

}

class AcademicWidgetListService : RemoteViewsService() {
    private fun rowLayout(row: WidgetRow) = intArrayOf(R.layout.widget_class_row_0, R.layout.widget_class_row_1,
        R.layout.widget_class_row_2, R.layout.widget_class_row_3, R.layout.widget_class_row_4, R.layout.widget_class_row_5,
        R.layout.widget_class_row_6, R.layout.widget_class_row_7)[row.color?.slot
        ?: WidgetClassPolicy.colorIndex(row.title)]

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind = runCatching { AcademicWidgetKind.valueOf(intent.getStringExtra("kind").orEmpty()) }
            .getOrDefault(AcademicWidgetKind.TIMETABLE)
        return object : RemoteViewsFactory {
            private var rows = emptyList<WidgetRow>()
            override fun onCreate() = onDataSetChanged()
            override fun onDataSetChanged() {
                rows = if (intent.getBooleanExtra("weekends", false)) {
                    AcademicWidgetPolicy.weekends(applicationContext.appDependencies.academicWidgets.snapshot()?.timetable.orEmpty()).map {
                        WidgetRow(it.title, "")
                    }
                } else AcademicWidgets.presentation(applicationContext, kind).rows
            }
            override fun onDestroy() { rows = emptyList() }
            override fun getCount() = rows.size
            override fun getViewAt(position: Int): RemoteViews? = rows.getOrNull(position)?.let { row ->
                if (intent.getBooleanExtra("weekends", false)) {
                    return@let RemoteViews(packageName, R.layout.academic_widget_weekend_row).apply {
                        setTextViewText(R.id.academic_widget_row_title, row.title)
                        setOnClickFillInIntent(R.id.academic_widget_row, Intent())
                    }
                }
                val dark = android.content.res.Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val color = row.color?.let { WidgetColors.text(it, dark) } ?: WidgetColors.text(row.colorKey, dark)
                RemoteViews(packageName, if (kind == AcademicWidgetKind.CALENDAR) R.layout.academic_widget_calendar_row
                    else rowLayout(row)).apply {
                    setTextViewText(R.id.academic_widget_row_title, row.title)
                    setTextViewText(R.id.academic_widget_row_detail, row.detail)
                    if (kind == AcademicWidgetKind.TIMETABLE) {
                        if (intent.getIntExtra("height", 140) < 100) {
                            val density = resources.displayMetrics.density
                            setViewPadding(R.id.academic_widget_row, (6 * density).toInt(), (1 * density).toInt(),
                                (6 * density).toInt(), (1 * density).toInt())
                            setTextViewTextSize(R.id.academic_widget_row_title, android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                            setTextViewTextSize(R.id.academic_widget_row_detail, android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                        }
                        setTextColor(R.id.academic_widget_row_title, color)
                        setTextColor(R.id.widget_row_relative, color)
                        setTextViewText(R.id.widget_row_relative, row.relative)
                        if (intent.getIntExtra("width", 140) < 250 && row.relative.endsWith("남음"))
                            setTextViewText(R.id.academic_widget_row_detail, "${row.detail} · ${row.relative}")
                        setViewVisibility(R.id.widget_row_relative, if (intent.getIntExtra("width", 140) >= 250) View.VISIBLE else View.GONE)
                        setViewVisibility(R.id.widget_row_progress, if (row.progress >= 0) View.VISIBLE else View.GONE)
                        setProgressBar(R.id.widget_row_progress, 100, row.progress.coerceAtLeast(0), false)
                    } else setInt(R.id.widget_calendar_bar, "setBackgroundColor", color)
                    setOnClickFillInIntent(R.id.academic_widget_row, Intent())
                }
            }
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewTypeCount() = TimetableColorPolicy.defaultPalette.light.size
            override fun getItemId(position: Int) = position.toLong()
            override fun hasStableIds() = false
        }
    }
}
