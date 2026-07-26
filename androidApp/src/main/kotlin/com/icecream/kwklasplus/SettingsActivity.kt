package com.icecream.kwklasplus

import android.R.attr.data
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import com.icecream.kwklasplus.modal.YearHakgiBottomSheetDialog
import com.icecream.kwklasplus.manager.AppLockManager
import com.icecream.kwklasplus.platform.biometric.AndroidBiometricAvailability
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.lock.AppLockSettings
import com.icecream.kwklasplus.core.platform.BiometricPurpose
import com.icecream.kwklasplus.core.platform.PlatformActionResult
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.bridge.legacy.SettingsLegacyBridgeCommandHandler
import com.icecream.kwklasplus.platform.bridge.legacy.SettingsLegacySynchronousBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import kotlinx.coroutines.launch
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.web.ComposeWebViewHost


class SettingsActivity : AppCompatActivity() {
    var appVersion: String = ""
    lateinit var webView: WebView
    lateinit var sharedPreferences: SharedPreferences
    var currentAppTheme: String = "system"
    var savedYearHakgi: String = ""
    lateinit var savedYearHakgiList: Array<String>
    var isDisablingProcess = false
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null

    val lockSetupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            val currentEnabled = AppLockManager.isAppLockEnabled(this)
            webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.APP_LOCK_SETTING_CHANGED,
                    JavaScriptArgument.BooleanValue(currentEnabled),
                ),
            )
            Toast.makeText(this, "인증이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            if (isDisablingProcess) {
                AppLockManager.setAppLockEnabled(this, false)
                Toast.makeText(this, "앱 잠금이 비활성화되고 비밀번호가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            }

            webView.executeWebScript(LegacyWebScripts.appLockSettingChanged(currentAppLockSettings()))
        }
        isDisablingProcess = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = appPreferences
        currentAppTheme = sharedPreferences.getString(AppPrefs.APP_THEME, "system").toString()
        savedYearHakgi = sharedPreferences.getString(AppPrefs.YEAR_HAKGI, "").toString()
        savedYearHakgiList = sharedPreferences.getString(AppPrefs.YEAR_HAKGI_LIST, "")
            .orEmpty()
            .split("&")
            .toTypedArray()

        val pInfo: PackageInfo =
            baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
        appVersion = pInfo.versionName.toString()

        webView = WebView(this)
        webSurface = AndroidWebSurface(webView)
        setContent {
            KlasPlusTheme {
                ComposeWebViewHost(
                    webView = webView,
                    isLoading = false,
                    title = "설정",
                )
            }
        }
        val legacyFacade = JavaScriptInterfaceForSettings(this)
        webView.configureAppWebView(
            javaScriptInterface = legacyFacade,
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.SETTINGS,
            lifecycleScope,
            SettingsLegacyBridgeCommandHandler(legacyFacade),
            SettingsLegacySynchronousBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        try {
            val version = pInfo.longVersionCode
            webView.settings.userAgentString += " AndroidApp_v${version}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@SettingsActivity)
                    builder.setTitle("안내")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                        }
                        .setCancelable(false)
                        .show()
                }
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@SettingsActivity)
                    builder.setTitle("안내")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                        }
                        .setNegativeButton("취소") { dialog, id ->
                            result?.cancel()
                        }
                        .setCancelable(false)
                        .show()
                }
                return true
            }
        }
        webView.webViewClient = AndroidWebSurfaceClient(requireNotNull(webSurface))

        webView.loadUrl(AppUrls.SETTINGS)
    }


    fun openYearHakgiBottomSheetDialog(isUpdate: Boolean = false) {
        val yearHakgiDialog = YearHakgiBottomSheetDialog(savedYearHakgiList, isUpdate).apply {
            setSpeedSelectionListener(object : YearHakgiBottomSheetDialog.YearHakgiSelectionListener {
                override fun onYearHakgiSelected(value: String) {
                    updateYearHakgi(value)
                }
            })
        }

        yearHakgiDialog.show(supportFragmentManager, YearHakgiBottomSheetDialog.TAG)
    }

    private fun updateYearHakgi(selectedYearHakgi: String) {
        savedYearHakgi = selectedYearHakgi
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString(AppPrefs.YEAR_HAKGI, selectedYearHakgi)
        editor.apply()
        webView.executeWebScript(
            LegacyWebScripts.call(
                LegacyWebCallback.RECEIVE_YEAR_SEMESTER,
                JavaScriptArgument.Text(selectedYearHakgi),
            ),
        )
    }

    fun notifyBiometricSettingChanged(enabled: Boolean) {
        webView.executeWebScript(
            LegacyWebScripts.call(
                LegacyWebCallback.BIOMETRIC_SETTING_CHANGED,
                JavaScriptArgument.BooleanValue(enabled),
            ),
        )
    }

    fun currentAppLockSettings(): AppLockSettings = AppLockSettings(
        enabled = AppLockManager.isAppLockEnabled(this),
        biometricEnabled = AppLockManager.isBiometricEnabled(this),
        hasPassword = AppLockManager.hasPassword(this),
    )
    override fun onDestroy() {
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroy()
    }
}

class JavaScriptInterfaceForSettings(private val activity: SettingsActivity) {
    @JavascriptInterface
    fun completePageLoad() {
        activity.runOnUiThread {
            activity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_THEME,
                    JavaScriptArgument.Text(activity.currentAppTheme),
                ),
            )
            activity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_YEAR_SEMESTER,
                    JavaScriptArgument.Text(activity.savedYearHakgi),
                ),
            )
            activity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_VERSION,
                    JavaScriptArgument.Text(activity.appVersion),
                ),
            )
        }
    }

    @JavascriptInterface
    fun changeAppTheme(type: String) {
        activity.runOnUiThread {
            activity.currentAppTheme = type
            when (type) {
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else -> return@runOnUiThread
            }
            with(activity.appPreferences.edit()) {
                putString(AppPrefs.APP_THEME, type)
                apply()
            }
        }
    }

    @JavascriptInterface
    fun openYearHakgiSelectModal() {
        activity.runOnUiThread {
            activity.openYearHakgiBottomSheetDialog()
        }
    }

    @JavascriptInterface
    fun openLibraryQRSettingsModal() {
        activity.runOnUiThread {
            val settingsModal = LibraryQRSettingsBottomSheetDialog()
            settingsModal.show(activity.supportFragmentManager, "LibraryQRSettingsModal")
        }
    }

    @JavascriptInterface
    fun openExternalLink(link: String) {
        activity.runOnUiThread {
            activity.openValidatedExternalDestination(link)
        }
    }

    @JavascriptInterface
    fun performHapticFeedback(type: String) {
        activity.runOnUiThread {
            activity.appDependencies.haptics(activity.webView).performLegacy(type)
        }
    }

    @JavascriptInterface
    fun setAppLockEnabled(enabled: Boolean) {
        activity.runOnUiThread {
            if (enabled) {
                activity.isDisablingProcess = false
                setAppLockPassword()
            } else {
                activity.isDisablingProcess = true
                val intent = Intent(activity, LockActivity::class.java).apply {
                    putExtra("MODE", "VERIFY")
                }
                activity.lockSetupLauncher.launch(intent)
            }
        }
    }

    @JavascriptInterface
    fun setAppLockPassword() {
        activity.runOnUiThread {
            val mode = if (AppLockManager.hasPassword(activity)) "CHANGE" else "SET"
            val intent = Intent(activity, LockActivity::class.java).apply {
                putExtra("MODE", mode)
            }
            activity.lockSetupLauncher.launch(intent)
        }
    }

    @JavascriptInterface
    fun setBiometricEnabled(enabled: Boolean) {
        activity.runOnUiThread {
            if (enabled) {
                val errorMessage = AndroidBiometricAvailability.errorMessage(activity)
                if (errorMessage != null) {
                    Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show()
                    activity.notifyBiometricSettingChanged(false)
                    return@runOnUiThread
                }

                activity.lifecycleScope.launch {
                    when (
                        activity.appDependencies.biometrics(activity)
                            .authenticate(BiometricPurpose.ENABLE_BIOMETRICS)
                    ) {
                        PlatformActionResult.Success -> {
                            AppLockManager.setBiometricEnabled(activity, true)
                            Toast.makeText(activity, "생체인증이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
                            activity.notifyBiometricSettingChanged(true)
                        }
                        else -> {
                            activity.notifyBiometricSettingChanged(false)
                        }
                    }
                }
            } else {
                AppLockManager.setBiometricEnabled(activity, false)
                Toast.makeText(activity, "생체인증이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()
                activity.notifyBiometricSettingChanged(false)
            }
        }
    }

    @JavascriptInterface
    fun getAppLockSettings(): String {
        return activity.currentAppLockSettings().toLegacyJson()
    }
}
