package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.StoredCredential
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.auth.WebAuthResult
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.network.KlasAuthenticatedResult
import com.icecream.kwklasplus.core.network.KlasAuthenticatedTransport
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.session.Session
import com.icecream.kwklasplus.core.session.SessionCoordinator
import com.icecream.kwklasplus.core.session.SessionInfoResult
import com.icecream.kwklasplus.core.session.SessionLeaseGateway
import com.icecream.kwklasplus.core.session.SessionLeaseInfo
import com.icecream.kwklasplus.core.session.SessionResult
import com.icecream.kwklasplus.core.session.SessionStore
import com.icecream.kwklasplus.core.session.WebCookieStore
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosAcademicWidgetSessionContractTest {
    private val suite = "com.icecream.kwklasplus.test.widget.session.${hashCode()}"
    private val defaults = requireNotNull(NSUserDefaults(suiteName = suite))
    private val flags = IosAcademicWidgetSharedFlags(defaults)
    private val tmp = NSTemporaryDirectory() + "academic-widget-${hashCode()}"

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suite)
        NSFileManager.defaultManager.removeItemAtPath(tmp, null)
    }

    @Test
    fun revisingStoreBumpsRevisionAndMarksCookieSync() = runSuspendTest {
        val inner = MemorySessionStore()
        val store = IosRevisingSessionStore(inner, flags, markCookieSync = true)
        store.save(Session(SecretValue.of("session-1"), 1L))
        assertEquals(1L, flags.revision())
        assertTrue(flags.cookieSyncNeeded())
        store.clear()
        assertEquals(2L, flags.revision())
        assertFalse(flags.cookieSyncNeeded())
    }

    @Test
    fun cookieSyncFlagIsConsumedWhenAppRestoresWebCookie() = runSuspendTest {
        flags.markCookieSyncNeeded()
        val cookies = MemoryCookieStore()
        val coordinator = SessionCoordinator(
            MemorySessionStore(Session(SecretValue.of("session-1"), 1_000L)),
            IosCookieSyncingWebCookieStore(cookies, flags),
            Clock { 1_000L },
        )
        assertIs<SessionResult.Active>(coordinator.restore())
        assertFalse(flags.cookieSyncNeeded())
        assertEquals("session-1", cookies.token?.reveal())
    }

    @Test
    fun mirroredTimestampWritesAppGroupAndPrimary() = runSuspendTest {
        val primary = object : com.icecream.kwklasplus.core.session.SessionTimestampStore {
            var value: Long? = null
            override suspend fun read(): Long? = value
            override suspend fun write(value: Long) { this.value = value }
            override suspend fun clear() { value = null }
        }
        val store = IosMirroredSessionTimestampStore(primary, flags)
        store.write(42L)
        assertEquals(42L, flags.timestamp())
        assertEquals(42L, primary.value)
        store.clear()
        assertNull(flags.timestamp())
        assertNull(primary.value)
    }

    @Test
    fun calendarSyncDiscardsNeedsLoginAfterForeignRevisionBump() = runSuspendTest {
        NSFileManager.defaultManager.createDirectoryAtPath(tmp, true, null, null)
        var fetchCalls = 0
        val widgets = widgets(
            fetch = {
                fetchCalls += 1
                if (fetchCalls == 1) KlasAuthenticatedResult.Success(JsonArray(emptyList()))
                else {
                    flags.bumpRevision()
                    KlasAuthenticatedResult.SessionExpired
                }
            },
        )
        assertFalse(widgets.syncCalendar("test"))
        assertEquals(WidgetSyncStatus.READY, widgets.snapshot()?.calendarStatus)
        assertFalse(widgets.syncCalendar("test"))
        assertEquals(WidgetSyncStatus.READY, widgets.snapshot()?.calendarStatus)
        assertEquals(2, fetchCalls)
    }

    @Test
    fun calendarSyncDiscardsResultWhenOwnerChanges() = runSuspendTest {
        NSFileManager.defaultManager.createDirectoryAtPath(tmp, true, null, null)
        var account = "2020123456"
        val widgets = widgets(
            account = { account },
            fetch = {
                account = "2020999999"
                KlasAuthenticatedResult.Success(JsonArray(emptyList()))
            },
        )
        assertFalse(widgets.syncCalendar("test"))
        assertNull(widgets.snapshot())
    }

    @Test
    fun calendarRefreshCoalescesOverlappingTaps() = kotlinx.coroutines.runBlocking {
        NSFileManager.defaultManager.createDirectoryAtPath(tmp, true, null, null)
        var fetches = 0
        val widgets = widgets(
            fetch = {
                fetches += 1
                kotlinx.coroutines.delay(30)
                KlasAuthenticatedResult.Success(JsonArray(emptyList()))
            },
        )
        val first = kotlinx.coroutines.CompletableDeferred<Unit>()
        val second = kotlinx.coroutines.CompletableDeferred<Unit>()
        val refresh = IosAcademicWidgetCalendarRefresh(
            widgets = widgets,
            gate = CalendarWidgetRefreshGate(1_000),
            scope = this,
        )
        refresh.refresh("test") { first.complete(Unit) }
        refresh.refresh("test") { second.complete(Unit) }
        first.await()
        second.await()
        assertEquals(1, fetches)
    }

    private fun widgets(
        account: () -> String? = { "2020123456" },
        fetch: suspend () -> KlasAuthenticatedResult,
    ): IosAcademicWidgets {
        defaults.setObject(account(), LegacyPreferenceKeys.KW_ID)
        defaults.setObject("2026,2", LegacyPreferenceKeys.YEAR_HAKGI)
        defaults.synchronize()
        val coordinator = SessionCoordinator(
            MemorySessionStore(),
            MemoryCookieStore(),
            Clock { 1_000L },
        )
        val sync = CalendarSyncUseCase(
            object : SessionLeaseGateway {
                override suspend fun fetchInfo(session: SecretValue, userAgent: KlasUserAgent) =
                    SessionInfoResult.Success(SessionLeaseInfo(60, 300, 1800))
                override suspend fun extend(session: SecretValue, userAgent: KlasUserAgent) =
                    error("unused")
            },
            WebAuthDriver { WebAuthResult.Failure(AuthFailure.InvalidCredentials) },
            CalendarRepository(
                KlasAuthenticatedTransport { _, _, _, _ -> fetch() },
                KlasCalendarEventNormalizer(),
            ),
            coordinator,
        )
        return IosAcademicWidgets(
            preferences = {
                when (it) {
                    LegacyPreferenceKeys.KW_ID -> account()
                    LegacyPreferenceKeys.YEAR_HAKGI -> "2026,2"
                    else -> null
                }
            },
            store = IosAcademicWidgetDisplayStore(defaults = defaults, containerPath = tmp),
            calendarSync = sync,
            credential = { StoredCredential("2020123456", SecretValue.of("enc")) },
            session = { SecretValue.of("session") },
            reloader = {},
            flags = flags,
        )
    }
}

private class MemorySessionStore(var session: Session? = null) : SessionStore {
    override suspend fun load() = session
    override suspend fun save(session: Session) { this.session = session }
    override suspend fun clear() { session = null }
}

private class MemoryCookieStore(var token: SecretValue? = null) : WebCookieStore {
    override suspend fun setSessionCookie(token: SecretValue) { this.token = token }
    override suspend fun clearSessionCookie() { token = null }
}
