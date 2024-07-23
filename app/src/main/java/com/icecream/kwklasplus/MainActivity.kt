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

        if (kwID == null || kwPWD == null) {
            finish()
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
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
                        val session =
                            cookies.split("; ").find { it.startsWith("SESSION=") }?.split("=")
                                ?.get(1)
                        if (session != null) {
                            with(sharedPreferences.edit()) {
                                putString("kwSESSION", session)
                                apply()
                            }
                            val serviceIntent = Intent(this@MainActivity, UpdateSession::class.java)
                            serviceIntent.putExtra("session", session)
                            startService(serviceIntent)

                            finish()
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    HomeActivity::class.java
                                )
                            )
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
            webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/login/LoginForm.do")
        }
    }
}