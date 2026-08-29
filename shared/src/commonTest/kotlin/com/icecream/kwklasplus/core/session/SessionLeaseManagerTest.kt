package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SessionLeaseManagerTest {
    private val token = SecretValue.of("session-token")
    private val userAgent = KlasUserAgent.fromPlatform("Test Agent")

    @Test
    fun oldStoredSessionIsValidatedByServerAndTimestampIsRefreshed() = runBlocking {
        val fixture = fixture(
            stored = Session(token, 1L),
            now = 24L * 60L * 60L * 1_000L,
            infoResults = listOf(SessionInfoResult.Success(info(7_200L))),
        )

        val result = fixture.manager.maintain()

        val active = assertIs<SessionLeaseResult.Active>(result)
        assertEquals(false, active.extended)
        assertEquals(30L * 60L * 1_000L, active.nextCheckAfterMillis)
        assertEquals(24L * 60L * 60L * 1_000L, fixture.store.session?.observedAtEpochMillis)
        assertEquals(token, fixture.cookies.token)
    }

    @Test
    fun nearExpirySessionIsExtendedAndVerifiedWithSecondInfoRequest() = runBlocking {
        val fixture = fixture(
            infoResults = listOf(
                SessionInfoResult.Success(info(500L)),
                SessionInfoResult.Success(info(7_200L)),
            ),
            extensionResults = listOf(SessionExtensionResult.Success),
        )

        val active = assertIs<SessionLeaseResult.Active>(fixture.manager.maintain())

        assertEquals(true, active.extended)
        assertEquals(2, fixture.gateway.infoCalls)
        assertEquals(1, fixture.gateway.extensionCalls)
        assertEquals(7_200L, active.info.remainingSeconds)
    }

    @Test
    fun extensionMustIncreaseServerReportedRemainingTime() = runBlocking {
        val fixture = fixture(
            infoResults = listOf(
                SessionInfoResult.Success(info(500L)),
                SessionInfoResult.Success(info(500L)),
            ),
            extensionResults = listOf(SessionExtensionResult.Success),
        )

        val retry = assertIs<SessionLeaseResult.Retry>(fixture.manager.maintain())

        assertEquals(SessionLeaseFailure.ExtensionNotConfirmed, retry.failure)
        assertEquals(1L, fixture.store.session?.observedAtEpochMillis)
    }

    @Test
    fun serverExpiryClearsStoredSessionAndCookie() = runBlocking {
        val fixture = fixture(infoResults = listOf(SessionInfoResult.SessionExpired))

        assertEquals(SessionLeaseResult.Expired, fixture.manager.maintain())
        assertNull(fixture.store.session)
        assertNull(fixture.cookies.token)
    }

    @Test
    fun networkAndTimeoutFailuresKeepSessionForRetry() = runBlocking {
        for ((gatewayResult, expectedFailure) in listOf(
            SessionInfoResult.NetworkFailure to SessionLeaseFailure.Network,
            SessionInfoResult.Timeout to SessionLeaseFailure.Timeout,
        )) {
            val fixture = fixture(infoResults = listOf(gatewayResult))

            val retry = assertIs<SessionLeaseResult.Retry>(fixture.manager.maintain())

            assertEquals(expectedFailure, retry.failure)
            assertEquals(token, fixture.store.session?.token)
            assertEquals(token, fixture.cookies.token)
        }
    }

    @Test
    fun missingSessionDoesNotCallNetwork() = runBlocking {
        val fixture = fixture(stored = null)

        assertEquals(SessionLeaseResult.Missing, fixture.manager.maintain())
        assertEquals(0, fixture.gateway.infoCalls)
        assertEquals(0, fixture.gateway.extensionCalls)
    }

    @Test
    fun policyUsesServerCountdownAndBoundedCheckIntervals() {
        val policy = SessionLeasePolicy()

        assertEquals(true, policy.shouldExtend(info(600L)))
        assertEquals(false, policy.shouldExtend(info(601L)))
        assertEquals(30L * 1_000L, policy.nextCheckAfterMillis(info(610L)))
        assertEquals(30L * 60L * 1_000L, policy.nextCheckAfterMillis(info(7_200L)))
        assertEquals(
            true,
            policy.shouldExtend(
                SessionLeaseInfo(
                    logoutCountDownSeconds = 900L,
                    sessionNotificationSeconds = 6_900L,
                    remainingSeconds = 950L,
                ),
            ),
        )
    }

    private fun fixture(
        stored: Session? = Session(token, 1L),
        now: Long = 10_000L,
        infoResults: List<SessionInfoResult> = emptyList(),
        extensionResults: List<SessionExtensionResult> = emptyList(),
    ): Fixture {
        val store = FakeSessionStore(stored)
        val cookies = FakeCookieStore()
        val gateway = FakeGateway(infoResults, extensionResults)
        val coordinator = SessionCoordinator(store, cookies, Clock { now })
        return Fixture(
            manager = SessionLeaseManager(coordinator, gateway, userAgent),
            store = store,
            cookies = cookies,
            gateway = gateway,
        )
    }

    private fun info(remaining: Long) = SessionLeaseInfo(
        logoutCountDownSeconds = 300L,
        sessionNotificationSeconds = 6_900L,
        remainingSeconds = remaining,
    )

    private data class Fixture(
        val manager: SessionLeaseManager,
        val store: FakeSessionStore,
        val cookies: FakeCookieStore,
        val gateway: FakeGateway,
    )

    private class FakeGateway(
        infoResults: List<SessionInfoResult>,
        extensionResults: List<SessionExtensionResult>,
    ) : SessionLeaseGateway {
        private val infos = ArrayDeque(infoResults)
        private val extensions = ArrayDeque(extensionResults)
        var infoCalls = 0
        var extensionCalls = 0

        override suspend fun fetchInfo(
            session: SecretValue,
            userAgent: KlasUserAgent,
        ): SessionInfoResult {
            infoCalls += 1
            return infos.removeFirst()
        }

        override suspend fun extend(
            session: SecretValue,
            userAgent: KlasUserAgent,
        ): SessionExtensionResult {
            extensionCalls += 1
            return extensions.removeFirst()
        }
    }

    private class FakeSessionStore(var session: Session?) : SessionStore {
        override suspend fun load(): Session? = session
        override suspend fun save(session: Session) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    private class FakeCookieStore(var token: SecretValue? = null) : WebCookieStore {
        override suspend fun setSessionCookie(token: SecretValue) {
            this.token = token
        }
        override suspend fun clearSessionCookie() {
            token = null
        }
    }
}
