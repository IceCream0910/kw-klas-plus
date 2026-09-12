package com.icecream.kwklasplus.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.core.academic.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class AcademicWidgetsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun compactTimetableUsesSingleClassCardThroughTwoColumns() {
        val portrait = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        })
        val landscape = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        })
        assertTrue(AcademicWidgets.isTwoColumnsOrLess(portrait, 130))
        assertTrue(AcademicWidgets.isTwoColumnsOrLess(portrait, 160))
        assertFalse(AcademicWidgets.isTwoColumnsOrLess(portrait, 203))
        assertTrue(AcademicWidgets.isTwoColumnsOrLess(landscape, 269))
        assertFalse(AcademicWidgets.isTwoColumnsOrLess(landscape, 412))
    }

    @Test fun widgetDestinationSurvivesAuthenticationHopsAndRejectsOtherUris() {
        for (destination in WidgetDestination.entries) {
            val origin = android.content.Intent().setData(android.net.Uri.parse(destination.uri))
            val login = WidgetNavigation.forward(origin, android.content.Intent())
            val main = WidgetNavigation.forward(login, android.content.Intent())
            val home = WidgetNavigation.forward(main, android.content.Intent())
            assertEquals(destination.uri, home.dataString)
        }
        assertNull(WidgetNavigation.forward(android.content.Intent().setData(android.net.Uri.parse("https://example.com")),
            android.content.Intent()).data)
    }

    @Test fun expandedTimetableShowsScrollableWeekendSectionOnlyWhenNeeded() {
        instrumentation.runOnMainSync {
            for (entries in listOf(emptyList(), listOf(TimetableEntry("일요일 수업", 6, "9:0", "10:0", "301", "S")))) {
                val snapshot = AcademicWidgetSnapshot("fixture", "2026,2", timetable = entries)
                val data = WidgetPresentation("시간표", "", "", "수업 없음", emptyList(), snapshot, true)
                val root = AcademicWidgets.view(context, 9876,
                    AcademicWidgetSpec(AcademicWidgetKind.TIMETABLE, AcademicWidgetVariant.EXPANDED), 350, 400, data)
                    .apply(context, FrameLayout(context))
                assertEquals(if (entries.isEmpty()) View.GONE else View.VISIBLE,
                    root.findViewById<View>(R.id.widget_weekend_section).visibility)
            }
        }
    }

    @Test fun remoteViewsInflateAcrossEveryRequestedSizeAndRenderMonthlyBars() {
        val month = YearMonth.now()
        val entries = listOf(
            TimetableEntry("자료구조", 0, "9:0", "10:15", "새빛관 301/교수", "A"),
            TimetableEntry("소프트웨어 설계", 2, "10:30", "11:45", "참빛관 401/교수", "B"),
            TimetableEntry("운영체제", 1, "13:30", "14:45", "새빛관 201/교수", "C"),
            TimetableEntry("컴퓨터 네트워크", 4, "15:0", "16:15", "비마관 202/교수", "D"),
        )
        val events = listOf(
            CalendarEvent("1", "수강신청 변경", "${month.atDay(1)}T00:00", "${month.atDay(9)}T23:59", "학사일정", "#895275", ""),
            CalendarEvent("2", "팀 프로젝트", "${month.atDay(4)}T12:00", "${month.atDay(4)}T13:00", "개인일정", "#426C95", ""),
            CalendarEvent("3", "과제 제출", "${month.atDay(15)}T00:00", "${month.atDay(15)}T23:59", "과제", "#D8AC59", ""),
        )
        val snapshot = AcademicWidgetSnapshot("fixture", "2026,2", entries, 1, month.toString(), events, 1)
        instrumentation.runOnMainSync {
            for (kind in AcademicWidgetKind.entries) for (variant in AcademicWidgetVariant.entries) {
                val sizes = if (variant == AcademicWidgetVariant.COMPACT)
                    listOf(130 to 110, 160 to 140, 180 to 140, 280 to 140, 350 to 180, 350 to 400)
                else listOf(280 to 250, 350 to 400)
                for ((width, height) in sizes) {
                    val data = WidgetPresentation(if (kind == AcademicWidgetKind.TIMETABLE) "시간표" else "${month.monthValue}월 일정",
                        "오늘은 2개의\n일정이 있어요.", "9/4 12:00 확인", "일정 없음", emptyList(), snapshot, true)
                    val root = AcademicWidgets.view(context, 9876, AcademicWidgetSpec(kind, variant), width, height, data)
                        .apply(context, FrameLayout(context))
                    if (kind == AcademicWidgetKind.CALENDAR)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.academic_widget_refresh).visibility)
                    val full = variant == AcademicWidgetVariant.EXPANDED
                    if (full) assertEquals(View.VISIBLE, root.findViewById<View>(R.id.academic_widget_grid).visibility)
                    else {
                        assertNull(root.findViewById<View>(R.id.academic_widget_grid))
                        val focus = kind == AcademicWidgetKind.TIMETABLE &&
                            AcademicWidgets.isTwoColumnsOrLess(context, width)
                        assertNotNull(root.findViewById<View>(if (focus) R.id.widget_focus_title else R.id.academic_widget_list))
                    }
                    val density = context.resources.displayMetrics.density
                    val w = (width * density).toInt()
                    val h = (height * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null), "widget-${kind.name}-${variant.name}-$width-$height.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }

    @Test fun missingDataDoesNotContainGridOrSummary() {
        instrumentation.runOnMainSync {
            val data = WidgetPresentation("캘린더", "", "앱을 눌러 확인", "로그인해주세요", emptyList(), null, false)
            val root = AcademicWidgets.view(context, 9877,
                AcademicWidgetSpec(AcademicWidgetKind.CALENDAR, AcademicWidgetVariant.EXPANDED), 280, 280, data)
                .apply(context, FrameLayout(context))
            assertEquals(View.GONE, root.findViewById<View>(R.id.academic_widget_grid).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.academic_widget_summary).visibility)
            assertEquals(data.empty, root.findViewById<TextView>(R.id.academic_widget_empty).text.toString())
        }
    }
}
