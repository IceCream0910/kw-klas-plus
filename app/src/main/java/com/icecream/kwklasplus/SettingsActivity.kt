package com.icecream.kwklasplus

import android.R.attr.data
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.icecream.kwklasplus.modal.LibraryQRSettingsBottomSheetDialog
import com.icecream.kwklasplus.modal.YearHakgiBottomSheetDialog


class SettingsActivity : AppCompatActivity() {
    var appVersion: String = ""
    lateinit var webView: WebView
    lateinit var sharedPreferences: SharedPreferences
    var currentAppTheme: String = "system"
    var savedYearHakgi: String = ""
    lateinit var savedYearHakgiList: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        currentAppTheme = sharedPreferences.getString("appTheme", "system").toString()
        savedYearHakgi = sharedPreferences.getString("yearHakgi", "").toString()
        savedYearHakgiList = sharedPreferences.getString("yearHakgiList", "")?.split("&")?.toTypedArray()!!

        val pInfo: PackageInfo =
            baseContext.packageManager.getPackageInfo(baseContext.packageName, 0)
        appVersion = pInfo.versionName.toString()

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.setBackgroundColor(0)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(JavaScriptInterfaceForSettings(this), "Android")
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

        webView.loadUrl("https://klasplus.yuntae.in/settings")
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
        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("yearHakgi", selectedYearHakgi)
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
            val sharedPreferences = activity.getSharedPreferences("com.icecream.kwklasplus", AppCompatActivity.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("appTheme", type)
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
}