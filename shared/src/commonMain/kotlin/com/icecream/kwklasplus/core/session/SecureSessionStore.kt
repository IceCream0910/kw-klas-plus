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

class MirroringSessionStore(
    private val primary: SessionStore,
    private val legacy: SessionStore,
) : SessionStore {
    override suspend fun load(): Session? {
        runCatching { primary.load() }.getOrNull()?.let { return it }
        val legacySession = legacy.load() ?: return null
        runCatching { primary.save(legacySession) }
        return legacySession
    }

    override suspend fun save(session: Session) {
        primary.save(session)
        try {
            legacy.save(session)
        } catch (cause: Throwable) {
            runCatching { primary.clear() }
            throw cause
        }
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { primary.clear() }
            .onFailure { failure = it }
        runCatching { legacy.clear() }
            .onFailure { if (failure == null) failure = it }
        failure?.let { throw it }
    }
}
