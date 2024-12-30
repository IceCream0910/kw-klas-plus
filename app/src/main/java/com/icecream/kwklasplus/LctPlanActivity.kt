package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.util.DeviceProperties.isTablet

class LctPlanActivity : AppCompatActivity() {
    lateinit var sessionIdForOtherClass: String
    lateinit var webView: WebView
    lateinit var subjID: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lct_plan)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        sessionIdForOtherClass = intent.getStringExtra("sessionId").toString()
        subjID = intent.getStringExtra("subjID").toString()

        webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(JavaScriptInterfaceLecturePlan(this), "Android")
        webView.loadUrl("https://klasplus.yuntae.in/lecturePlan")

        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }
        })
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}


class JavaScriptInterfaceLecturePlan(private val lctPlanActivity: LctPlanActivity) {
    @JavascriptInterface
    fun completePageLoad() {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.webView.evaluateJavascript(
                "javascript:window.receivedData('${lctPlanActivity.sessionIdForOtherClass}', '${lctPlanActivity.subjID}')",
                null
            )
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        lctPlanActivity.runOnUiThread {
            val intent = Intent(lctPlanActivity, LinkViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("sessionID", lctPlanActivity.sessionIdForOtherClass)
            lctPlanActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openExternalPage(url: String) {
        lctPlanActivity.runOnUiThread {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            lctPlanActivity.startActivity(intent)
        }
    }
}