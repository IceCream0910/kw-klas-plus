package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.ActivityInfo
import android.net.MailTo
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TaskViewActivity : AppCompatActivity() {
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1
    lateinit var webView: WebView
    lateinit var onBackPressedCallback: OnBackPressedCallback

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_view)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedCallback = object: OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                when {
                    webView.canGoBack() -> webView.goBack()
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val url = intent.getStringExtra("url")?.let {
            val sanitizedUrl = "https://klas.kw.ac.kr"+Uri.parse(it).toString()
            if (URLUtil.isValidUrl(sanitizedUrl)) {
                sanitizedUrl
            } else {
                null
            }
        } ?: run {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val yearHakgi = intent.getStringExtra("yearHakgi")
        val subj = intent.getStringExtra("subj")
        var sessionId = intent.getStringExtra("sessionID")

        val swipeLayout = findViewById<SwipeRefreshLayout>(R.id.swipeLayout)

        swipeLayout.setOnRefreshListener {
            webView.reload()
        }

        webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.loadUrl(url)

        var isOpenVideoAcitivity = false

        if(url!=null && url.contains("OnlineCntntsStdPage.do")) {
            isOpenVideoAcitivity = true
            val intent = Intent(this@TaskViewActivity, VideoPlayerActivity::class.java)
            intent.putExtra("sessionID", sessionId)
            intent.putExtra("subj", subj)
            intent.putExtra("yearHakgi", yearHakgi)
            finish()
            startActivity(intent)
        }

        var isScriptExecuted = false
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
            Snackbar.make(webView, "파일 다운로드\n$filename", Snackbar.LENGTH_LONG).show()

            val onComplete = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            startActivity(onComplete)
        })

        webView.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                onBackPressedCallback.isEnabled = webView.canGoBack()
            }

            @SuppressLint("SuspiciousIndentation")
            override fun onPageFinished(view: WebView, url: String) {
                webView.evaluateJavascript(
                    "javascript:(function() {" +
                            "var style = document.createElement('style');" +
                            "style.innerHTML = 'header { display: none; } .selectsemester { display: none; } .card { border-radius: 15px !important; }" +
                            ".container { margin-top: -10px } button { border-radius: 10px !important } .board_view_header { border: none !important; border-radius: 15px; } #appHeaderSubj { display: none; }';" +
                            "document.head.appendChild(style);" +
                            "window.scroll(0, 0);" +
                            "})()", null
                )
                swipeLayout.isRefreshing = false
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
                    if(!isOpenVideoAcitivity && url.contains("OnlineCntntsStdPage.do")) {
                        webView.evaluateJavascript(
                            "javascript:localStorage.setItem('selectYearhakgi', '$yearHakgi');" +
                                    "javascript:localStorage.setItem('selectSubj', '$subj');",
                            null
                        )
                        val intent = Intent(this@TaskViewActivity, VideoPlayerActivity::class.java)
                        intent.putExtra("sessionID", sessionId)
                        intent.putExtra("subj", subj)
                        intent.putExtra("yearHakgi", yearHakgi)
                        finish()
                        startActivity(intent)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(webView: WebView, webResourceRequest: WebResourceRequest): Boolean {
                Log.d("MyWebViewClient", "shouldOverrideUrlLoading")
                val uri = webResourceRequest.url.toString()
                if (uri != null) {
                    if (uri.startsWith("sms:") || uri.startsWith("tel:") || uri.startsWith(MailTo.MAILTO_SCHEME) || uri.startsWith("geo:")) {
                        if (uri.trim() == "sms:") {
                            val intent = Intent("android.intent.action.VIEW")
                            intent.type = "vnd.android-dir/mms-sms"
                            webView.context.startActivity(intent)
                            return true
                        }
                        val intent = Intent("android.intent.action.VIEW", Uri.parse(uri))
                        intent.addCategory("android.intent.category.BROWSABLE")
                        intent.putExtra("com.android.browser.application_id", webView.context.packageName)
                        webView.context.startActivity(intent)
                        return true
                    } else {
                        if (uri.startsWith("http:") || uri.startsWith("https:")) {
                            if (uri.startsWith("https://klas.kw.ac.kr/common/file/DownloadFile/") || uri.contains("CallAppLibrary") || uri.startsWith("https://klas.kw.ac.kr/std/") || uri.startsWith("https://klas.kw.ac.kr/spv/") || uri.contains("/usr/cmn/login/AutoLoginForm.do")) {
                                if (uri.startsWith("https://klas.kw.ac.kr/common/file/DownloadFile/")) {
                                    webView.context.startActivity(Intent("android.intent.action.VIEW", Uri.parse(uri)))
                                    return true
                                } else if (uri.endsWith(".hwp") || uri.endsWith(".pdf") || uri.endsWith(".zip") || uri.endsWith(".mp4") || uri.endsWith(".jpg") || uri.endsWith(".png") || uri.endsWith(".xls") || uri.endsWith(".ppt") || uri.endsWith(".ppt") || uri.endsWith(".gif") || uri.endsWith(".avi") || uri.endsWith(".mp3")) {
                                    webView.context.startActivity(Intent("android.intent.action.VIEW", Uri.parse(uri)))
                                    return true
                                } else if (uri.startsWith("https://klas.kw.ac.kr/std/") || uri.startsWith("https://klas.kw.ac.kr/spv/")) {
                                    webView.context.startActivity(Intent("android.intent.action.VIEW", Uri.parse(uri)))
                                    return true
                                } else if (uri.startsWith("about:blank#blocked")) {
                                    return true
                                } else {
                                    if (uri.contains("/usr/cmn/login/AutoLoginForm.do")) {
                                        webView.context.startActivity(Intent("android.intent.action.VIEW", Uri.parse(uri)))
                                        return true
                                    }
                                    webView.context.startActivity(Intent("android.intent.action.VIEW", Uri.parse(uri)))
                                    return true
                                }
                            }
                            return false
                        } else {
                            return false
                        }
                    }
                }
                return false
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
                    if(!isFinishing) {
                        val builder = MaterialAlertDialogBuilder(this@TaskViewActivity)
                        builder.setTitle("안내")
                            .setMessage(message)
                            .setPositiveButton("확인") { dialog, id ->
                                result?.confirm()
                            }
                            .setCancelable(false)
                            .show()
                    }
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

    override fun onBackPressed() {
        if(webView.canGoBack()){
            webView.goBack()

        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == uploadMessage) return
            val result = if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
            uploadMessage?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent))
            uploadMessage = null
        }
    }

}