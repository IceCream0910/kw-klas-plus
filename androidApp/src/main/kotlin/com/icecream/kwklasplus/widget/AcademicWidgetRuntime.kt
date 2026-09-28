package com.icecream.kwklasplus.widget

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings
import com.icecream.kwklasplus.*
import com.icecream.kwklasplus.core.academic.*
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.feature.auth.LoginFunnelStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.YearMonth

class AcademicWidgetRuntime(private val context: Context) {
    private val persistence = AcademicWidgetSnapshotPersistence(AndroidAcademicWidgetSnapshotStore(context))
    private val syncMutex = Mutex()
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initialized = CompletableDeferred<Unit>()
    private val immediateRefreshLock = Any()
    private val immediateRefreshCallbacks = mutableListOf<() -> Unit>()
    private var immediateRefreshActive = false
    @Volatile
    var calendarLoading: Boolean = false
        private set
    @Volatile private var generation = 0L
    @Volatile private var snapshotCache: AcademicWidgetSnapshot? = null
    private var identity = identity()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == AppPrefs.KW_ID || key == AppPrefs.YEAR_HAKGI) {
            immediateScope.launch {
                initialized.await()
                refreshIdentity()
            }
        }
    }

    fun start() {
        context.appPreferences.registerOnSharedPreferenceChangeListener(listener)
        WidgetDisplayRefresh.start(context)
        immediateScope.launch {
            val loaded = persistence.read()
            snapshotCache = loaded
            initialized.complete(Unit)
            refreshIdentity()
            render()
        }
        AcademicWidgetScheduler.schedule(context)
    }

    private fun identity(): Pair<String, String> {
        val account = context.appPreferences.getString(AppPrefs.KW_ID, "").orEmpty()
        val owner = if (account.isBlank()) "" else MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        return owner to context.appPreferences.getString(AppPrefs.YEAR_HAKGI, "").orEmpty()
    }

    private suspend fun refreshIdentity() {
        val next = identity()
        var changed = next != identity
        if (next != identity) { generation++; identity = next }
        val old = snapshotCache
        val updated = AcademicWidgetSnapshotPolicy.forIdentity(old, next.first, next.second)
        if (updated == null) {
            snapshotCache = null
            persistence.clear()
            WidgetBitmapCache.clear()
            changed = changed || old != null
        } else if (old != updated) {
            persist(updated)
            changed = true
        }
        if (changed) render()
    }

    fun snapshot(): AcademicWidgetSnapshot? {
        if (setupIncomplete()) return null
        val key = identity()
        return snapshotCache?.takeIf { key.first.isNotBlank() && it.owner == key.first && it.term == key.second }
    }

    suspend fun recordTimetable(term: String, entries: List<TimetableEntry>) = withContext(Dispatchers.Main.immediate) {
        if (setupIncomplete()) return@withContext
        initialized.await()
        refreshIdentity()
        if (identity.first.isBlank() || identity.second != term) return@withContext
        val old = snapshot() ?: AcademicWidgetSnapshot(identity.first, term)
        val sorted = entries.distinct().sortedWith(compareBy({ it.day }, { AcademicWidgetPolicy.minutes(it.startTime) }, { it.subj }))
        val saved = persist(old.copy(timetable = sorted, timetableFetchedAt = System.currentTimeMillis()))
        if (saved && old.timetable != sorted) render()
    }

    fun foreground() {
        render()
        if (setupIncomplete()) return
        AcademicWidgetScheduler.schedule(context)
        refreshCalendarNow()
    }

    fun refreshCalendarNow(onFinished: () -> Unit = {}) {
        if (setupIncomplete()) {
            render()
            onFinished()
            return
        }
        synchronized(immediateRefreshLock) {
            immediateRefreshCallbacks += onFinished
            if (immediateRefreshActive) return
            immediateRefreshActive = true
        }
        immediateScope.launch {
            CalendarInitialRefresh().run(
                refresh = { syncCalendar() },
                scheduleRetry = { AcademicWidgetScheduler.requestCalendar(context, replacePending = true) },
                finish = {
                    val callbacks = synchronized(immediateRefreshLock) {
                        immediateRefreshActive = false
                        immediateRefreshCallbacks.toList().also { immediateRefreshCallbacks.clear() }
                    }
                    callbacks.forEach { runCatching(it) }
                },
            )
        }
    }

    fun requestCalendar() {
        if (setupIncomplete()) return
        AcademicWidgetScheduler.requestCalendar(context)
    }

    suspend fun syncCalendar(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (setupIncomplete()) return@withContext false
        initialized.await()
        syncMutex.withLock {
            if (setupIncomplete()) return@withLock false
            if (!AcademicWidgets.hasCalendar(context)) return@withLock false
            refreshIdentity()
            val key = identity
            val version = generation
            if (key.first.isBlank()) return@withLock false
            val month = YearMonth.now()
            val dependencies = context.appDependencies
            calendarLoading = true
            render()
            try {
                val credential = dependencies.credentialStore.load()
                val result = dependencies.calendarSync.sync(
                    credential, dependencies.secureStore.read(SecureKey.SESSION_TOKEN),
                    KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(context)),
                    month.atDay(1).toString(), month.atEndOfMonth().toString(),
                )
                if (setupIncomplete() || version != generation || key != identity() || month != YearMonth.now()) return@withLock false
                when (result) {
                    is CalendarSyncResult.Success -> {
                        val old = snapshot() ?: AcademicWidgetSnapshot(key.first, key.second)
                        val saved = persist(old.copy(month = month.toString(), calendar = result.events,
                            calendarFetchedAt = System.currentTimeMillis(), calendarStatus = WidgetSyncStatus.READY))
                        render()
                        !saved
                    }
                    CalendarSyncResult.NeedsLogin -> { markStatus(WidgetSyncStatus.NEEDS_LOGIN); false }
                    CalendarSyncResult.Retry -> { markStatus(WidgetSyncStatus.RETRY); true }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                if (version == generation && key == identity()) markStatus(WidgetSyncStatus.RETRY)
                true
            } finally {
                calendarLoading = false
                render()
            }
        }
    }

    private fun setupIncomplete(): Boolean = LoginFunnelStatus.blocksHome(
        context.appPreferences.getString(LoginFunnelStatus.KEY, null),
    )

    private suspend fun markStatus(status: WidgetSyncStatus) {
        val old = snapshot() ?: AcademicWidgetSnapshot(identity.first, identity.second)
        persist(old.copy(calendarStatus = status))
        render()
    }

    suspend fun clear() = withContext(Dispatchers.Main.immediate) {
        initialized.await()
        generation++
        snapshotCache = null
        persistence.clear()
        WidgetBitmapCache.clear()
        render()
    }

    private suspend fun persist(snapshot: AcademicWidgetSnapshot): Boolean {
        if (setupIncomplete()) return false
        val version = generation
        val saved = runCatching {
            persistence.writeIf(snapshot) {
                val current = identity()
                !setupIncomplete() && version == generation && snapshot.owner == current.first && snapshot.term == current.second
            }
        }.getOrDefault(false)
        if (saved && version == generation) snapshotCache = snapshot
        return saved
    }

    fun render() {
        AcademicWidgets.renderAll(context)
        WidgetDisplayRefresh.schedule(context)
    }
}
