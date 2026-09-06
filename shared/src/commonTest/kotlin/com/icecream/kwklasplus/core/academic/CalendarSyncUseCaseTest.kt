package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.auth.*
import com.icecream.kwklasplus.core.network.*
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.session.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlin.test.*

class CalendarSyncUseCaseTest {
    private val active = SessionInfoResult.Success(SessionLeaseInfo(60, 300, 1800))
    private val credential = StoredCredential("test-account", SecretValue.of("encrypted-test"))
    private var authCalls = 0
    private var fetchCalls = 0
    private val success = KlasAuthenticatedResult.Success(JsonArray(emptyList()))

    private fun useCase(info: (SecretValue) -> SessionInfoResult = { active },
                        authResult: WebAuthResult = WebAuthResult.SessionObserved(SecretValue.of("new")),
                        fetch: (SecretValue) -> KlasAuthenticatedResult = { success }) = CalendarSyncUseCase(
        object : SessionLeaseGateway {
            override suspend fun fetchInfo(session: SecretValue, userAgent: KlasUserAgent) = info(session)
            override suspend fun extend(session: SecretValue, userAgent: KlasUserAgent): SessionExtensionResult = error("No foreground lease mutation")
        }, WebAuthDriver { authCalls++; authResult },
        CalendarRepository(KlasAuthenticatedTransport { _, token, _, _ -> fetchCalls++; fetch(token) }, KlasCalendarEventNormalizer()),
    )

    private suspend fun sync(case: CalendarSyncUseCase, token: SecretValue? = SecretValue.of("old")) =
        case.sync(credential, token, KlasUserAgent.fromPlatform("test"), "2026-09-01", "2026-09-30")

    @Test fun activeSessionDoesNotLogin() = runBlocking {
        assertIs<CalendarSyncResult.Success>(sync(useCase()))
        assertEquals(0, authCalls)
        assertEquals(1, fetchCalls)
    }

    @Test fun expiredSessionAuthenticatesOnceAndValidatesNewToken() = runBlocking {
        val seen = mutableListOf<String>()
        val case = useCase(info = { token -> seen += token.reveal(); active },
            fetch = { if (it.reveal() == "old") KlasAuthenticatedResult.SessionExpired else success })
        assertIs<CalendarSyncResult.Success>(sync(case))
        assertEquals(listOf("new"), seen)
        assertEquals(1, authCalls)
    }

    @Test fun apiExpiryBetweenValidationAndFetchRetriesOnlyOnce() = runBlocking {
        val case = useCase(fetch = { KlasAuthenticatedResult.SessionExpired })
        assertEquals(CalendarSyncResult.NeedsLogin, sync(case))
        assertEquals(1, authCalls)
        assertEquals(2, fetchCalls)
    }

    @Test fun existingSessionFetchDoesNotDependOnInfoEndpoint() = runBlocking {
        assertIs<CalendarSyncResult.Success>(sync(useCase(info = { error("Web calendar does not preflight /info") })))
        assertEquals(0, authCalls)
        assertEquals(1, fetchCalls)
    }

    @Test fun transientCalendarFailureDoesNotLogin() = runBlocking {
        assertEquals(CalendarSyncResult.Retry, sync(useCase(fetch = { KlasAuthenticatedResult.NetworkFailure })))
        assertEquals(0, authCalls)
    }

    @Test fun storedSessionCanFetchWithoutStoredPassword() = runBlocking {
        assertIs<CalendarSyncResult.Success>(useCase().sync(null, SecretValue.of("old"),
            KlasUserAgent.fromPlatform("test"), "2026-09-01", "2026-09-30"))
        assertEquals(0, authCalls)
    }

    @Test fun captchaAndUserCancellationRequireForeground() = runBlocking {
        for (failure in listOf(AuthFailure.CaptchaRequired, AuthFailure.TemporaryPasswordChangeRequired, AuthFailure.UserCancelled, AuthFailure.InvalidCredentials)) {
            assertEquals(CalendarSyncResult.NeedsLogin, sync(useCase(authResult = WebAuthResult.Failure(failure)), null))
        }
        assertEquals(0, fetchCalls)
    }

    @Test fun schedulerCancellationPropagates() = runBlocking {
        assertFailsWith<CancellationException> { sync(useCase(fetch = { throw CancellationException() })) }
        assertEquals(0, authCalls)
    }
}
