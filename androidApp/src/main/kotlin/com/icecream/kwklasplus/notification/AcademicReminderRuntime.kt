package com.icecream.kwklasplus.notification

import android.app.Activity
import android.app.job.*
import android.content.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.icecream.kwklasplus.*
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.notification.*
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus
import kotlinx.coroutines.*

class AcademicReminderRuntime(private val context: Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val platform=AndroidReminderPlatform(context,MainActivity::class.java,R.drawable.ic_academic_reminder)
    val engine=ReminderEngine(AndroidReminderStore(context),platform,ReminderIdentityProvider {
        ReminderIdentity(context.appPreferences.getString("kwID",null).orEmpty(),context.appPreferences.getString("yearHakgi",null).orEmpty(),!LoginFunnelStatus.blocksHome(context.appPreferences.getString(LoginFunnelStatus.KEY,null)),context.appPreferences.getString(LoginFunnelStatus.KEY,null) in listOf(LoginFunnelStatus.SETUP,LoginFunnelStatus.COMPLETE))
    },SessionRefreshingReminderSource(context.appDependencies.reminderSource) {
        val dependencies=context.appDependencies
        dependencies.reminderSessionRecovery.recover({ dependencies.credentialStore.load() },
            com.icecream.kwklasplus.core.network.KlasUserAgent.fromPlatform("KLAS+ Android reminders"))
    },Clock(System::currentTimeMillis),ReminderDeliveryGate {
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    })
    private val mutableSettingsRevision=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val settingsRevision: kotlinx.coroutines.flow.StateFlow<Long> = mutableSettingsRevision
    val consent=DeadlineNotificationConsent(engine) {
        mutableSettingsRevision.value+=1
        foreground()
    }
    fun cancelConsent(attempt: Long) { scope.launch { consent.cancel(attempt) } }
    fun foreground() { scope.launch { runCatching { engine.snapshot();updateJobs() } } }
    fun requestRefresh(done: () -> Unit = {}) { scope.launch { try { runCatching { updateJobs();engine.refresh() } } finally { done() } } }
    suspend fun logout() { try { engine.clear() } finally { context.getSystemService(JobScheduler::class.java).cancel(JOB) } }
    suspend fun updateJobs() {
        val state=engine.snapshot();val scheduler=context.getSystemService(JobScheduler::class.java)
        if(engine.isReady() && state.ownerHash.isNotBlank() && state.deadlineEnabled) {
            val pending=scheduler.getPendingJob(JOB)
            if(pending==null || pending.intervalMillis!=REFRESH_INTERVAL_MILLIS || pending.flexMillis!=REFRESH_FLEX_MILLIS) {
                scheduler.schedule(JobInfo.Builder(JOB,ComponentName(context,AcademicReminderJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(REFRESH_INTERVAL_MILLIS,REFRESH_FLEX_MILLIS)
                    .setPersisted(true)
                    .setBackoffCriteria(REFRESH_FLEX_MILLIS,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
            }
        } else scheduler.cancel(JOB)
    }
    fun wrap(activity: Activity,fallback: BridgeCommandHandler)=ReminderBridgeHandler(consent,ReminderSettingsUi { attempt ->
        if(activity.isFinishing || activity.isDestroyed)false else {
            activity.runOnUiThread { activity.startActivity(Intent(activity,DeadlineNotificationSettingsActivity::class.java).putExtra("consent_attempt",attempt)) };true
        }
    },fallback)
    companion object {
        const val JOB=7311
        const val REFRESH_INTERVAL_MILLIS=60*60*1000L
        const val REFRESH_FLEX_MILLIS=15*60*1000L
    }
}
class AcademicReminderJobService: JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var job: Job?=null
    override fun onStartJob(params: JobParameters): Boolean {
        job=scope.launch {
            val retry=try { appDependencies.reminders.engine.refresh();false } catch(cause: Exception) { if(cause is CancellationException)throw cause;true }
            jobFinished(params,retry)
        };return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel();return true }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
