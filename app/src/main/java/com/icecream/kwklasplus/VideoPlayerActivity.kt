package com.icecream.kwklasplus

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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.jsoup.Jsoup
import kotlin.properties.Delegates

class VideoPlayerActivity : AppCompatActivity() {
    lateinit var lectureNameTextView: TextView
    lateinit var lectureTimeTextView: TextView
    lateinit var timeProgressBar: ProgressBar
    lateinit var webView: WebView
    lateinit var titleLayout: LinearLayout
    var isViewer = false
    var onStopCalled by Delegates.notNull<Boolean>()
    var originVideoURL: String = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        window.statusBarColor = Color.parseColor("#3A051F")

        lectureNameTextView = findViewById(R.id.lectureNameTextView)
        lectureTimeTextView = findViewById(R.id.lectureTimeTextView)
        timeProgressBar = findViewById(R.id.timeProgressBar)
        timeProgressBar.isIndeterminate = false

        val titleTextView = findViewById<TextView>(R.id.titleTextView)

        titleLayout = findViewById<LinearLayout>(R.id.titleLayout)

        val pipButton = findViewById<Button>(R.id.pipButton)
        val closeButton = findViewById<Button>(R.id.closeButton)
        val openInBrowserButton = findViewById<Button>(R.id.openInBrowserButton)

        openInBrowserButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(originVideoURL.ifEmpty { webView.url ?: "" }))
            startActivity(intent)
        }

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

        var subj = intent.getStringExtra("subj")
        var yearHakgi = intent.getStringExtra("yearHakgi")

        webView = findViewById<BackgroundWebView>(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.setBackgroundColor(0)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
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
                    titleLayout.visibility = View.GONE
                    val params = swipeLayout.layoutParams
                    params.height = (resources.displayMetrics.heightPixels * 0.3).toInt() + 50
                    swipeLayout.layoutParams = params
                    webView.evaluateJavascript(
                        "javascript:(function() {" +
                                "var style = document.createElement('style');" +
                                "style.innerHTML = '.antopbak { display: none; } #appHeaderSubj { display: none; }';" +
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

                    webView.evaluateJavascript(
                        "const videoURL = chkOpen.toString().split('https://kwcommons.kw.ac.kr/em/')[1].split('\"')[0];"+
                                "Android.receiveVideoURL(videoURL);"
                        , null
                    )
                } else {
                    isViewer = false;
                    titleLayout.visibility = View.VISIBLE
                }
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

    override fun onStop() {
        super.onStop()
        onStopCalled = true
    }

    override fun onResume() {
        super.onResume()
        onStopCalled = false
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            titleLayout.visibility = View.GONE
        } else {
            if (onStopCalled) {
                finish()
            } else {
                titleLayout.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
    super.onDestroy()

    // WebView 종료
    webView?.let {
        it.stopLoading()
        it.clearHistory()
        it.clearCache(true)
        it.loadUrl("about:blank")
        it.onPause()
        it.removeAllViews()
        it.destroyDrawingCache()
        it.destroy()
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
        if(isViewer) {
            startPIP()
        }
    }

    override fun onPause() {
        super.onPause()
        if(isViewer) {
            startPIP()
        }
    }
}

class WebAppInterface(private val videoPlayerActivity: VideoPlayerActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun receiveVideoData(progress: String, time: String) {
        var progressText = "${progress.replace("<span id=\"lrnPer\">", "").replace("</span>", "").replace(">", "")}";
        var timeText = "${time.replace("<span id=\"lrnmin\">", "").replace("</span>", "").replace("<span>", "").replace(">", "")}"

        var progressNum: Float = progressText.replace("진행률 ", "").replace("%", "").trim().toFloat()

        mainHandler.post {
            videoPlayerActivity.lectureTimeTextView.text =
                "$progressText, $timeText"
            videoPlayerActivity.timeProgressBar.progress = progressNum.toInt()
        }
    }

    @JavascriptInterface
    fun receiveVideoURL(videoURL: String) {
        mainHandler.post {
            val url = "https://kwcommons.kw.ac.kr/em/$videoURL"
            videoPlayerActivity.originVideoURL = url

            Thread {
                try {
                    val doc = Jsoup.connect(url).get()
                    val title = doc.title()
                    mainHandler.post {
                        videoPlayerActivity.lectureNameTextView.text = title
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }
}