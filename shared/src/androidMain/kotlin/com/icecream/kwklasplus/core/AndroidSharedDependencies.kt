package com.icecream.kwklasplus.core

import android.content.Context
import android.content.SharedPreferences
import com.icecream.kwklasplus.core.auth.AndroidCredentialStore
import com.icecream.kwklasplus.core.auth.AndroidHttpAuthDriver
import com.icecream.kwklasplus.core.auth.CredentialStore
import com.icecream.kwklasplus.core.auth.KlasAuthRepository
import com.icecream.kwklasplus.core.auth.LoginUseCase
import com.icecream.kwklasplus.core.auth.PrepareCredentialUseCase
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.academic.AcademicRepository
import com.icecream.kwklasplus.core.academic.DeadlineRepository
import com.icecream.kwklasplus.core.academic.TimetableRepository
import com.icecream.kwklasplus.core.attendance.AttendanceRepository
import com.icecream.kwklasplus.core.library.LibraryGateway
import com.icecream.kwklasplus.core.library.AndroidLibraryService
import com.icecream.kwklasplus.core.lock.AndroidAppLockSecretStore
import com.icecream.kwklasplus.core.lock.AndroidAppLockCredentialCodec
import com.icecream.kwklasplus.core.media.MediaMetadataRepository
import com.icecream.kwklasplus.core.migration.AndroidLegacySecretSource
import com.icecream.kwklasplus.core.migration.LegacyStoreId
import com.icecream.kwklasplus.core.migration.SecureStoreMigrator
import com.icecream.kwklasplus.core.network.AndroidCoreNetworkDependencies
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.profile.IdCardQrRepository
import com.icecream.kwklasplus.core.security.AndroidKeystoreSecureStore
import com.icecream.kwklasplus.core.session.AndroidPreferencesSessionStore
import com.icecream.kwklasplus.core.session.AndroidPreferencesSessionTimestampStore
import com.icecream.kwklasplus.core.session.AndroidWebCookieStore
import com.icecream.kwklasplus.core.session.Clock
import com.icecream.kwklasplus.core.session.MirroringSessionStore
import com.icecream.kwklasplus.core.session.SecureSessionStore
import com.icecream.kwklasplus.core.session.SessionCoordinator

class AndroidSharedDependencies(
    context: Context,
    private val preferences: SharedPreferences,
    private val encryptedPreferences: SharedPreferences,
    private val libraryCachePreferences: SharedPreferences,
    private val libraryEncryptedPreferences: SharedPreferences,
    private val clock: Clock = Clock(System::currentTimeMillis),
) {
    private val applicationContext = context.applicationContext
    private val network = AndroidCoreNetworkDependencies()

    val authRepository: KlasAuthRepository get() = network.authRepository
    val attendanceRepository: AttendanceRepository get() = network.attendanceRepository
    val academicRepository: AcademicRepository get() = network.academicRepository
    val timetableRepository: TimetableRepository get() = network.timetableRepository
    val deadlineRepository: DeadlineRepository get() = network.deadlineRepository
    val libraryGateway: LibraryGateway get() = network.libraryGateway
    val idCardQrRepository: IdCardQrRepository get() = network.idCardQrRepository
    val mediaMetadataRepository: MediaMetadataRepository get() = network.mediaMetadataRepository
    val libraryService by lazy {
        AndroidLibraryService(
            network.libraryGateway,
            libraryCachePreferences,
            libraryEncryptedPreferences,
            clock,
        )
    }

    val androidSecureStore: AndroidKeystoreSecureStore by lazy {
        AndroidKeystoreSecureStore(applicationContext)
    }
    val secureStore: SecureStore get() = androidSecureStore
    val appLockSecretStore by lazy {
        AndroidAppLockSecretStore(androidSecureStore, encryptedPreferences)
    }
    val appLockCredentialCodec by lazy { AndroidAppLockCredentialCodec() }

    private val legacySecretSource by lazy {
        AndroidLegacySecretSource { store ->
            when (store) {
                LegacyStoreId.PREFERENCES -> preferences
                LegacyStoreId.ENCRYPTED_PREFERENCES -> encryptedPreferences
                LegacyStoreId.LIBRARY_ENCRYPTED_PREFERENCES -> libraryEncryptedPreferences
            }
        }
    }
    val secureStoreMigrator by lazy {
        SecureStoreMigrator(legacySecretSource, secureStore)
    }
    val credentialStore: CredentialStore by lazy {
        AndroidCredentialStore(
            preferences,
            secureStore,
            secureStoreMigrator,
            legacySecretSource,
        )
    }
    val prepareCredentialUseCase by lazy {
        PrepareCredentialUseCase(authRepository, credentialStore)
    }
    fun httpAuthDriver(): WebAuthDriver = AndroidHttpAuthDriver()
    val sessionCoordinator by lazy {
        val primary = SecureSessionStore(
            secureStore,
            AndroidPreferencesSessionTimestampStore(preferences),
        )
        val compatibleStore = MirroringSessionStore(
            primary,
            AndroidPreferencesSessionStore(preferences),
        )
        SessionCoordinator(compatibleStore, AndroidWebCookieStore(), clock)
    }

    fun loginUseCase(webAuthDriver: WebAuthDriver) = LoginUseCase(
        prepareCredentialUseCase,
        credentialStore,
        webAuthDriver,
        sessionCoordinator,
    )
}
