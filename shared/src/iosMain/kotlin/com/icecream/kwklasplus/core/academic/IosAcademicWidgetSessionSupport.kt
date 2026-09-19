package com.icecream.kwklasplus.core.academic

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import platform.Foundation.NSUserDefaults

class IosAcademicWidgetSharedFlags(
    private val defaults: NSUserDefaults? = NSUserDefaults(suiteName = IosAcademicWidgetDisplayStore.APP_GROUP),
) {
    fun revision(): Long = defaults?.stringForKey(REVISION_KEY)?.toLongOrNull() ?: 0L

    fun bumpRevision() {
        val next = revision() + 1L
        defaults?.setObject(next.toString(), REVISION_KEY)
        defaults?.synchronize()
    }

    fun cookieSyncNeeded(): Boolean = defaults?.boolForKey(COOKIE_SYNC_KEY) == true

    fun markCookieSyncNeeded() {
        defaults?.setBool(true, COOKIE_SYNC_KEY)
        defaults?.synchronize()
    }

    fun clearCookieSyncNeeded() {
        defaults?.setBool(false, COOKIE_SYNC_KEY)
        defaults?.synchronize()
    }

    fun writeIdentity(accountId: String?, yearHakgi: String?) {
        if (accountId.isNullOrBlank()) {
            defaults?.removeObjectForKey(LegacyPreferenceKeys.KW_ID)
        } else {
            defaults?.setObject(accountId, LegacyPreferenceKeys.KW_ID)
        }
        if (yearHakgi.isNullOrBlank()) {
            defaults?.removeObjectForKey(LegacyPreferenceKeys.YEAR_HAKGI)
        } else {
            defaults?.setObject(yearHakgi, LegacyPreferenceKeys.YEAR_HAKGI)
        }
        defaults?.synchronize()
    }

    fun accountId(): String? = defaults?.stringForKey(LegacyPreferenceKeys.KW_ID)?.takeIf { it.isNotBlank() }

    fun yearHakgi(): String? =
        defaults?.stringForKey(LegacyPreferenceKeys.YEAR_HAKGI)?.takeIf { it.isNotBlank() }

    fun writeTimestamp(value: Long) {
        defaults?.setObject(value.toString(), LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        defaults?.synchronize()
    }

    fun timestamp(): Long? = defaults?.stringForKey(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)?.toLongOrNull()

    fun clearTimestamp() {
        defaults?.removeObjectForKey(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        defaults?.synchronize()
    }

    fun clearIdentityAndSessionFlags() {
        writeIdentity(null, null)
        clearTimestamp()
        clearCookieSyncNeeded()
        defaults?.removeObjectForKey(REVISION_KEY)
        defaults?.synchronize()
    }

    companion object {
        const val REVISION_KEY = "academic_widget_session_revision"
        const val COOKIE_SYNC_KEY = "academic_widget_cookie_sync_needed"
    }
}

class IosMirroredSessionTimestampStore(
    private val primary: com.icecream.kwklasplus.core.session.SessionTimestampStore,
    private val flags: IosAcademicWidgetSharedFlags,
) : com.icecream.kwklasplus.core.session.SessionTimestampStore {
    override suspend fun read(): Long? = flags.timestamp() ?: primary.read()

    override suspend fun write(value: Long) {
        flags.writeTimestamp(value)
        primary.write(value)
    }

    override suspend fun clear() {
        var failure: Throwable? = null
        runCatching { flags.clearTimestamp() }.onFailure { failure = it }
        runCatching { primary.clear() }.onFailure { if (failure == null) failure = it }
        failure?.let { throw it }
    }
}

class IosRevisingSessionStore(
    private val inner: com.icecream.kwklasplus.core.session.SessionStore,
    private val flags: IosAcademicWidgetSharedFlags,
    private val markCookieSync: Boolean,
) : com.icecream.kwklasplus.core.session.SessionStore {
    override suspend fun load() = inner.load()

    override suspend fun save(session: com.icecream.kwklasplus.core.session.Session) {
        inner.save(session)
        flags.bumpRevision()
        if (markCookieSync) flags.markCookieSyncNeeded()
    }

    override suspend fun clear() {
        inner.clear()
        flags.bumpRevision()
        flags.clearCookieSyncNeeded()
    }
}

class IosNoOpWebCookieStore : com.icecream.kwklasplus.core.session.WebCookieStore {
    override suspend fun setSessionCookie(token: com.icecream.kwklasplus.core.security.SecretValue) = Unit

    override suspend fun clearSessionCookie() = Unit
}

class IosCookieSyncingWebCookieStore(
    private val inner: com.icecream.kwklasplus.core.session.WebCookieStore,
    private val flags: IosAcademicWidgetSharedFlags,
) : com.icecream.kwklasplus.core.session.WebCookieStore {
    override suspend fun setSessionCookie(token: com.icecream.kwklasplus.core.security.SecretValue) {
        inner.setSessionCookie(token)
        flags.clearCookieSyncNeeded()
    }

    override suspend fun clearSessionCookie() {
        inner.clearSessionCookie()
        flags.clearCookieSyncNeeded()
    }
}
