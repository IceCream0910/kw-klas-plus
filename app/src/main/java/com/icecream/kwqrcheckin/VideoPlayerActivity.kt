package com.icecream.kwqrcheckin

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.MailTo
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jsoup.Jsoup

class VideoPlayerActivity : AppCompatActivity() {
    lateinit var lectureNameTextView: TextView
    lateinit var webView: WebView
    var isViewer = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        lectureNameTextView = findViewById<TextView>(R.id.lectureNameTextView)

        val titleTextView = findViewById<TextView>(R.id.titleTextView)

        val pipButton = findViewById<Button>(R.id.pipButton)
        val closeButton = findViewById<Button>(R.id.closeButton)

        pipButton.setOnClickListener {
            startPIP()
        }

        closeButton.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle("강의 종료")
                .setMessage("정말 강의 수강을 종료할까요?")
                .setPositiveButton("확인") { dialog, id ->
                    finish()
                }
                .setNegativeButton("취소") { dialog, id ->
                    dialog.dismiss()
                }
                .show()
        }

        webView = findViewById<WebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Whale/3.25.232.19 Safari/537.36"
        webView.loadUrl("https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do")


        val swipeLayout = findViewById<SwipeRefreshLayout>(R.id.swipeLayout)

        swipeLayout.setOnRefreshListener {
            //webView.reload()
            swipeLayout.isRefreshing = false
        }


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript(
                    "javascript:(function() {" +
                            "var style = document.createElement('style');" +
                            "style.innerHTML = 'header { display: none; } .selectsemester { display: none; } .card { border-radius: 15px !important; } .contsubtitle { display: none; } " +
                            ".container { margin-top: -10px } button { border-radius: 10px !important } .board_view_header { border: none !important; border-radius: 15px; }';" +
                            "document.head.appendChild(style);" +
                            "document.querySelector('#appModule').childNodes[2].style.display = 'none';" +
                            "window.scroll(0, 0);" +
                            "})()", null
                )

                swipeLayout.isRefreshing = false
                if (url.contains("viewer/")) {
                    isViewer = true
                    titleTextView.visibility = View.GONE
                    val params = swipeLayout.layoutParams
                    params.height = (resources.displayMetrics.heightPixels * 0.3).toInt() + 50
                    swipeLayout.layoutParams = params
                    webView.evaluateJavascript(
                        "javascript:(function() {" +
                                "var style = document.createElement('style');" +
                                "style.innerHTML = '.antopbak { display: none; }';" +
                                "document.head.appendChild(style);" +
                                "window.scroll(0, 0);" +
                                "})()", null
                    )
                    webView.evaluateJavascript(
                        "var progress = document.querySelector('.antopbak').children[0].innerHTML;"+
                                "var time = document.querySelector('.antopbak').children[1].innerHTML;"+
                                "Android.receiveVideoData(progress, time);"+
                        "setInterval(() => {"+
                                "var progress = document.querySelector('.antopbak').children[0].innerHTML;"+
                                "var time = document.querySelector('.antopbak').children[1].innerHTML;"+
                                "Android.receiveVideoData(progress, time);"+
                                "}, 10000);"
                        , null
                    )
                } else { isViewer = false; titleTextView.visibility = View.VISIBLE }
            }
        }


        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null
            private var originalOrientation: Int = 0

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }

                customView = view
                originalOrientation = requestedOrientation
                customViewCallback = callback

                (window.decorView as FrameLayout).addView(
                    customView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            override fun onHideCustomView() {
                (window.decorView as FrameLayout).removeView(customView)
                customView = null

                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                requestedOrientation = originalOrientation

                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@VideoPlayerActivity)
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

        }
    }


    private fun startPIP() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            var param = PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(param)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
        } else {
        }
    }

    override fun onBackPressed() {
        if(isViewer) {
            if (!isInPictureInPictureMode) {
                startPIP()
            } else {
                super.onBackPressed()
            }
        } else {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        startPIP()
    }

    override fun onPause() {
        super.onPause()
        startPIP()
    }
}

class WebAppInterface(videoPlayerActivity: VideoPlayerActivity) {
    private val videoPlayerActivity: VideoPlayerActivity = videoPlayerActivity

    @JavascriptInterface
    fun receiveVideoData(progress: String, time: String) {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.lectureNameTextView.text =
                "${progress.replace("<span id=\"lrnPer\">", "").replace("</span>", "").replace(">", "")}, " +
                        "${time.replace("<span id=\"lrnmin\">", "").replace("</span>", "").replace("<span>", "").replace(">", "")}"
        }
    }
}