package com.icecream.kwklasplus

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.icecream.kwklasplus.core.AndroidSharedDependencies
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.platform.AndroidExternalNavigator
import com.icecream.kwklasplus.core.platform.AndroidHaptics
import com.icecream.kwklasplus.manager.AppDownloadManager
import com.icecream.kwklasplus.platform.biometric.AndroidBiometrics
import com.icecream.kwklasplus.platform.navigation.AndroidRouteNavigator
import com.icecream.kwklasplus.platform.pip.AndroidPictureInPicture
import com.icecream.kwklasplus.platform.qr.AndroidQrScanner

class AndroidAppDependencies(context: Context) {
    private val applicationContext = context.applicationContext
    private val shared = AndroidSharedDependencies(
        context = applicationContext,
        preferences = applicationContext.appPreferences,
        encryptedPreferences = applicationContext.encryptedPreferences,
        libraryCachePreferences = applicationContext.libraryQrCachePreferences,
        libraryEncryptedPreferences = applicationContext.libraryEncryptedCachePreferences,
    )

    val authRepository get() = shared.authRepository
    val attendanceRepository get() = shared.attendanceRepository
    val academicRepository get() = shared.academicRepository
    val timetableRepository get() = shared.timetableRepository
    val deadlineRepository get() = shared.deadlineRepository
    val libraryGateway get() = shared.libraryGateway
    val idCardQrRepository get() = shared.idCardQrRepository
    val mediaMetadataRepository get() = shared.mediaMetadataRepository
    val libraryService get() = shared.libraryService
    val androidSecureStore get() = shared.androidSecureStore
    val secureStore get() = shared.secureStore
    val appLockSecretStore get() = shared.appLockSecretStore
    val appLockCredentialCodec get() = shared.appLockCredentialCodec
    val secureStoreMigrator get() = shared.secureStoreMigrator
    val credentialStore get() = shared.credentialStore
    val prepareCredentialUseCase get() = shared.prepareCredentialUseCase
    val sessionCoordinator get() = shared.sessionCoordinator
    fun httpAuthDriver() = shared.httpAuthDriver()

    fun loginUseCase(webAuthDriver: WebAuthDriver) = shared.loginUseCase(webAuthDriver)

    fun externalNavigator(context: Context) = AndroidExternalNavigator(context)

    fun haptics(view: View) = AndroidHaptics(view)

    fun biometrics(activity: FragmentActivity) = AndroidBiometrics(activity)

    fun pictureInPicture(activity: Activity) = AndroidPictureInPicture(activity)

    fun qrScanner(activity: Activity) = AndroidQrScanner(activity)

    fun fileTransfer(activity: Activity) = AppDownloadManager(activity)

    fun routeNavigator(activity: Activity) = AndroidRouteNavigator(activity)
}

val Context.appDependencies: AndroidAppDependencies
    get() = (applicationContext as MainApplication).dependencies
