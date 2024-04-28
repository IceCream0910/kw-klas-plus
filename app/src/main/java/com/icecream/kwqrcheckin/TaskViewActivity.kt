package com.icecream.kwqrcheckin

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
import android.net.MailTo
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import com.google.android.material.snackbar.Snackbar
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TaskViewActivity : AppCompatActivity() {
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_view)

        window.statusBarColor = Color.parseColor("#3A051F")

        val url = intent.getStringExtra("url")
        val yearHakgi = intent.getStringExtra("yearHakgi")
        val subj = intent.getStringExtra("subj")

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Whale/3.25.232.19 Safari/537.36"
        webView.loadUrl("https://klas.kw.ac.kr$url")

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
            Snackbar.make(webView, "파일 다운로드 시작\n$filename", Snackbar.LENGTH_LONG).show()
        })

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
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
            // Enable file upload
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
                startActivityForResult(Intent.createChooser(intent, "File Chooser"), FILECHOOSER_RESULTCODE)
                return true
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
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