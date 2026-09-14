package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.testing.InMemorySharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidSessionStoresTest {
    @Test
    fun upgradedSessionSurvivesRestartAndLogoutRemovesBothCopies() = runBlocking {
        val preferences = InMemorySharedPreferences().apply {
            edit().putString(LegacyPreferenceKeys.KW_SESSION, "legacy")
                .putString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, "1234").commit()
        }
        val secrets = object : SecureStore {
            var token: SecretValue? = null
            override suspend fun read(key: SecureKey) = token
            override suspend fun write(key: SecureKey, value: SecretValue) { token = value }
            override suspend fun remove(key: SecureKey) { token = null }
        }
        fun store() = MigratingSessionStore(
            SecureSessionStore(secrets, AndroidPreferencesSessionTimestampStore(preferences)),
            AndroidPreferencesSessionStore(preferences),
        )

        assertEquals("legacy", store().load()?.token?.reveal())
        assertNull(preferences.getString(LegacyPreferenceKeys.KW_SESSION, null))
        assertEquals("1234", preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null))
        assertEquals("legacy", store().load()?.token?.reveal())
        store().save(Session(SecretValue.of("renewed"), 2345L))
        assertEquals("renewed", store().load()?.token?.reveal())
        assertNull(preferences.getString(LegacyPreferenceKeys.KW_SESSION, null))
        store().clear()
        assertNull(store().load())
        assertNull(preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null))
    }

    @Test
    fun removesOnlyLegacyTokenAfterReadingIt() = runBlocking {
        val preferences = InMemorySharedPreferences().apply {
            edit().putString(LegacyPreferenceKeys.KW_SESSION, "token")
                .putString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, "1234").commit()
        }
        val store = AndroidPreferencesSessionStore(preferences)
        val session = Session(SecretValue.of("token"), 1234L)

        assertEquals(session, store.load())
        store.removeToken()
        assertNull(store.load())
        assertNull(preferences.getString(LegacyPreferenceKeys.KW_SESSION, null))
        assertEquals("1234", preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null))
    }

    @Test
    fun malformedTimestampDoesNotExposePartialSession() = runBlocking {
        val preferences = InMemorySharedPreferences().apply {
            edit()
                .putString(LegacyPreferenceKeys.KW_SESSION, "token")
                .putString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, "broken")
                .commit()
        }

        assertNull(AndroidPreferencesSessionStore(preferences).load())
    }
}
