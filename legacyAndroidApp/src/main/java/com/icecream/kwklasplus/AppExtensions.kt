package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.icecream.kwklasplus.R
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

val Context.appPreferences: SharedPreferences
    get() = getSharedPreferences(AppPrefs.MAIN, Context.MODE_PRIVATE)

val Context.encryptedPreferences: SharedPreferences
    get() {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

val Context.libraryQrCachePreferences: SharedPreferences
    get() = getSharedPreferences(AppPrefs.LIBRARY_QR_CACHE, Context.MODE_PRIVATE)

val Context.libraryEncryptedCachePreferences: SharedPreferences
    get() {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            this,
            "library_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

fun Context.getLibraryPassword(): String? {
    val encryptedPrefs = encryptedPreferences
    val regularPrefs = appPreferences
    
    var password = encryptedPrefs.getString(AppPrefs.LIBRARY_PASSWORD, null)
    if (password == null) {
        password = regularPrefs.getString(AppPrefs.LIBRARY_PASSWORD, null)
        if (password != null) {
            // Migrate
            encryptedPrefs.edit().putString(AppPrefs.LIBRARY_PASSWORD, password).apply()
            regularPrefs.edit().let { it.remove(AppPrefs.LIBRARY_PASSWORD); it.apply() }
        }
    }
    return password
}

object AppHttpClient {
    val default: OkHttpClient by lazy { OkHttpClient() }
}

fun AppCompatActivity.applyEdgeToEdgeInsets(
    @IdRes rootViewId: Int = R.id.main,
    onInsetsApplied: ((WindowInsetsCompat) -> Unit)? = null
) {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(rootViewId)) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        onInsetsApplied?.invoke(insets)
        insets
    }
}

fun AppCompatActivity.lockPortraitOnPhone() {
    requestedOrientation = if (isTablet(this)) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

@SuppressLint("JavascriptInterface")
fun WebView.configureAppWebView(
    javaScriptInterface: Any? = null,
    interfaceName: String = "Android",
    allowFileAccess: Boolean? = null,
    allowContentAccess: Boolean? = null,
    supportMultipleWindows: Boolean? = null,
    javaScriptCanOpenWindowsAutomatically: Boolean = false,
    transparentBackground: Boolean = true,
    disableScrollBars: Boolean = true,
    domStorageEnabled: Boolean = true,
    mediaPlaybackRequiresUserGesture: Boolean? = null
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = domStorageEnabled
    allowFileAccess?.let { settings.allowFileAccess = it }
    allowContentAccess?.let { settings.allowContentAccess = it }
    supportMultipleWindows?.let { settings.setSupportMultipleWindows(it) }
    settings.javaScriptCanOpenWindowsAutomatically = javaScriptCanOpenWindowsAutomatically
    mediaPlaybackRequiresUserGesture?.let { settings.mediaPlaybackRequiresUserGesture = it }

    if (transparentBackground) {
        setBackgroundColor(0)
    }
    if (disableScrollBars) {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }
    javaScriptInterface?.let { addJavascriptInterface(it, interfaceName) }
}

fun hapticFeedbackConstant(type: String): Int = when (type) {
    "CLOCK_TICK" -> HapticFeedbackConstants.CLOCK_TICK
    "KEYBOARD_TAP" -> HapticFeedbackConstants.KEYBOARD_TAP
    "KEYBOARD_RELEASE" -> HapticFeedbackConstants.KEYBOARD_RELEASE
    "LONG_PRESS" -> HapticFeedbackConstants.LONG_PRESS
    "VIRTUAL_KEY" -> HapticFeedbackConstants.VIRTUAL_KEY
    "VIRTUAL_KEY_RELEASE" -> HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
    "TEXT_HANDLE_MOVE" -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    "CONFIRM" -> HapticFeedbackConstants.CONFIRM
    "REJECT" -> HapticFeedbackConstants.REJECT
    "DRAG_START" -> HapticFeedbackConstants.DRAG_START
    "GESTURE_START" -> HapticFeedbackConstants.GESTURE_START
    "GESTURE_END" -> HapticFeedbackConstants.GESTURE_END
    "TOGGLE_OFF" -> HapticFeedbackConstants.TOGGLE_OFF
    "TOGGLE_ON" -> HapticFeedbackConstants.TOGGLE_ON
    else -> HapticFeedbackConstants.CLOCK_TICK
}

fun syncSessionCookie(sessionId: String) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setCookie(AppUrls.KLAS_BASE, "SESSION=$sessionId; Path=/; Domain=.kw.ac.kr; Secure; HttpOnly")
    cookieManager.flush()
}

fun AppCompatActivity.startActivityWithLock(intent: Intent) {
    if (com.icecream.kwklasplus.manager.AppLockManager.isAppLockEnabled(this) && !com.icecream.kwklasplus.manager.AppLockManager.isUnlocked) {
        val lockIntent = Intent(this, LockActivity::class.java).apply {
            putExtra("MODE", "UNLOCK")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(lockIntent)
    }
    startActivity(intent)
}

fun Context.buildKlasJsonRequest(
    url: String,
    sessionId: String,
    requestBody: RequestBody? = null
): Request {
    val defaultUserAgent = WebSettings.getDefaultUserAgent(this)
    val requestBuilder = Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .header("Cookie", "SESSION=$sessionId")
        .header("User-Agent", "$defaultUserAgent NuriwareApp")

    if (requestBody != null) {
        requestBuilder.post(requestBody)
    }

    return requestBuilder.build()
}