package com.icecream.kwklasplus

import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        val sharedPreferences = getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
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
                    //Toast.makeText(this@MainActivity, "세션 로그인 완료", Toast.LENGTH_SHORT).show()
                    val cookies = CookieManager.getInstance().getCookie(url)
                    val session =
                        cookies.split("; ").find { it.startsWith("SESSION=") }?.split("=")
                            ?.get(1)
                    if (session != null) {
                        with(sharedPreferences.edit()) {
                            putString("kwSESSION", session)
                            putString("kwSESSION_timestamp", System.currentTimeMillis().toString())
                            apply()
                        }
                        val serviceIntent = Intent(this@MainActivity, UpdateSession::class.java)
                        serviceIntent.putExtra("session", session)
                        startService(serviceIntent)
                        if(!isInstantLogin) {
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
                    val builder = MaterialAlertDialogBuilder(this@MainActivity)
                    builder.setTitle("오류")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                            finish()
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        }
                        .setCancelable(false)
                        .show()
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
                val session = instantSession
                val timestamp = instantSessionTimestamp.toLong()
                if (System.currentTimeMillis() - timestamp < 1000 * 60 * 60) { // 1시간 이내 세션 정보 있으면 바로 실행
                    isInstantLogin = true
                    /* TODO: 세션 자동 갱신 시 SocketTimeoutException으로 인한 비정상 종료 발생
                    val serviceIntent = Intent(this, UpdateSession::class.java)
                    serviceIntent.putExtra("session", session)
                    startService(serviceIntent)
                    */
                    startActivity(Intent(this, HomeActivity::class.java))
                }
            }

            webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do")
        }
    }
}