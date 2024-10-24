package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.properties.Delegates

class VideoPlayerActivity : AppCompatActivity() {
    lateinit var lectureNameTextView: TextView
    lateinit var lectureTimeTextView: TextView
    lateinit var timeProgressBar: ProgressBar
    lateinit var webView: WebView
    lateinit var listWebView: WebView
    lateinit var listLayout: SwipeRefreshLayout
    lateinit var videoPlayerLayout: LinearLayout
    lateinit var subj: String
    lateinit var yearHakgi: String
    lateinit var sessionId: String
    var isViewer = false
    var onStopCalled by Delegates.notNull<Boolean>()
    var originVideoURL: String = ""
    var isLoadedKLASWebView = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        window.statusBarColor = Color.parseColor("#3A051F")

        lectureNameTextView = findViewById(R.id.lectureNameTextView)
        lectureTimeTextView = findViewById(R.id.lectureTimeTextView)
        timeProgressBar = findViewById(R.id.timeProgressBar)
        timeProgressBar.isIndeterminate = false

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

        subj = intent.getStringExtra("subj").toString()
        yearHakgi = intent.getStringExtra("yearHakgi").toString()
        sessionId = intent.getStringExtra("sessionID").toString()

        webView = findViewById<BackgroundWebView>(R.id.webView)
        listWebView = findViewById<BackgroundWebView>(R.id.listWebView)

        listWebView.settings.javaScriptEnabled = true
        listWebView.settings.domStorageEnabled = true
        listWebView.settings.allowFileAccess = true
        listWebView.settings.allowContentAccess = true
        listWebView.settings.supportMultipleWindows()
        listWebView.setBackgroundColor(0)
        listWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        listWebView.addJavascriptInterface(WebAppInterface(this), "Android")
        listWebView.loadUrl("https://klasplus.yuntae.in/onlineLecture")

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.setBackgroundColor(0)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.loadUrl("https://klas.kw.ac.kr/std/lis/evltn/OnlineCntntsStdPage.do")


        listLayout = findViewById(R.id.listLayout)
        videoPlayerLayout = findViewById(R.id.videoPlayerLayout)

        listLayout.setOnRefreshListener {
            listWebView.reload()
            listLayout.isRefreshing = false
        }

        var isScriptExecuted = false

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

                listLayout.isRefreshing = false

                if (!isScriptExecuted) {
                    webView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectYearhakgi', '$yearHakgi');",
                        null
                    )
                    webView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectSubj', '$subj');",
                        null
                    )
                    webView.reload()
                    isScriptExecuted = true
                } else {
                    isLoadedKLASWebView = true
                }

                if (url.contains("viewer/")) {
                    isViewer = true
                    val params = videoPlayerLayout.layoutParams
                    params.height = (resources.displayMetrics.heightPixels * 0.3).toInt() + 50
                    videoPlayerLayout.layoutParams = params
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
                }
            }
        }

        listWebView.webChromeClient = object : WebChromeClient() {
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
        if (!isInPictureInPictureMode) {
            if (onStopCalled) {
                finish()
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
    fun completePageLoad() {
        videoPlayerActivity.runOnUiThread {
            videoPlayerActivity.listWebView.evaluateJavascript(
                "javascript:window.receivedData('${videoPlayerActivity.sessionId}', '${videoPlayerActivity.subj}', '${videoPlayerActivity.yearHakgi}')",
                null
            )
        }
    }

    @JavascriptInterface
    fun openExternalLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        videoPlayerActivity.startActivity(intent)
    }

    @JavascriptInterface
    fun openInKLAS() {
        mainHandler.post {
            videoPlayerActivity.listLayout.visibility = View.GONE
            videoPlayerActivity.videoPlayerLayout.visibility = View.VISIBLE
        }
    }

    @JavascriptInterface
    fun requestOnlineLecture(jsonData: String) {
        mainHandler.post {
            try {
                val data = JSONObject(jsonData)
                val grcode = data.optString("grcode")
                val subj = data.optString("subj")
                val year = data.optString("year")
                val hakgi = data.optString("hakgi")
                val bunban = data.optString("bunban")
                val module = data.optString("module")
                val lesson = data.optString("lesson")
                val oid = data.optString("oid")
                val starting = data.optString("starting")
                val contentsType = data.optString("contentsType")
                val weekNo = data.optInt("weekNo")
                val weeklyseq = data.optInt("weeklyseq")
                val width = data.optInt("width")
                val height = data.optInt("height")
                val today = data.optString("today")
                val sdate = data.optString("sdate")
                val edate = data.optString("edate")
                val ptype = data.optString("ptype")
                val learnTime = data.optString("learnTime")
                val prog = data.optInt("prog")
                val ptime = data.optString("ptime")
                var jsCode: String? = null

                if(!videoPlayerActivity.isLoadedKLASWebView) {
                    Toast.makeText(videoPlayerActivity, "아직 강의 정보를 불러오는 중이에요. 몇 초 후에 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    return@post
                }

                if(prog == 100) {
                    jsCode = """
                javascript:appModule.goViewCntnts(
                    '$grcode', '$subj', '$year', '$hakgi', '$bunban', '$module', '$lesson', '$oid', '$starting',
                    '$contentsType', $weekNo, $weeklyseq, $width, $height, '$today', '$sdate', '$edate',
                    '$ptype', '$learnTime', $prog, '$ptime'
                )
                """.trimIndent()
                } else {
                    jsCode = """
                javascript:lrnCerti.checkCerti(
                    '$grcode', '$subj', '$year', '$hakgi', '$bunban', '$module', '$lesson', '$oid', '$starting',
                    '$contentsType', $weekNo, $weeklyseq, $width, $height, '$today', '$sdate', '$edate',
                    '$ptype', '$learnTime', $prog, 'C', '$ptime'
                )
                """.trimIndent()
                }

                videoPlayerActivity.webView.evaluateJavascript(jsCode, null)
                videoPlayerActivity.listLayout.visibility = View.GONE
                videoPlayerActivity.videoPlayerLayout.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
                videoPlayerActivity.runOnUiThread {
                    MaterialAlertDialogBuilder(videoPlayerActivity)
                        .setTitle("안내")
                        .setMessage("강의를 불러오는 중 오류가 발생했습니다.")
                        .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        }
    }


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