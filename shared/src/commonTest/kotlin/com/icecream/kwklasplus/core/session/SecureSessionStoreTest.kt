package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureSessionStoreTest {
    @Test
    fun storesTokenSeparatelyFromTimestamp() = runSessionStoreTest {
        val secrets = FakeSecureStore()
        val timestamps = FakeTimestampStore()
        val store = SecureSessionStore(secrets, timestamps)
        val session = Session(SecretValue.of("token"), 1234L)

        store.save(session)

        assertEquals(session, store.load())
        assertEquals("token", secrets.values[SecureKey.SESSION_TOKEN]?.reveal())
        assertEquals(1234L, timestamps.value)
    }

    @Test
    fun missingOrMalformedMetadataDoesNotExposePartialSession() = runSessionStoreTest {
        val secrets = FakeSecureStore(
            mutableMapOf(SecureKey.SESSION_TOKEN to SecretValue.of("token")),
        )
        val store = SecureSessionStore(secrets, FakeTimestampStore())

        assertNull(store.load())
        assertNull(secrets.values[SecureKey.SESSION_TOKEN])
    }

    @Test
    fun migratesLegacyOnceAndRestoresAfterRestart() = runSessionStoreTest {
        val session = Session(SecretValue.of("legacy"), 2000L)
        val primary = FakeSessionStore()
        val legacy = FakeLegacySource(session)
        val store = MigratingSessionStore(primary, legacy)

        assertEquals(session, store.load())
        assertEquals(session, primary.session)
        assertNull(legacy.session)
        assertEquals(session, MigratingSessionStore(primary, legacy).load())
    }

    @Test
    fun primaryReadFailureDoesNotUsePlaintextFallback() = runSessionStoreTest {
        val session = Session(SecretValue.of("legacy"), 2000L)
        val primary = FakeSessionStore(failLoad = true)
        val legacy = FakeLegacySource(session)

        assertTrue(runCatching { MigratingSessionStore(primary, legacy).load() }.isFailure)
        assertEquals(session, legacy.session)
    }

    @Test
    fun saveDoesNotMirrorAndClearRemovesLegacyToken() = runSessionStoreTest {
        val primary = FakeSessionStore()
        val legacy = FakeLegacySource(Session(SecretValue.of("old"), 1L))
        val store = MigratingSessionStore(primary, legacy)
        val session = Session(SecretValue.of("new"), 3000L)

        store.save(session)
        assertEquals(session, primary.session)
        assertNull(legacy.session)

        store.clear()
        assertNull(primary.session)
        assertNull(legacy.session)
    }

    @Test
    fun clearAttemptsBothStoresWhenPrimaryFails() = runSessionStoreTest {
        val primary = FakeSessionStore(failClear = true)
        val legacy = FakeLegacySource(Session(SecretValue.of("legacy"), 1L))
        val result = runCatching { MigratingSessionStore(primary, legacy).clear() }

        assertTrue(result.isFailure)
        assertNull(legacy.session)
    }

    @Test
    fun failedMigrationRetainsLegacyForRetry() = runSessionStoreTest {
        val session = Session(SecretValue.of("legacy"), 2000L)
        val primary = FakeSessionStore(failSave = true)
        val legacy = FakeLegacySource(session)
        assertTrue(runCatching { MigratingSessionStore(primary, legacy).load() }.isFailure)
        assertEquals(session, legacy.session)
        primary.failSave = false
        assertEquals(session, MigratingSessionStore(primary, legacy).load())
        assertNull(legacy.session)
    }

    @Test
    fun failedVerificationRetainsLegacyForRetry() = runSessionStoreTest {
        val session = Session(SecretValue.of("legacy"), 2000L)
        val primary = FakeSessionStore(hideLoad = true)
        val legacy = FakeLegacySource(session)
        assertTrue(runCatching { MigratingSessionStore(primary, legacy).load() }.isFailure)
        assertEquals(session, legacy.session)
        primary.hideLoad = false
        assertEquals(session, MigratingSessionStore(primary, legacy).load())
    }

    private class FakeLegacySource(var session: Session? = null) : LegacySessionSource {
        override suspend fun load() = session
        override suspend fun removeToken() { session = null }
    }

    private class FakeSecureStore(
        val values: MutableMap<SecureKey, SecretValue> = mutableMapOf(),
    ) : SecureStore {
        override suspend fun read(key: SecureKey) = values[key]
        override suspend fun write(key: SecureKey, value: SecretValue) {
            values[key] = value
        }
        override suspend fun remove(key: SecureKey) {
            values.remove(key)
        }
    }

    private class FakeTimestampStore(var value: Long? = null) : SessionTimestampStore {
        override suspend fun read() = value
        override suspend fun write(value: Long) {
            this.value = value
        }
        override suspend fun clear() {
            value = null
        }
    }

    private class FakeSessionStore(
        var session: Session? = null,
        private val failLoad: Boolean = false,
        private val failClear: Boolean = false,
        var failSave: Boolean = false,
        var hideLoad: Boolean = false,
    ) : SessionStore {
        override suspend fun load(): Session? {
            if (failLoad) error("load failed")
            return if (hideLoad) null else session
        }
        override suspend fun save(session: Session) {
            if (failSave) error("save failed")
            this.session = session
        }
        override suspend fun clear() {
            if (failClear) error("clear failed")
            session = null
        }
    }
}

private fun <T> runSessionStoreTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return requireNotNull(outcome).getOrThrow()
}
