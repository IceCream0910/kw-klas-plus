package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.security.SecretValue

data class Session(
    val token: SecretValue,
    val observedAtEpochMillis: Long,
)

fun interface Clock {
    fun nowEpochMillis(): Long
}

class SessionPolicy {
    fun isUsable(session: Session, nowEpochMillis: Long): Boolean {
        return isUsable(session.observedAtEpochMillis, nowEpochMillis)
    }

    fun isUsable(observedAtEpochMillis: Long, nowEpochMillis: Long): Boolean {
        return observedAtEpochMillis >= 0 && nowEpochMillis >= observedAtEpochMillis
    }
}

interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

interface WebCookieStore {
    suspend fun setSessionCookie(token: SecretValue)
    suspend fun clearSessionCookie()
}

sealed interface SessionResult {
    data class Active(val session: Session) : SessionResult
    data object Missing : SessionResult
    data object Expired : SessionResult
    data class Failed(val cause: Throwable) : SessionResult
}
