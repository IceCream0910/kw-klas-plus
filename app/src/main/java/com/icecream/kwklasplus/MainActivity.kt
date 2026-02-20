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
    private var loadingTimeoutRunnable: Runnable? = null
    private var isLoginActivityStarted = false
    private var errorDialog: AlertDialog? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        loadingText = findViewById(R.id.loadingText)

        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val appTheme = sharedPreferences.getString("appTheme", "system")
        when (appTheme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

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

        val kwID = sharedPreferences.getString("kwID", null)
        val kwPWD = sharedPreferences.getString("kwPWD", null)
        var isInstantLogin = false
        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript(
                    "javascript:appLogin.setInitial('on', '$kwID', '$kwPWD')",
                    null
                )
                if (url != "https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do") {
                    val cookies = CookieManager.getInstance().getCookie(url)
                    val session = cookies.split("; ").find { it.startsWith("SESSION=") }?.split("=")?.get(1)
                    if (session != null) {
                        with(sharedPreferences.edit()) {
                            putString("kwSESSION", session)
                            putString("kwSESSION_timestamp", System.currentTimeMillis().toString())
                            apply()
                        }
                        if (!isInstantLogin) {
                            loadingTimeoutRunnable?.let { handler.removeCallbacks(it) }
                            isLoginActivityStarted = true
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
            val instantSession = sharedPreferences.getString("kwSESSION", null)
            val instantSessionTimestamp = sharedPreferences.getString("kwSESSION_timestamp", null)
            isInstantLogin = false
            if (instantSession != null && instantSessionTimestamp != null) {
                val timestamp = instantSessionTimestamp.toLong()
                if (System.currentTimeMillis() - timestamp < 1000 * 60 * 60) { // 1시간 이내 세션 정보 있으면 바로 실행
                    isInstantLogin = true
                    startActivity(Intent(this, HomeActivity::class.java))
                }
            }

            webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do")
        }

        val timeoutRunnable = Runnable {
            if (!isLoginActivityStarted) {
                loadingText.text = "로딩이 오래 걸리고 있어요.\n여기를 눌러 현재 서버 상태를 확인해보세요."
                loadingText.setOnClickListener {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://status.klasplus.yuntae.in"))
                    startActivity(browserIntent)
                }
            }
        }
        loadingTimeoutRunnable = timeoutRunnable
        handler.postDelayed(timeoutRunnable, 7000)
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
        loadingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        loadingTimeoutRunnable = null
        errorDialog?.dismiss()
        errorDialog = null
    }
}