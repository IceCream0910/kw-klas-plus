package com.icecream.kwklasplus.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.icecream.kwklasplus.appDependencies
import kotlinx.coroutines.*
import java.time.Duration
import java.time.ZonedDateTime

object AcademicWidgetScheduler {
    private const val CALENDAR_JOB = 7201
    private const val DATE_JOB = 7202
    private const val REFRESH_JOB = 7203

    fun requestCalendar(context: Context, replacePending: Boolean = false) {
        if (!AcademicWidgets.hasCalendar(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (replacePending || scheduler.getPendingJob(REFRESH_JOB) == null) {
            scheduler.schedule(JobInfo.Builder(REFRESH_JOB, ComponentName(context, AcademicWidgetJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(0)
                .setBackoffCriteria(15 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
        }
    }

    fun schedule(context: Context) {
        WidgetDisplayRefresh.schedule(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(context, AcademicWidgetJobService::class.java)
        if (AcademicWidgets.hasCalendar(context)) {
            if (scheduler.getPendingJob(CALENDAR_JOB) == null) {
                scheduler.schedule(JobInfo.Builder(CALENDAR_JOB, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(60 * 60 * 1000L, 15 * 60 * 1000L)
                    .setBackoffCriteria(15 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setPersisted(true).build())
            }
        } else {
            scheduler.cancel(CALENDAR_JOB)
            scheduler.cancel(REFRESH_JOB)
        }
        if (AcademicWidgets.hasAny(context)) {
            if (scheduler.getPendingJob(DATE_JOB) == null) scheduleDate(context)
        } else scheduler.cancel(DATE_JOB)
    }

    fun scheduleDate(context: Context) {
        if (!AcademicWidgets.hasAny(context)) return
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(1000)
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(DATE_JOB, ComponentName(context, AcademicWidgetJobService::class.java))
                .setMinimumLatency(delay).setOverrideDeadline(delay + 15 * 60 * 1000L)
                .setPersisted(true).build(),
        )
    }

    fun isDateJob(id: Int) = id == DATE_JOB
}

class AcademicWidgetJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch {
            val retry = if (AcademicWidgetScheduler.isDateJob(params.jobId)) {
                appDependencies.academicWidgets.render()
                AcademicWidgetScheduler.requestCalendar(this@AcademicWidgetJobService)
                false
            } else appDependencies.academicWidgets.syncCalendar()
            jobs.remove(params.jobId)
            jobFinished(params, retry)
            if (AcademicWidgetScheduler.isDateJob(params.jobId)) AcademicWidgetScheduler.scheduleDate(this@AcademicWidgetJobService)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
