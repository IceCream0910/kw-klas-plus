package com.icecream.kwklasplus.widget

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings
import com.icecream.kwklasplus.*
import com.icecream.kwklasplus.core.academic.*
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.platform.SecureKey
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.YearMonth

class AcademicWidgetRuntime(private val context: Context) {
    private val store = AndroidAcademicWidgetSnapshotStore(context)
    private val syncMutex = Mutex()
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    var calendarLoading: Boolean = false
        private set
    private var generation = 0L
    private var identity = identity()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == AppPrefs.KW_ID || key == AppPrefs.YEAR_HAKGI) {
            refreshIdentity()
        }
    }

    fun start() {
        context.appPreferences.registerOnSharedPreferenceChangeListener(listener)
        WidgetDisplayRefresh.start(context)
        refreshIdentity()
        render()
        AcademicWidgetScheduler.schedule(context)
    }

    private fun identity(): Pair<String, String> {
        val account = context.appPreferences.getString(AppPrefs.KW_ID, "").orEmpty()
        val owner = if (account.isBlank()) "" else MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        return owner to context.appPreferences.getString(AppPrefs.YEAR_HAKGI, "").orEmpty()
    }

    private fun refreshIdentity() {
        val next = identity()
        var changed = next != identity
        if (next != identity) { generation++; identity = next }
        val old = store.read()
        val updated = AcademicWidgetSnapshotPolicy.forIdentity(old, next.first, next.second)
        if (updated == null) {
            store.clear()
            WidgetBitmapCache.clear()
            changed = changed || old != null
        } else if (old != updated) {
            persist(updated)
            changed = true
        }
        if (changed) render()
    }

    fun snapshot(): AcademicWidgetSnapshot? {
        val key = identity()
        return store.read()?.takeIf { key.first.isNotBlank() && it.owner == key.first && it.term == key.second }
    }

    fun recordTimetable(term: String, entries: List<TimetableEntry>) {
        refreshIdentity()
        if (identity.first.isBlank() || identity.second != term) return
        val old = snapshot() ?: AcademicWidgetSnapshot(identity.first, term)
        val sorted = entries.distinct().sortedWith(compareBy({ it.day }, { AcademicWidgetPolicy.minutes(it.startTime) }, { it.subj }))
        val saved = persist(old.copy(timetable = sorted, timetableFetchedAt = System.currentTimeMillis()))
        if (saved && old.timetable != sorted) render()
    }

    fun foreground() {
        render()
        AcademicWidgetScheduler.schedule(context)
        refreshCalendarNow()
    }

    fun refreshCalendarNow(onFinished: () -> Unit = {}) {
        immediateScope.launch {
            CalendarInitialRefresh().run(
                refresh = { syncCalendar() },
                scheduleRetry = { AcademicWidgetScheduler.requestCalendar(context, replacePending = true) },
                finish = onFinished,
            )
        }
    }

    fun requestCalendar() {
        AcademicWidgetScheduler.requestCalendar(context)
    }

    suspend fun syncCalendar(): Boolean = withContext(Dispatchers.Main.immediate) {
        syncMutex.withLock {
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
                if (version != generation || key != identity() || month != YearMonth.now()) return@withLock false
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

    private fun markStatus(status: WidgetSyncStatus) {
        val old = snapshot() ?: AcademicWidgetSnapshot(identity.first, identity.second)
        persist(old.copy(calendarStatus = status))
        render()
    }

    fun clear() {
        generation++
        store.clear()
        WidgetBitmapCache.clear()
        render()
    }

    private fun persist(snapshot: AcademicWidgetSnapshot): Boolean = runCatching { store.write(snapshot) }.isSuccess

    fun render() {
        AcademicWidgets.renderAll(context)
        WidgetDisplayRefresh.schedule(context)
    }
}
