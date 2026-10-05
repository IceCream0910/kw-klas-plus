package com.icecream.kwklasplus.core.notification

import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReminderEngine(
    private val store: ReminderStore,
    private val platform: ReminderPlatform,
    private val identity: ReminderIdentityProvider,
    private val source: ReminderDataSource,
    private val clock: Clock,
    private val deliveryGate: ReminderDeliveryGate = ReminderDeliveryGate { true },
) {
    private val mutex=Mutex()
    private val refreshMutex=Mutex()
    private val json=Json { encodeDefaults=true; ignoreUnknownKeys=true }
    private var latestDeadlineRequest=0L
    private var latestHomeRequest=0L
    private var latestHomeAcceptedRequest=0L
    private var requestSequence=0L
    private var ledger: ReminderLedger?=null
    private suspend fun load(): ReminderLedger {
        ledger?.let { return it }
        val raw=store.read()
        return try {
            (raw?.let { json.decodeFromString<ReminderLedger>(it) } ?: ReminderLedger()).also {
                require(it.schemaVersion==1); ledger=it
            }
        } catch(cause: Exception) {
            if(cause is CancellationException) throw cause
            platform.cancelAll()
            throw IllegalStateException("STORAGE_FAILED")
        }
    }
    private suspend fun save(value: ReminderLedger) { store.write(json.encodeToString(value)); ledger=value }
    private suspend fun align(): ReminderLedger {
        val current=identity.current(); val hash=if(current.owner.isNotBlank()) platform.hash(current.owner) else ""
        require(current.owner.isBlank() || hash.isNotBlank()) { "STORAGE_FAILED" }
        val old=load()
        if(hash==old.ownerHash) return old
        val next=ReminderLedger(ownerHash=hash,generation=old.generation+1,revision=old.revision+1,claims=old.claims.filter { it.day>=ReminderTime.day(clock.nowEpochMillis())-35 })
        try { save(next) } finally { cancelOwned(old) }; return next
    }
    fun isReady()=identity.current().ready
    fun canConfigure()=identity.current().canConfigure
    suspend fun snapshot(): ReminderLedger=mutex.withLock { align() }
    suspend fun clear()=mutex.withLock {
        val old=load()
        try { save(ReminderLedger(generation=old.generation+1,revision=old.revision+1,claims=old.claims)) } finally { cancelOwned(old) }
    }
    private suspend fun cancelOwned(old: ReminderLedger) {
        old.claims.forEach { platform.cancel(it.batchId) }
        platform.cancelAll()
    }
    suspend fun settings(): DeadlineNotificationSettings = mutex.withLock {
        val state=align()
        DeadlineNotificationSettings(state.deadlineEnabled, canConfigure() && state.ownerHash.isNotBlank(), platform.permission("deadline"), state.deadlineStatus, state.deadlineFetchedAt)
    }
    suspend fun setEnabled(enabled: Boolean): ReminderLedger = mutex.withLock {
        val old=align()
        require(old.ownerHash.isNotBlank() && canConfigure()) { "NOT_AUTHENTICATED" }
        if(old.deadlineEnabled==enabled)return@withLock old
        val next=old.copy(revision=old.revision+1, deadlineEnabled=enabled, deadlineStatus=if(enabled)"WAITING_REFRESH" else "DISABLED")
        save(next)
        if(!enabled)platform.cancelAll()
        next
    }
    suspend fun enableAfterConsent(captured: ReminderLedger): Boolean=mutex.withLock {
        val old=align()
        if(old.ownerHash!=captured.ownerHash || old.generation!=captured.generation || old.revision!=captured.revision || !canConfigure())return@withLock false
        if(platform.permission("deadline") !in listOf("authorized","provisional"))return@withLock false
        if(!canConfigure() || platform.hash(identity.current().owner)!=captured.ownerHash)return@withLock false
        if(!old.deadlineEnabled)save(old.copy(deadlineEnabled=true,revision=old.revision+1,deadlineStatus="WAITING_REFRESH"))
        true
    }
    suspend fun refresh()=refreshMutex.withLock {
        val ticket=beginDeadlineRefresh()
        val state=snapshot()
        if(!isReady() || state.ownerHash.isBlank() || !state.deadlineEnabled)return@withLock
        val result=try { withTimeout(60_000) { source.deadlines(ticket.term) } } catch(cause: Exception) {
            if(cause is CancellationException && cause !is kotlinx.coroutines.TimeoutCancellationException)throw cause
            ReminderSourceResult.Retry
        }
        acceptDeadlines(ticket,result)
    }
    suspend fun beginDeadlineRefresh(): ReminderDeadlineTicket = beginRefresh(false)
    suspend fun beginHomeDeadlineRefresh(): ReminderDeadlineTicket = beginRefresh(true)
    private suspend fun beginRefresh(home: Boolean): ReminderDeadlineTicket=mutex.withLock {
        val state=align()
        val request=++requestSequence
        if(home)latestHomeRequest=request else latestDeadlineRequest=request
        ReminderDeadlineTicket(state.ownerHash,state.generation,state.revision,identity.current().term,clock.nowEpochMillis(),request)
    }
    suspend fun acceptHomeDeadlines(ticket: ReminderDeadlineTicket, result: com.icecream.kwklasplus.core.academic.DeadlinesResult) {
        val projection=when(result) {
            is com.icecream.kwklasplus.core.academic.DeadlinesResult.Success -> {
                val now=clock.nowEpochMillis()
                if(result.fetchedAt<ticket.startedAt || result.startedAt<ticket.startedAt || now-result.fetchedAt !in 0..60_000 || result.fetchedAt-result.startedAt !in 0..60_000)return
                result.reminders?.let { ReminderSourceResult.Success(it) } ?: ReminderSourceResult.UnverifiedSource
            }
            com.icecream.kwklasplus.core.academic.DeadlinesResult.SessionExpired -> ReminderSourceResult.NeedsLogin
            else -> ReminderSourceResult.Retry
        }
        acceptDeadlines(ticket,projection,allowPosting=false)
    }
    private suspend fun acceptDeadlines(ticket: ReminderDeadlineTicket,result: ReminderSourceResult<List<ReminderDeadline>>,allowPosting: Boolean=true) {
        mutex.withLock {
            val old=align(); val current=identity.current(); val now=clock.nowEpochMillis()
            if(ticket.requestId!=(if(allowPosting) latestDeadlineRequest else latestHomeRequest) || (allowPosting && ticket.requestId<latestHomeAcceptedRequest) || old.ownerHash!=ticket.ownerHash || old.generation!=ticket.generation || !current.ready || current.term!=ticket.term || !old.deadlineEnabled || old.revision!=ticket.revision) return@withLock
            val status=when(result) { ReminderSourceResult.NeedsLogin ->"NEEDS_LOGIN"; ReminderSourceResult.UnverifiedSource ->"UNVERIFIED_SOURCE"; ReminderSourceResult.Retry ->"REFRESH_FAILED"; is ReminderSourceResult.Success ->"READY" }
            save(old.copy(deadlineStatus=status))
            if(result !is ReminderSourceResult.Success || now-ticket.startedAt !in 0..60_000) return@withLock
            val day=ReminderTime.day(now)
            val eligible=result.value.filter { (it.startsAt==null || it.startsAt<=now) && it.dueAt>now && it.dueAt<=now+ReminderTime.DAY }
            if(eligible.map { it.key }.distinct().size!=eligible.size) { save(load().copy(deadlineStatus="UNVERIFIED_SOURCE"));return@withLock }
            if(!allowPosting)latestHomeAcceptedRequest=maxOf(latestHomeAcceptedRequest,ticket.requestId)
            val candidates=eligible.map { platform.hash("${old.ownerHash}/${it.key}") }.toSet()
            val claims=old.claims.filter { it.day>=day-35 }
            claims.filter { it.ownerHash==old.ownerHash && !candidates.containsAll(it.keys) }.forEach { platform.cancel(it.batchId) }
            if(candidates.isEmpty()) {
                save(load().copy(deadlineFetchedAt=now,deadlineStatus="NO_ELIGIBLE_ITEMS",claims=claims)); return@withLock
            }
            val seen=claims.filter { it.ownerHash==old.ownerHash && it.day==day }.flatMap { it.keys }.toSet()
            val added=candidates-seen
            if(added.isEmpty()) { save(load().copy(deadlineFetchedAt=now,deadlineStatus="NO_NEW_ELIGIBLE_ITEMS",claims=claims)); return@withLock }
            save(load().copy(deadlineFetchedAt=now))
            if(!allowPosting || !deliveryGate.canPost()) {
                save(load().copy(deadlineStatus="FOREGROUND_SUPPRESSED"));return@withLock
            }
            if(ReminderTime.isQuietHours(clock.nowEpochMillis())) {
                save(load().copy(deadlineStatus="QUIET_HOURS"));return@withLock
            }
            if(platform.permission("deadline") !in listOf("authorized","provisional")) { save(load().copy(deadlineStatus="PERMISSION_BLOCKED")); return@withLock }
            if(!deliveryGate.canPost()) {
                save(load().copy(deadlineStatus="FOREGROUND_SUPPRESSED"));return@withLock
            }
            if(ReminderTime.isQuietHours(clock.nowEpochMillis())) {
                save(load().copy(deadlineStatus="QUIET_HOURS"));return@withLock
            }
            if(clock.nowEpochMillis()-ticket.startedAt !in 0..60_000)return@withLock
            val batch=platform.hash("${old.ownerHash}/$day/${added.sorted().joinToString()}")
            save(load().copy(deadlineFetchedAt=now,claims=claims+ReminderClaim(old.ownerHash,day,added,batch),deadlineStatus="POST_ATTEMPTED"))
            if(!deliveryGate.canPost()) {
                save(load().copy(claims=claims,deadlineStatus="FOREGROUND_SUPPRESSED"));return@withLock
            }
            val dispatchTime=clock.nowEpochMillis()
            if(ReminderTime.isQuietHours(dispatchTime)) {
                save(load().copy(claims=claims,deadlineStatus="QUIET_HOURS"));return@withLock
            }
            val dispatchIdentity=identity.current()
            if(!dispatchIdentity.ready || dispatchIdentity.term!=ticket.term || platform.hash(dispatchIdentity.owner)!=ticket.ownerHash)return@withLock
            if(dispatchTime-ticket.startedAt !in 0..60_000 || ReminderTime.day(dispatchTime)!=day)return@withLock
            val newItems=eligible.filter { platform.hash("${old.ownerHash}/${it.key}") in added && it.dueAt>dispatchTime }
            if(newItems.isEmpty())return@withLock
            val message=DeadlineReminderMessage.create(newItems,dispatchTime,seen.isNotEmpty())
            val posted=platform.postDetailed(batch,"deadline",old.generation,seen.isNotEmpty(),message)
            if(!posted && ReminderTime.isQuietHours(clock.nowEpochMillis())) {
                save(load().copy(claims=claims,deadlineStatus="QUIET_HOURS"))
            } else save(load().copy(deadlineStatus=if(posted)"POST_ATTEMPTED" else "SCHEDULE_FAILED"))
        }
    }

}
