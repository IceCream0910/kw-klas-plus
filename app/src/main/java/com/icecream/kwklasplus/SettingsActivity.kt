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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import com.icecream.kwklasplus.modal.YearHakgiBottomSheetDialog
import com.icecream.kwklasplus.manager.AppLockManager


class SettingsActivity : AppCompatActivity() {
    var appVersion: String = ""
    lateinit var webView: WebView
    lateinit var sharedPreferences: SharedPreferences
    var currentAppTheme: String = "system"
    var savedYearHakgi: String = ""
    lateinit var savedYearHakgiList: Array<String>
    var isDisablingProcess = false

    val lockSetupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            val currentEnabled = AppLockManager.isAppLockEnabled(this)
            webView.evaluateJavascript("window.onAppLockSettingChanged($currentEnabled)", null)
            Toast.makeText(this, "인증이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            if (isDisablingProcess) {
                AppLockManager.setAppLockEnabled(this, false)
                Toast.makeText(this, "앱 잠금이 비활성화되고 비밀번호가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            }
            // If it was SET/CHANGE mode, AppLockManager already updated the state.
            
            val settings = JavaScriptInterfaceForSettings(this).getAppLockSettings()
            webView.evaluateJavascript("window.onAppLockSettingChanged($settings)", null)
        }
        isDisablingProcess = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyEdgeToEdgeInsets()

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

        webView = findViewById(R.id.webView)
        webView.configureAppWebView(
            javaScriptInterface = JavaScriptInterfaceForSettings(this),
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
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
        webView.evaluateJavascript(
            "window.receiveYearHakgi('$selectedYearHakgi')",
            null
        )
    }
}

class JavaScriptInterfaceForSettings(private val activity: SettingsActivity) {
    @JavascriptInterface
    fun completePageLoad() {
        activity.runOnUiThread {
            activity.webView.evaluateJavascript(
                "window.receiveTheme('${activity.currentAppTheme}')",
                null)
            activity.webView.evaluateJavascript(
                "window.receiveYearHakgi('${activity.savedYearHakgi}')",
                null)
            activity.webView.evaluateJavascript(
                "window.receiveVersion('${activity.appVersion}')",
                null)
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun performHapticFeedback(type: String) {
        activity.runOnUiThread {
            activity.webView.performHapticFeedback(hapticFeedbackConstant(type))
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
                // Require biometric authentication before enabling
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            AppLockManager.setBiometricEnabled(activity, true)
                            Toast.makeText(activity, "생체인증이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
                            activity.webView.evaluateJavascript("window.onBiometricSettingChanged(true)", null)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            activity.webView.evaluateJavascript("window.onBiometricSettingChanged(false)", null)
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("생체인증 사용")
                    .setNegativeButtonText("취소")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } else {
                AppLockManager.setBiometricEnabled(activity, false)
                Toast.makeText(activity, "생체인증이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()
                activity.webView.evaluateJavascript("window.onBiometricSettingChanged(false)", null)
            }
        }
    }

    @JavascriptInterface
    fun getAppLockSettings(): String {
        val enabled = AppLockManager.isAppLockEnabled(activity)
        val biometric = AppLockManager.isBiometricEnabled(activity)
        val hasPassword = AppLockManager.hasPassword(activity)
        return "{\"enabled\": $enabled, \"biometric\": $biometric, \"hasPassword\": $hasPassword}"
    }
}
