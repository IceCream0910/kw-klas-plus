package com.icecream.kwklasplus.core.session

import android.content.SharedPreferences
import android.webkit.CookieManager
import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPreferencesSessionStore(
    private val preferences: SharedPreferences,
) : LegacySessionSource {
    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val token = preferences.getString(LegacyPreferenceKeys.KW_SESSION, null)
            ?.takeIf(String::isNotBlank)
            ?: return@withContext null
        val timestamp = preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null)
            ?.toLongOrNull()
            ?: return@withContext null
        Session(SecretValue.of(token), timestamp)
    }

    override suspend fun removeToken() = withContext(Dispatchers.IO) {
        if (!preferences.contains(LegacyPreferenceKeys.KW_SESSION)) return@withContext
        check(
            preferences.edit()
                .remove(LegacyPreferenceKeys.KW_SESSION)
                .commit(),
        )
    }
}

class AndroidPreferencesSessionTimestampStore(
    private val preferences: SharedPreferences,
) : SessionTimestampStore {
    override suspend fun read(): Long? =
        withContext(Dispatchers.IO) {
            preferences.getString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, null)?.toLongOrNull()
        }

    override suspend fun write(value: Long) = withContext(Dispatchers.IO) {
        check(
            preferences.edit()
                .putString(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP, value.toString())
                .commit(),
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP).commit())
    }
}

class AndroidWebCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : WebCookieStore {
    override suspend fun setSessionCookie(token: SecretValue) = withContext(Dispatchers.IO) {
        setSessionCookieNow(token)
    }

    override suspend fun clearSessionCookie() = withContext(Dispatchers.IO) {
        cookieManager.setCookie(KlasUrls.KLAS_BASE, EXPIRED_SESSION_COOKIE)
        cookieManager.flush()
    }

    fun setSessionCookieNow(token: SecretValue) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie(KlasUrls.KLAS_BASE, sessionCookie(token))
        cookieManager.flush()
    }

    private fun sessionCookie(token: SecretValue): String =
        "SESSION=${token.reveal()}; Path=/; Domain=.kw.ac.kr; Secure; HttpOnly"

    private companion object {
        const val EXPIRED_SESSION_COOKIE =
            "SESSION=; Max-Age=0; Path=/; Domain=.kw.ac.kr; Secure; HttpOnly"
    }
}
