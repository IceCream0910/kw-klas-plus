package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.core.testing.InMemorySharedPreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidSessionStoresTest {
    @Test
    fun preservesLegacySessionKeysAndStringTimestamp() = runBlocking {
        val preferences = InMemorySharedPreferences()
        val store = AndroidPreferencesSessionStore(preferences)
        val session = Session(SecretValue.of("token"), 1234L)

        store.save(session)

        assertEquals("token", preferences.getString(LegacyPreferenceKeys.KW_SESSION, null))
        assertEquals("1234", preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null))
        assertEquals(session, store.load())
        store.clear()
        assertNull(store.load())
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
