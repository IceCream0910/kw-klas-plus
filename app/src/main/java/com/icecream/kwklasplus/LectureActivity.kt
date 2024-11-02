package com.icecream.kwklasplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Log
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_VISIBLE
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Calendar

class LectureActivity : AppCompatActivity() {
    lateinit var webView: WebView
    lateinit var LctName: TextView
    private lateinit var bodyJSON: JSONObject
    private lateinit var sessionId: String
    private lateinit var yearHakgi: String

    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecture)

        window.statusBarColor = Color.parseColor("#3A051F")

        val subjID = intent.getStringExtra("subjID")
        val subjName = intent.getStringExtra("subjName")
        bodyJSON = JSONObject(intent.getStringExtra("bodyJSON")!!)
        sessionId = intent.getStringExtra("sessionID")!!
        yearHakgi = intent.getStringExtra("yearHakgi")!!

        LctName = findViewById<TextView>(R.id.LctName)
        LctName.text = subjName

        val LctPlanBtn = findViewById<TextView>(R.id.LctPlanBtn)
        LctPlanBtn.setOnClickListener {
            val intent = Intent(this, LctPlanActivity::class.java)
            intent.putExtra("sessionId", sessionId)
            intent.putExtra("subjID", subjID)
            startActivity(intent)
        }

        val QRBtn = findViewById<TextView>(R.id.QRBtn)
        QRBtn.setOnClickListener {
            val intent = Intent(this, QRScanActivity::class.java)
            intent.putExtra("bodyJSON", bodyJSON.toString())
            intent.putExtra("sessionID", sessionId)
            startActivity(intent)
        }

        webView = findViewById<WebView>(R.id.webView)
        val scrollView = findViewById<SwipeRefreshLayout>(R.id.scrollView)
        val progressBar = findViewById<LinearLayout>(R.id.progressBar)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/frame/Frame.do")

        scrollView.visibility = ScrollView.GONE
        progressBar.visibility = ProgressBar.VISIBLE

        scrollView.setOnRefreshListener {
            webView.evaluateJavascript("javascript:pageReload()", null)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("OnlineCntntsMstPage.do")) {
                    webView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectYearhakgi', '$yearHakgi');" +
                                "javascript:localStorage.setItem('selectSubj', '$subjID');",
                        null
                    )
                    val intent = Intent(this@LectureActivity, VideoPlayerActivity::class.java)
                    intent.putExtra("sessionID", sessionId)
                    intent.putExtra("subj", subjID)
                    intent.putExtra("yearHakgi", yearHakgi)
                    webView.goBack()
                    startActivity(intent)
                }
                scrollView.isRefreshing = false
                webView.evaluateJavascript(
                    "javascript:(function() {" +
                            "var style = document.createElement('style');" +
                            "style.innerHTML = 'header { display: none; } .selectsemester { display: none; } .card { border-radius: 15px !important; }" +
                            ".container { margin-top: -10px } button { border-radius: 10px !important } .board_view_header { border: none !important; border-radius: 15px; } #appHeaderSubj { display: none; }';" +
                            "document.head.appendChild(style);" +
                            "window.scroll(0, 0);" +
                            "})()", null
                )
                if (url.contains("Frame.do")) {
                    webView.evaluateJavascript(
                        "javascript:appModule.goLctrum('$yearHakgi', '$subjID')",
                        null
                    )
                    scrollView.visibility = ScrollView.VISIBLE
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url))
            var filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            filename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name()) // 파일 이름 디코딩
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("파일 다운로드 중...")
            request.setTitle(filename)
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            val dManager = this.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dManager.enqueue(request)
            Snackbar.make(webView, "파일 다운로드 시작\n$filename", Snackbar.LENGTH_LONG).show()
        })

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

                window.decorView.systemUiVisibility = SYSTEM_UI_FLAG_FULLSCREEN
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            override fun onHideCustomView() {
                (window.decorView as FrameLayout).removeView(customView)
                customView = null

                window.decorView.systemUiVisibility = SYSTEM_UI_FLAG_VISIBLE
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
                    val builder = MaterialAlertDialogBuilder(this@LectureActivity)
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

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "파일 선택"), FILECHOOSER_RESULTCODE)
                return true
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (uploadMessage == null) return

            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            uploadMessage = null
        } else {
            webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/frame/Frame.do")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()

        } else {
            super.onBackPressed()
        }
    }

}