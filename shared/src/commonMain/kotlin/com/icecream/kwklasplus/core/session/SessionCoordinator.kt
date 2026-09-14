package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionCoordinator(
    private val sessionStore: SessionStore,
    private val cookieStore: WebCookieStore,
    private val clock: Clock,
    private val policy: SessionPolicy = SessionPolicy(),
) {
    private val mutationMutex = Mutex()
    private var generation = 0L
    private var backgroundReauthenticationAllowed = true

    suspend fun checkpoint(): Long = mutationMutex.withLock { generation }

    suspend fun restore(): SessionResult {
        val stored = try {
            sessionStore.load()
        } catch (cause: Throwable) {
            return SessionResult.Failed(cause)
        } ?: return SessionResult.Missing

        if (!policy.isUsable(stored, clock.nowEpochMillis())) {
            return clearExpired()
        }

        return try {
            cookieStore.setSessionCookie(stored.token)
            SessionResult.Active(stored)
        } catch (cause: Throwable) {
            SessionResult.Failed(cause)
        }
    }

    suspend fun observe(token: SecretValue): SessionResult = mutationMutex.withLock {
        observeLocked(token)
    }

    suspend fun observeIfCurrent(token: SecretValue, checkpoint: Long): SessionResult =
        mutationMutex.withLock {
            if (checkpoint != generation || !backgroundReauthenticationAllowed) {
                SessionResult.Expired
            } else observeLocked(token)
        }

    private suspend fun observeLocked(token: SecretValue): SessionResult {
        val previous = try {
            sessionStore.load()
        } catch (cause: Throwable) {
            return SessionResult.Failed(cause)
        }
        val next = Session(token, clock.nowEpochMillis())

        return try {
            sessionStore.save(next)
            cookieStore.setSessionCookie(token)
            generation++
            backgroundReauthenticationAllowed = true
            SessionResult.Active(next)
        } catch (cause: Throwable) {
            restorePrevious(previous)
            SessionResult.Failed(cause)
        }
    }

    suspend fun expire(): SessionResult = mutationMutex.withLock {
        generation++
        backgroundReauthenticationAllowed = false
        try {
            sessionStore.clear()
            cookieStore.clearSessionCookie()
            SessionResult.Expired
        } catch (cause: Throwable) {
            SessionResult.Failed(cause)
        }
    }

    private suspend fun clearExpired(): SessionResult = try {
        sessionStore.clear()
        cookieStore.clearSessionCookie()
        SessionResult.Expired
    } catch (cause: Throwable) {
        SessionResult.Failed(cause)
    }

    private suspend fun restorePrevious(previous: Session?) {
        runCatching {
            if (previous == null) {
                sessionStore.clear()
                cookieStore.clearSessionCookie()
            } else {
                sessionStore.save(previous)
                cookieStore.setSessionCookie(previous.token)
            }
        }
    }
}
