package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue

class SessionCoordinator(
    private val sessionStore: SessionStore,
    private val cookieStore: WebCookieStore,
    private val clock: Clock,
    private val policy: SessionPolicy = SessionPolicy(),
) {
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

    suspend fun observe(token: SecretValue): SessionResult {
        val previous = try {
            sessionStore.load()
        } catch (cause: Throwable) {
            return SessionResult.Failed(cause)
        }
        val next = Session(token, clock.nowEpochMillis())

        return try {
            sessionStore.save(next)
            cookieStore.setSessionCookie(token)
            SessionResult.Active(next)
        } catch (cause: Throwable) {
            restorePrevious(previous)
            SessionResult.Failed(cause)
        }
    }

    suspend fun expire(): SessionResult = try {
        sessionStore.clear()
        cookieStore.clearSessionCookie()
        SessionResult.Expired
    } catch (cause: Throwable) {
        SessionResult.Failed(cause)
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
