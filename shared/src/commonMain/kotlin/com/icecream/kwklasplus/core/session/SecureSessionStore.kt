package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore

interface SessionTimestampStore {
    suspend fun read(): Long?
    suspend fun write(value: Long)
    suspend fun clear()
}

class SecureSessionStore(
    private val secureStore: SecureStore,
    private val timestampStore: SessionTimestampStore,
) : SessionStore {
    override suspend fun load(): Session? {
        val token = secureStore.read(SecureKey.SESSION_TOKEN) ?: return null
        val timestamp = timestampStore.read()
        if (timestamp == null) {
            secureStore.remove(SecureKey.SESSION_TOKEN)
            return null
        }
        return Session(token, timestamp)
    }

    override suspend fun save(session: Session) {
        secureStore.write(SecureKey.SESSION_TOKEN, session.token)
        try {
            timestampStore.write(session.observedAtEpochMillis)
        } catch (cause: Throwable) {
            runCatching { secureStore.remove(SecureKey.SESSION_TOKEN) }
            throw cause
        }
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { secureStore.remove(SecureKey.SESSION_TOKEN) }
            .onFailure { failure = it }
        runCatching { timestampStore.clear() }
            .onFailure { if (failure == null) failure = it }
        failure?.let { throw it }
    }
}

interface LegacySessionSource {
    suspend fun load(): Session?
    suspend fun removeToken()
}

class MigratingSessionStore(
    private val primary: SessionStore,
    private val legacy: LegacySessionSource,
) : SessionStore {
    override suspend fun load(): Session? {
        primary.load()?.let {
            legacy.removeToken()
            return it
        }
        val legacySession = legacy.load() ?: return null
        primary.save(legacySession)
        check(primary.load() == legacySession)
        legacy.removeToken()
        return legacySession
    }

    override suspend fun save(session: Session) {
        primary.save(session)
        legacy.removeToken()
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { primary.clear() }
            .onFailure { failure = it }
        runCatching { legacy.removeToken() }
            .onFailure { if (failure == null) failure = it }
        failure?.let { throw it }
    }
}
