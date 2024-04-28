package com.icecream.kwqrcheckin

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import androidx.core.view.WindowCompat

class LctPlanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lct_plan)
        window.statusBarColor = Color.parseColor("#3A051F")

        val subjID = intent.getStringExtra("subjID")

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://klas.kw.ac.kr/std/cps/atnlc/popup/LectrePlanStdView.do?selectSubj=$subjID")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}