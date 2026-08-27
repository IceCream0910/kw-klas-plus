package com.icecream.kwklasplus.core

import com.icecream.kwklasplus.core.academic.AcademicRepository
import com.icecream.kwklasplus.core.academic.DeadlineRepository
import com.icecream.kwklasplus.core.academic.IosDeadlineDateParsers
import com.icecream.kwklasplus.core.academic.TimetableRepository
import com.icecream.kwklasplus.core.auth.CredentialStore
import com.icecream.kwklasplus.core.auth.IosCredentialStore
import com.icecream.kwklasplus.core.auth.KlasAuthRepository
import com.icecream.kwklasplus.core.auth.LoginUseCase
import com.icecream.kwklasplus.core.auth.PrepareCredentialUseCase
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.network.KlasSessionHttpClient
import com.icecream.kwklasplus.core.network.createIosKlasHttpClient
import com.icecream.kwklasplus.core.platform.IosUserDefaultsPreferencesStore
import com.icecream.kwklasplus.core.platform.PreferencesStore
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.IosKeychainSecureStore
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.session.IosUserDefaultsSessionTimestampStore
import com.icecream.kwklasplus.core.session.IosWebCookieStore
import com.icecream.kwklasplus.core.session.SecureSessionStore
import com.icecream.kwklasplus.core.session.SessionCoordinator
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

class IosSharedDependencies(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val clock: Clock = Clock {
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    },
    secureStoreOverride: SecureStore? = null,
    cookieStoreOverride: IosWebCookieStore? = null,
) {
    private val httpClient by lazy { createIosKlasHttpClient() }
    private val longRunningClient by lazy { createIosKlasHttpClient(timeoutMillis = 30_000) }

    val authRepository: KlasAuthRepository by lazy { KlasAuthRepository(httpClient) }

    val academicRepository: AcademicRepository by lazy {
        AcademicRepository(KlasSessionHttpClient(httpClient))
    }

    val timetableRepository: TimetableRepository by lazy {
        TimetableRepository(KlasSessionHttpClient(longRunningClient))
    }

    val deadlineRepository: DeadlineRepository by lazy {
        DeadlineRepository(
            transport = KlasSessionHttpClient(longRunningClient),
            clock = clock,
            onlineLectureEndParser = IosDeadlineDateParsers.onlineLecture,
            assignmentEndParser = IosDeadlineDateParsers.assignment,
        )
    }

    val preferencesStore: PreferencesStore by lazy {
        IosUserDefaultsPreferencesStore(defaults)
    }

    fun stringPreference(key: String): String? =
        defaults.stringForKey(key)?.takeIf(String::isNotBlank)

    fun writeStringPreference(key: String, value: String) {
        defaults.setObject(value, key)
        defaults.synchronize()
    }

    val secureStore: SecureStore by lazy {
        secureStoreOverride ?: IosKeychainSecureStore()
    }

    val credentialStore: CredentialStore by lazy {
        IosCredentialStore(secureStore, defaults)
    }

    val prepareCredentialUseCase by lazy {
        PrepareCredentialUseCase(authRepository, credentialStore)
    }

    val webCookieStore: IosWebCookieStore by lazy {
        cookieStoreOverride ?: IosWebCookieStore()
    }

    val sessionCoordinator by lazy {
        SessionCoordinator(
            SecureSessionStore(
                secureStore,
                IosUserDefaultsSessionTimestampStore(defaults),
            ),
            webCookieStore,
            clock,
        )
    }

    fun loginUseCase(webAuthDriver: WebAuthDriver) = LoginUseCase(
        prepareCredentialUseCase,
        credentialStore,
        webAuthDriver,
        sessionCoordinator,
    )

    fun clearNonSecretPreferences() {
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_ID)
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_PASSWORD)
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_SESSION)
        defaults.removeObjectForKey(LegacyPreferenceKeys.KW_SESSION_TIMESTAMP)
        defaults.removeObjectForKey(LegacyPreferenceKeys.APP_THEME)
        defaults.removeObjectForKey(LegacyPreferenceKeys.YEAR_HAKGI)
        defaults.removeObjectForKey(LegacyPreferenceKeys.YEAR_HAKGI_LIST)
        defaults.synchronize()
    }
}
