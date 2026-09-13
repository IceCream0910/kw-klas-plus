package com.icecream.kwklasplus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.core.academic.WidgetClassPolicy
import java.time.LocalDate
import java.time.LocalTime

object WidgetDisplayRefresh {
    private var registered = false
    private var lastMinute = -1L

    fun start(context: Context) {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                    AcademicWidgets.renderAll(context)
                    schedule(context)
                } else tick(context, intent.action == Intent.ACTION_SCREEN_ON)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }

    private fun active(context: Context): Boolean {
        if (!AcademicWidgets.hasSmallTimetable(context)) return false
        val now = LocalTime.now()
        return WidgetClassPolicy.needsMinuteRefresh(context.appDependencies.academicWidgets.snapshot()?.timetable.orEmpty(),
            LocalDate.now().dayOfWeek.value - 1, now.hour * 60 + now.minute, hasCompactWidget = true)
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 7204, Intent(context, WidgetDisplayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if (!active(context)) return
        // 비정확·비기상 알람은 프로세스 종료 후의 복구용이며 화면이 켜진 동안 TIME_TICK으로 보완한다.
        val delay = 60_000 - System.currentTimeMillis() % 60_000
        alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pending)
    }

    fun tick(context: Context, force: Boolean = false) {
        val minute = System.currentTimeMillis() / 60_000
        if (context.getSystemService(PowerManager::class.java).isInteractive && (force || minute != lastMinute) &&
            (force || active(context))) {
            lastMinute = minute
            AcademicWidgets.renderAll(context, compactOnly = true)
        }
        schedule(context)
    }
}

class WidgetDisplayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WidgetDisplayRefresh.tick(context)
}
