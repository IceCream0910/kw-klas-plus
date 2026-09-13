package com.icecream.kwklasplus.widget

import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.icecream.kwklasplus.core.academic.*
import java.time.LocalDate
import java.time.YearMonth

internal object AcademicWidgetDrawing {
    fun draw(kind: AcademicWidgetKind, snapshot: AcademicWidgetSnapshot, width: Int, height: Int, dark: Boolean,
             resolution: WidgetBitmapResolution = WidgetBitmapResolution(width, height)): Bitmap {
        val bitmap = Bitmap.createBitmap(resolution.width, resolution.height, Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        val canvas = Canvas(bitmap)
        canvas.scale(resolution.width.toFloat() / width, resolution.height.toFloat() / height)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val ink = Color.parseColor(if (dark) "#FAF0F6" else "#231B20")
        val grid = Color.parseColor(if (dark) "#51434D" else "#E2D7DF")
        if (kind == AcademicWidgetKind.CALENDAR) calendar(canvas, pen, snapshot.calendar.orEmpty(), width.toFloat(), height.toFloat(), ink, grid)
        else {
            val entries = snapshot.timetable.orEmpty()
            val colors = TimetableColorPolicy.assign(entries.map(TimetableEntry::title))
            timetable(canvas, pen, AcademicWidgetPolicy.weekdays(entries), colors, width.toFloat(), height.toFloat(), ink, grid, dark)
        }
        return bitmap
    }

    private fun text(canvas: Canvas, pen: Paint, value: String, x: Float, baseline: Float, width: Float, size: Float, color: Int) {
        if (width <= 0) return
        pen.color = color
        pen.textSize = size
        pen.style = Paint.Style.FILL
        var label = value
        if (pen.measureText(label) > width) {
            val count = pen.breakText(label, true, (width - pen.measureText("…")).coerceAtLeast(0f), null)
            label = label.take(count) + "…"
        }
        canvas.drawText(label, x, baseline, pen)
    }

    private fun calendar(canvas: Canvas, pen: Paint, events: List<CalendarEvent>, width: Float, height: Float, ink: Int, grid: Int) {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val offset = month.atDay(1).dayOfWeek.value % 7
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        val header = 24f
        val cellW = width / 7
        val cellH = (height - header) / weeks
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { col, label ->
            text(canvas, pen, label, col * cellW + 4, 17f, cellW - 6, 14f, if (col == 0) Color.rgb(204, 80, 103) else ink)
        }
        for (week in 0..weeks) {
            pen.color = grid
            canvas.drawLine(0f, header + week * cellH, width, header + week * cellH, pen)
        }
        for (day in 1..month.lengthOfMonth()) {
            val index = day - 1 + offset
            val x = index % 7 * cellW
            val y = header + index / 7 * cellH
            if (day == today.dayOfMonth) {
                pen.color = Color.rgb(129, 56, 99)
                canvas.drawRoundRect(x + 1, y + 2, x + 25, y + 22, 6f, 6f, pen)
            }
            text(canvas, pen, day.toString(), x + 4, y + 17, cellW - 6, 13f,
                if (day == today.dayOfMonth) Color.WHITE else ink)
        }
        val barHeight = 19f
        val lanes = ((cellH - 25) / barHeight).toInt().coerceAtLeast(0)
        val bars = AcademicWidgetPolicy.monthBars(events, month.toString(), month.lengthOfMonth(), offset)
        val overflow = mutableMapOf<Pair<Int, Int>, Int>()
        bars.forEach { bar ->
            if (bar.lane >= lanes) {
                for (column in bar.column until bar.column + bar.span) {
                    val key = bar.week to column
                    overflow[key] = (overflow[key] ?: 0) + 1
                }
            } else {
                val x = bar.column * cellW + 1
                val y = header + bar.week * cellH + 25 + bar.lane * barHeight
                val bottom = minOf(y + barHeight - 1, header + (bar.week + 1) * cellH - 1)
                if (bottom > y) {
                    val color = eventColor(bar.event.color, bar.event.title)
                    pen.color = color
                    canvas.drawRoundRect(x, y, (bar.column + bar.span) * cellW - 1, bottom, 4f, 4f, pen)
                    if (bottom - y >= 15) text(canvas, pen, bar.event.title, x + 4, y + 13,
                        bar.span * cellW - 9, 11f, contrasting(color))
                }
            }
        }
        overflow.forEach { (key, count) ->
            val x = key.second * cellW + cellW - 27
            val y = header + key.first * cellH + 18
            text(canvas, pen, "+$count", x, y, 27f, 10f, ink)
        }

    }

    private fun timetable(canvas: Canvas, pen: Paint, entries: List<TimetableEntry>, colors: Map<String, TimetableColor>, width: Float, height: Float, ink: Int, grid: Int, dark: Boolean) {
        if (entries.isEmpty()) {
            text(canvas, pen, "평일 수업이 없어요.", 8f, height / 2, width - 16, 12f, ink)
            return
        }
        val days = 5
        val firstHour = entries.minOf { AcademicWidgetPolicy.minutes(it.startTime) / 60 }.coerceIn(0, 23)
        val lastHour = entries.maxOf { (AcademicWidgetPolicy.minutes(it.endTime) + 59) / 60 }.coerceIn(firstHour + 1, 24)
        val left = 30f
        val top = 26f
        val cellW = (width - left) / days
        val minuteH = (height - top) / ((lastHour - firstHour) * 60)
        listOf("월", "화", "수", "목", "금", "토").take(days).forEachIndexed { index, label ->
            text(canvas, pen, label, left + index * cellW + 4, 18f, cellW - 6, 14f, ink)
        }
        for (hour in firstHour..lastHour) {
            val y = top + (hour - firstHour) * 60 * minuteH
            pen.color = grid
            canvas.drawLine(left, y, width, y, pen)
            if (y < height - 12) text(canvas, pen, "$hour", 1f, y + 12, left - 4, 11f, ink)
        }
        for (day in 0..days) {
            pen.color = grid
            canvas.drawLine(left + day * cellW, top, left + day * cellW, height, pen)
        }
        entries.filter { it.day in 0 until days }.forEach { entry ->
            val x = left + entry.day * cellW + 1
            val y = top + (AcademicWidgetPolicy.minutes(entry.startTime) - firstHour * 60) * minuteH
            val end = top + (AcademicWidgetPolicy.minutes(entry.endTime) - firstHour * 60) * minuteH
            val subjectColor = TimetableColorPolicy.assignedColor(entry.title, colors)
            pen.color = WidgetColors.fill(subjectColor, dark)
            canvas.drawRoundRect(x, y + 1, x + cellW - 2, end.coerceAtLeast(y + 3), 5f, 5f, pen)
            val save = canvas.save()
            canvas.clipRect(x, y + 1, x + cellW - 2, end)
            val contentWidth = (cellW - 10).toInt().coerceAtLeast(1)
            val availableHeight = (end - y - 8).coerceAtLeast(0f)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = 13f
                this.color = WidgetColors.text(subjectColor, dark)
                typeface = Typeface.DEFAULT_BOLD
            }
            val title = StaticLayout.Builder.obtain(entry.title, 0, entry.title.length, titlePaint, contentWidth)
                .setIncludePad(false).setMaxLines(if (availableHeight >= 32) 2 else 1)
                .setEllipsize(TextUtils.TruncateAt.END).build()
            canvas.translate(x + 4, y + 4)
            title.draw(canvas)
            if (availableHeight - title.height >= 15) text(canvas, pen, entry.info.substringBefore('/'),
                0f, title.height + 13f, contentWidth.toFloat(), 11f, WidgetColors.text(subjectColor, dark))
            canvas.restoreToCount(save)
        }
    }

    private fun eventColor(value: String, seed: String): Int {
        if (Regex("#[0-9a-fA-F]{6}").matches(value)) return Color.parseColor(value)
        val colors = intArrayOf(0xFF895275.toInt(), 0xFF426C95.toInt(), 0xFF467D6A.toInt(), 0xFF9E633D.toInt(), 0xFF76599A.toInt())
        return colors[(seed.hashCode() and Int.MAX_VALUE) % colors.size]
    }

    private fun contrasting(color: Int): Int = if (Color.red(color) * .299 + Color.green(color) * .587 + Color.blue(color) * .114 > 160) Color.rgb(25, 20, 25) else Color.WHITE
}
