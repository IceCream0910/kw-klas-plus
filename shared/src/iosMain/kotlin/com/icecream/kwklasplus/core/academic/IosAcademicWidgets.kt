package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.IosSharedDependencies
import com.icecream.kwklasplus.core.auth.LoginTokenEncryptor
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.session.SessionResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970

fun interface AcademicWidgetTimelineReloader {
    fun reload()
}

class IosAcademicWidgets(
    private val preferences: (String) -> String?,
    private val store: IosAcademicWidgetDisplayStore,
    private val calendarSync: CalendarSyncUseCase?,
    private val credential: suspend () -> com.icecream.kwklasplus.core.auth.StoredCredential?,
    private val session: suspend () -> com.icecream.kwklasplus.core.security.SecretValue?,
    private val reloader: AcademicWidgetTimelineReloader?,
) {
    fun snapshot(): AcademicWidgetSnapshot? {
        val owner = owner()
        val term = identityTerm()
        val display = store.read() ?: return null
        if (owner.isBlank() || display.owner != owner || display.term != term) return null
        return AcademicWidgetSnapshot(
            owner = display.owner,
            term = display.term,
            timetable = display.classes?.map {
                TimetableEntry(it.title, it.day, it.startTime, it.endTime, it.info, "")
            },
            timetableFetchedAt = display.timetableFetchedAt,
            month = display.month,
            calendar = display.events?.map {
                CalendarEvent(it.id, it.title, it.start, it.end, "", it.color, "")
            },
            calendarFetchedAt = display.calendarFetchedAt,
            calendarStatus = display.calendarStatus,
        )
    }

    fun recordTimetable(term: String, entries: List<TimetableEntry>) {
        refreshIdentity()
        val owner = owner()
        if (owner.isBlank() || identityTerm() != term) return
        val previous = snapshot() ?: AcademicWidgetSnapshot(owner, term)
        val sorted = entries.distinct().sortedWith(
            compareBy({ it.day }, { AcademicWidgetPolicy.minutes(it.startTime) }, { it.subj }),
        )
        persist(previous.copy(timetable = sorted, timetableFetchedAt = currentMillis()))
    }

    suspend fun syncCalendar(userAgent: String) {
        refreshIdentity()
        val owner = owner()
        val term = identityTerm()
        val sync = calendarSync ?: return
        if (owner.isBlank()) return
        val range = currentMonthRange()
        val month = range.first.take(7)
        val result = runCatching {
            sync.sync(
                credential(),
                session(),
                KlasUserAgent.fromPlatform(userAgent),
                range.first,
                range.second,
            )
        }.getOrElse {
            persistCalendar(owner, term, CalendarSyncResult.Retry, month)
            return
        }
        persistCalendar(owner, term, result, month)
    }

    fun refreshIdentity() {
        val owner = owner()
        val term = identityTerm()
        val previous = store.read()?.let { display ->
            AcademicWidgetSnapshot(
                owner = display.owner,
                term = display.term,
                timetable = display.classes?.map {
                    TimetableEntry(it.title, it.day, it.startTime, it.endTime, it.info, "")
                },
                timetableFetchedAt = display.timetableFetchedAt,
                month = display.month,
                calendar = display.events?.map {
                    CalendarEvent(it.id, it.title, it.start, it.end, "", it.color, "")
                },
                calendarFetchedAt = display.calendarFetchedAt,
                calendarStatus = display.calendarStatus,
            )
        }
        val updated = AcademicWidgetSnapshotPolicy.forIdentity(previous, owner, term)
        if (updated == null) {
            if (previous != null) clear()
        } else if (previous != null && updated != previous) {
            persist(updated)
        }
    }

    fun clear() {
        store.clear()
        reloader?.reload()
    }

    private fun persistCalendar(
        owner: String,
        term: String,
        result: CalendarSyncResult,
        month: String,
    ) {
        val current = snapshot() ?: AcademicWidgetSnapshot(owner, term)
        persist(AcademicWidgetSnapshotPolicy.applyCalendar(current, result, month, currentMillis()))
    }

    private fun persist(snapshot: AcademicWidgetSnapshot) {
        store.write(AcademicWidgetDisplayFactory.from(snapshot))
        reloader?.reload()
    }

    private fun owner(): String {
        val account = preferences(LegacyPreferenceKeys.KW_ID).orEmpty()
        return if (account.isBlank()) "" else sha256Hex(account)
    }

    private fun identityTerm(): String = preferences(LegacyPreferenceKeys.YEAR_HAKGI).orEmpty()

    private fun currentMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    private fun currentMonthRange(): Pair<String, String> {
        val calendar = NSCalendar.currentCalendar
        calendar.timeZone = NSTimeZone.localTimeZone
        val now = NSDate()
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond
        val comps = calendar.components(units, fromDate = now)
        comps.day = 1
        comps.hour = 0
        comps.minute = 0
        comps.second = 0
        val start = calendar.dateFromComponents(comps) ?: now
        val next = calendar.dateByAddingUnit(NSCalendarUnitMonth, 1, start, 0u) ?: start
        val end = calendar.dateByAddingUnit(NSCalendarUnitDay, -1, next, 0u) ?: start
        val formatter = NSDateFormatter()
        formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
        formatter.timeZone = NSTimeZone.localTimeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.stringFromDate(start) to formatter.stringFromDate(end)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sha256Hex(value: String): String {
        val input = value.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { pinned ->
            digest.usePinned { out ->
                CC_SHA256(pinned.addressOf(0), input.size.convert(), out.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toInt().toString(16).padStart(2, '0') }
    }

    companion object {
        fun create(
            dependencies: IosSharedDependencies,
            tokenEncryptor: LoginTokenEncryptor?,
            reloader: AcademicWidgetTimelineReloader?,
        ): IosAcademicWidgets = IosAcademicWidgets(
            preferences = { dependencies.stringPreference(it) },
            store = IosAcademicWidgetDisplayStore(),
            calendarSync = tokenEncryptor?.let { dependencies.calendarSync(it) },
            credential = { runCatching { dependencies.credentialStore.load() }.getOrNull() },
            session = {
                when (val restored = dependencies.sessionCoordinator.restore()) {
                    is SessionResult.Active -> restored.session.token
                    else -> null
                }
            },
            reloader = reloader,
        )
    }
}
