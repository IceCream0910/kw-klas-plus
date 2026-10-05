package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.IosSharedDependencies
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume

data class IosReminderLedgerRead(val value: String?, val success: Boolean)

interface IosReminderHost {
    fun isBackground(): Boolean
    fun readLedger(): IosReminderLedgerRead
    fun writeLedger(value: String): Boolean
    fun hash(value: String): String
    fun permission(kind: String,done: (String)->Unit)
    fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,title: String,body: String,done: (String)->Unit)
    fun openDeadlineSettings(attempt: Long): Boolean
    fun cancelRefresh()
    fun preferencesChanged()
    fun cancel(id: String)
    fun cancelAll(done: (String)->Unit)
}
object IosReminderRuntime {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var engine: ReminderEngine?=null
    private var consent: DeadlineNotificationConsent?=null
    private var host: IosReminderHost?=null
    fun install(host: IosReminderHost,dependencies: IosSharedDependencies,tokenEncryptor: com.icecream.kwklasplus.core.auth.LoginTokenEncryptor) {
        if(engine!=null)return
        this.host=host
        val adapter=object: ReminderPlatform {
            override fun hash(value: String)=host.hash(value)
            override suspend fun permission(kind: String)=awaitString { host.permission(kind,it) }
            override suspend fun postDetailed(id: String,kind: String,generation: Long,additional: Boolean,message: ReminderMessage)=awaitString { host.postDetailed(id,kind,generation,additional,message.title,message.body,it) }=="ok"
            override suspend fun cancel(id: String) { host.cancel(id) }
            override suspend fun cancelAll() { awaitString { host.cancelAll(it) } }
        }
        val store=object: ReminderStore {
            override suspend fun read(): String? {
                val result=host.readLedger()
                check(result.success) { "STORAGE_FAILED" }
                return result.value
            }
            override suspend fun write(value: String) { check(host.writeLedger(value)) { "STORAGE_FAILED" } }
        }
        engine=ReminderEngine(store,adapter,ReminderIdentityProvider {
            ReminderIdentity(dependencies.stringPreference("kwID").orEmpty(),dependencies.stringPreference("yearHakgi").orEmpty(),dependencies.stringPreference("login_funnel_status")=="complete",dependencies.stringPreference("login_funnel_status") in listOf("setup","complete"))
        },SessionRefreshingReminderSource(dependencies.reminderSource) {
            dependencies.reminderSessionRecovery(tokenEncryptor).recover({ dependencies.credentialStore.load() },KlasUserAgent.fromPlatform("KLAS+ iOS reminders"))
        },Clock { (NSDate().timeIntervalSince1970*1000).toLong() },ReminderDeliveryGate { host.isBackground() })
    }
    fun wrap(fallback: BridgeCommandHandler): BridgeCommandHandler {
        val h=host ?: return fallback
        val c=configurationConsent() ?: return fallback
        return ReminderBridgeHandler(c,ReminderSettingsUi { h.openDeadlineSettings(attempt=it) },fallback)
    }
    private fun configurationConsent(): DeadlineNotificationConsent? {
        val h=host ?: return null
        return consent ?: DeadlineNotificationConsent(engine ?: return null) { h.preferencesChanged() }.also { consent=it }
    }
    fun beginNativeConsent(done: (Long,String)->Unit) {
        scope.launch {
            try {
                var attempt=0L
                val state=configurationConsent()?.request(true,ReminderSettingsUi { attempt=it;true })
                done(attempt,if(state?.pending==true && attempt!=0L) "" else "알림 설정을 시작하지 못했어요.")
            } catch(cause: Exception) { if(cause is CancellationException)throw cause;done(0,"알림 설정을 시작하지 못했어요.") }
        }
    }
    fun readConsentState(done: (DeadlineConsentState?)->Unit) {
        scope.launch { done(runCatching { configurationConsent()?.state() }.getOrNull()) }
    }
    fun readSettings(done: (DeadlineNotificationSettings?, String)->Unit) {
        scope.launch { try { done(engine?.settings(),"") } catch(cause: Exception) { if(cause is CancellationException)throw cause;done(null,"알림 설정을 읽지 못했어요.") } }
    }
    fun completeConsent(attempt: Long,done: (String)->Unit) {
        scope.launch { try { done(consent?.complete(attempt) ?: "CANCELLED") } catch(cause: Exception) { if(cause is CancellationException)throw cause;done("STORAGE_FAILED") } }
    }
    fun cancelConsent(attempt: Long) { scope.launch { consent?.cancel(attempt) } }
    fun allowsPresentation(generation: Long,kind: String,occurrenceId: String,done: (Boolean)->Unit) {
        scope.launch { done(runCatching { engine?.snapshot()?.let { state -> engine?.isReady()==true && kind=="deadline" && state.ownerHash.isNotBlank() && state.generation==generation && state.deadlineEnabled && state.claims.any { it.batchId==occurrenceId && it.ownerHash==state.ownerHash } } ?: false }.getOrDefault(false)) }
    }
    fun isEnabled(done: (Boolean)->Unit) { scope.launch { done(runCatching { engine?.snapshot()?.let { engine?.isReady()==true && it.ownerHash.isNotBlank() && it.deadlineEnabled } ?: false }.getOrDefault(false)) } }
    suspend fun beginDeadlineRefresh(): ReminderDeadlineTicket? = engine?.beginHomeDeadlineRefresh()
    suspend fun acceptHomeDeadlines(ticket: ReminderDeadlineTicket?,result: com.icecream.kwklasplus.core.academic.DeadlinesResult) { if(ticket!=null)engine?.acceptHomeDeadlines(ticket,result) }
    fun foreground() { scope.launch { runCatching { engine?.snapshot() } } }
    fun refresh(done: (String)->Unit): IosReminderRefreshTask {
        val job=scope.launch {
            try { engine?.refresh();done("ok") } catch(cause: Exception) { if(cause is CancellationException)throw cause;done("retry") }
        }
        return IosReminderRefreshTask(job)
    }
    suspend fun clear() { try { engine?.clear() } finally { host?.cancelRefresh() } }
    private suspend fun awaitString(action: ((String)->Unit)->Unit): String=suspendCancellableCoroutine { c -> action { if(c.isActive)c.resume(it) } }
}
class IosReminderRefreshTask internal constructor(private val job: Job) { fun cancel()=job.cancel() }
