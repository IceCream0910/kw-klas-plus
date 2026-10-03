package com.icecream.kwklasplus.core.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DeadlineConsentState(val enabled: Boolean,val pending: Boolean,val permission: String,val status: String,val ready: Boolean)
class DeadlineNotificationConsent(private val engine: ReminderEngine,private val changed: ()->Unit) {
    private val mutex=Mutex()
    private var sequence=0L
    private var attempt=0L
    private var captured: ReminderLedger?=null
    private var status="IDLE"
    suspend fun state(): DeadlineConsentState=mutex.withLock { stateLocked() }
    private suspend fun stateLocked(): DeadlineConsentState {
        val current=engine.snapshot()
        if(captured?.let { it.ownerHash!=current.ownerHash || it.generation!=current.generation || it.revision!=current.revision }==true || !engine.canConfigure()) {
            captured=null;attempt=0;status="CANCELLED"
        }
        val settings=engine.settings()
        return DeadlineConsentState(settings.enabled && settings.permission in listOf("authorized","provisional"),attempt!=0L,settings.permission,status,settings.ready)
    }
    suspend fun request(enabled: Boolean,ui: ReminderSettingsUi): DeadlineConsentState=mutex.withLock {
        if(!enabled) {
            engine.setEnabled(false);captured=null;attempt=0;status="DISABLED";changed()
        } else if(attempt==0L) {
            val current=engine.snapshot()
            check(engine.canConfigure() && current.ownerHash.isNotBlank()) { "NOT_AUTHENTICATED" }
            captured=current;attempt=++sequence;status="AWAITING_PERMISSION"
            val opened=try { ui.open(attempt) } catch(cause: Exception) {
                captured=null;attempt=0;status="OPEN_FAILED"
                if(cause is CancellationException)throw cause
                false
            }
            if(!opened) { captured=null;attempt=0;status="OPEN_FAILED" }
        }
        stateLocked()
    }
    suspend fun complete(id: Long): String=mutex.withLock {
        val saved=captured ?: return@withLock "CANCELLED"
        if(id!=attempt)return@withLock "CANCELLED"
        if(!engine.enableAfterConsent(saved))return@withLock stateLocked().let { if(it.pending)"PERMISSION_DENIED" else "CANCELLED" }
        captured=null;attempt=0;status="COMPLETED";changed();status
    }
    suspend fun cancel(id: Long)=mutex.withLock {
        if(id==attempt) { captured=null;attempt=0;status="CANCELLED" }
    }
}
