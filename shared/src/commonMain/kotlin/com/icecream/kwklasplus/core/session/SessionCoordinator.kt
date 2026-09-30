package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionCoordinator(
    private val sessionStore: SessionStore,
    private val cookieStore: WebCookieStore,
    private val clock: Clock,
    private val policy: SessionPolicy = SessionPolicy(),
    private val mutationGuard: SessionMutationGuard? = null,
) {
    private val mutationMutex = Mutex()
    private var generation = 0L
    private var backgroundReauthenticationAllowed = true

    suspend fun checkpoint(): Long = mutate { currentGeneration() }

    suspend fun restore(): SessionResult = mutateSession { restoreLocked() }

    private suspend fun restoreLocked(): SessionResult {
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

    suspend fun observe(token: SecretValue): SessionResult = mutateSession {
        observeLocked(token)
    }

    suspend fun observeIfStored(token: SecretValue): SessionResult = mutateSession {
        if (!backgroundAuthenticationAllowed() || sessionStore.load()?.token != token) {
            SessionResult.Expired
        } else observeLocked(token)
    }

    suspend fun observeIfCurrent(token: SecretValue, checkpoint: Long): SessionResult =
        mutateSession {
            if (checkpoint != currentGeneration() || !backgroundAuthenticationAllowed()) {
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
            advanceGeneration()
            sessionStore.save(next)
            cookieStore.setSessionCookie(token)
            backgroundReauthenticationAllowed = true
            SessionResult.Active(next)
        } catch (cause: Throwable) {
            restorePrevious(previous)
            SessionResult.Failed(cause)
        }
    }

    suspend fun expire(): SessionResult = mutateSession {
        advanceGeneration(false)
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
        advanceGeneration()
        sessionStore.clear()
        cookieStore.clearSessionCookie()
        SessionResult.Expired
    } catch (cause: Throwable) {
        SessionResult.Failed(cause)
    }

    private fun currentGeneration(): Long = mutationGuard?.generation() ?: generation

    private fun backgroundAuthenticationAllowed(): Boolean =
        mutationGuard?.backgroundAuthenticationAllowed() ?: backgroundReauthenticationAllowed

    private fun advanceGeneration(allowed: Boolean = true) {
        if (mutationGuard == null) generation++ else mutationGuard.advanceGeneration(allowed)
    }

    private suspend fun <T> mutate(block: suspend () -> T): T = mutationMutex.withLock {
        mutationGuard?.acquire()
        try { block() } finally { mutationGuard?.release() }
    }

    private suspend fun mutateSession(block: suspend () -> SessionResult): SessionResult = try {
        mutate(block)
    } catch (cause: CancellationException) {
        throw cause
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
