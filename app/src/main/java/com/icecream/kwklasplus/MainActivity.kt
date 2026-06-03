package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.loadingindicator.LoadingIndicator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.performHapticFeedback
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var loadingText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var loadingHintRunnable: Runnable? = null
    private var loadingTimeoutRunnable: Runnable? = null
    private var isLoginActivityStarted = false
    private var isHomeStarted = false
    private var errorDialog: AlertDialog? = null
    private var loginAttempted = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets()
        loadingText = findViewById(R.id.loadingText)

        val sharedPreferences = appPreferences
        val appTheme = sharedPreferences.getString(AppPrefs.APP_THEME, "system")
        when (appTheme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        lockPortraitOnPhone()

        // 네트워크 연결 상태 확인
        if (!isNetworkConnected()) {
            var builder = MaterialAlertDialogBuilder(this)
            builder
                .setTitle("네트워크 연결 오류")
                .setMessage("네트워크 연결 상태를 확인해주세요.")
                .setPositiveButton("확인") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

        val kwID = sharedPreferences.getString(AppPrefs.KW_ID, null)
        val kwPWD = sharedPreferences.getString(AppPrefs.KW_PASSWORD, null)
        var isInstantLogin = false
        val webView = findViewById<WebView>(R.id.webView)
        webView.configureAppWebView(
            disableScrollBars = false,
            transparentBackground = false,
            domStorageEnabled = false
        )
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url == AppUrls.KLAS_LOGIN) {
                    if (loginAttempted) {
                        // If we are still on login page after attempt, and no JS alert was shown
                        showSecurityActionRequiredDialog()
                    } else {
                        webView.evaluateJavascript(
                            "javascript:appLogin.setInitial('on', '$kwID', '$kwPWD')",
                            null
                        )
                        loginAttempted = true
                    }
                }

                if (url != AppUrls.KLAS_LOGIN) {
                    val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                    val session = cookies.split("; ")
                        .firstOrNull { it.startsWith("SESSION=") }
                        ?.split("=", limit = 2)
                        ?.getOrNull(1)
                    if (!session.isNullOrBlank()) {
                        with(sharedPreferences.edit()) {
                            putString(AppPrefs.KW_SESSION, session)
                            putString(
                                AppPrefs.KW_SESSION_TIMESTAMP,
                                System.currentTimeMillis().toString()
                            )
                            apply()
                        }
                        if (!isInstantLogin && !isHomeStarted) {
                            cancelLoginTimers()
                            isLoginActivityStarted = true
                            isHomeStarted = true
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    HomeActivity::class.java
                                )
                            )
                        }
                        finish()
                    }
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        result?.cancel()
                        return@runOnUiThread
                    }
                    val root = findViewById<View>(R.id.main)
                    root.performHapticFeedback(HapticFeedbackConstants.REJECT)

                    if (message?.contains("자동 입력 방지") == true) {
                        result?.confirm()
                        showSecurityActionRequiredDialog()
                        return@runOnUiThread
                    }

                    val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("오류")
                        .setMessage(message)
                        .setPositiveButton("확인") { _, _ ->
                            result?.confirm()
                            if (!isFinishing && !isDestroyed) {
                                finish()
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            }
                        }
                        .setCancelable(false)
                        .create()
                    errorDialog = dialog
                    dialog.show()
                }
                return true
            }
        }
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Whale/3.25.232.19 Safari/537.36"
        // webView 초기화 완료

        if (kwID == null || kwPWD == null) { // 로그인 정보 없으면 로그인 화면으로 이동
            finish()
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            val instantSession = sharedPreferences.getString(AppPrefs.KW_SESSION, null)
            val instantSessionTimestamp =
                sharedPreferences.getString(AppPrefs.KW_SESSION_TIMESTAMP, null)
            isInstantLogin = false
            if (instantSession != null && instantSessionTimestamp != null) {
                val timestamp = instantSessionTimestamp.toLong()
                if (System.currentTimeMillis() - timestamp < 1000 * 60 * 60) { // 1시간 이내 세션 정보 있으면 바로 실행
                    isInstantLogin = true
                    isHomeStarted = true
                    syncSessionCookie(instantSession)
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                    return
                }
            }

            startLoginTimers(webView)
            webView.loadUrl(AppUrls.KLAS_LOGIN)
        }
    }

    private fun startLoginTimers(webView: WebView) {
        cancelLoginTimers()
        loadingText.text = "로그인 중"
        val hintRunnable = Runnable {
            if (!isLoginActivityStarted && !isHomeStarted && !isFinishing && !isDestroyed) {
                loadingText.text = "조금만 더 기다려주세요"
            }
        }
        val timeoutRunnable = Runnable {
            if (!isLoginActivityStarted && !isHomeStarted) {
                showLoginFailedDialog(webView)
            }
        }
        loadingHintRunnable = hintRunnable
        loadingTimeoutRunnable = timeoutRunnable
        handler.postDelayed(hintRunnable, 7000)
        handler.postDelayed(timeoutRunnable, 15000)
    }

    private fun cancelLoginTimers() {
        loadingHintRunnable?.let { handler.removeCallbacks(it) }
        loadingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        loadingHintRunnable = null
        loadingTimeoutRunnable = null
    }

    private fun showLoginFailedDialog(webView: WebView) {
        if (isFinishing || isDestroyed || errorDialog != null) {
            return
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("로그인 실패")
            .setMessage("알 수 없는 오류로 인해 로그인에 실패했어요. 먼저 기기의 네트워크 상태가 불안정한지 확인 후 다시 시도해보세요. 어쩌면 전체적인 서버 장애가 발생했을 수도 있어요. 이 경우 담당자가 빠르게 대응하고 있을거예요.")
            .setNeutralButton("앱 종료") { _, _ ->
                val sharedPreferences =
                    appPreferences
                val editor = sharedPreferences.edit()
                editor.clear()
                editor.apply()
                finish() }
            .setNegativeButton("서버 상태 확인") { _, _ ->
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(AppUrls.STATUS)
                )
                startActivity(browserIntent)
                finish()
            }
            .setPositiveButton("다시 시도") { _, _ ->
                if (!isFinishing && !isDestroyed) {
                    startLoginTimers(webView)
                    webView.loadUrl(AppUrls.KLAS_LOGIN)
                }
            }
            .setCancelable(false)
            .create()
        errorDialog = dialog
        dialog.show()
    }

    private fun showSecurityActionRequiredDialog() {
        if (isFinishing || isDestroyed || errorDialog != null) return

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("로그인 실패")
            .setMessage("임시 비밀번호 변경이 필요하거나 자동완성 방지 문자(CAPTCHA) 입력이 필요할 수 있어요. 계정 보안을 위해 외부 브라우저에서 KLAS에 먼저 로그인하신 후 다시 시도해 주세요.")
            .setPositiveButton("브라우저 열기") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppUrls.KLAS_LOGIN))
                startActivity(intent)
                finish()
            }
            .setNegativeButton("종료") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        errorDialog = dialog
        dialog.show()
    }

    // 네트워크 연결 여부 확인 함수
    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelLoginTimers()
        errorDialog?.dismiss()
        errorDialog = null
    }
}
