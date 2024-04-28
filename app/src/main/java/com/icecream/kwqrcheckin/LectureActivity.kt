package com.icecream.kwqrcheckin

import android.Manifest
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
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
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

    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecture)

        val subjID = intent.getStringExtra("subjID")
        val subjName = intent.getStringExtra("subjName")
        bodyJSON = JSONObject(intent.getStringExtra("bodyJSON")!!)
        sessionId = intent.getStringExtra("sessionID")!!


        LctName = findViewById<TextView>(R.id.LctName)
        LctName.text = subjName

        val LctPlanBtn = findViewById<TextView>(R.id.LctPlanBtn)
        LctPlanBtn.setOnClickListener {
            val intent = Intent(this, LctPlanActivity::class.java)
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
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val progressBar = findViewById<LinearLayout>(R.id.progressBar)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Whale/3.25.232.19 Safari/537.36"
        webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/frame/Frame.do")

        scrollView.visibility = ScrollView.GONE
        progressBar.visibility = ProgressBar.VISIBLE

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if(url.contains("Frame.do")) {
                    webView.evaluateJavascript(
                        "javascript:appModule.goLctrum('${getCurrentYear()},${getCurrentSemester()}', '$subjID')",
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
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                runOnUiThread {
                    val builder = MaterialAlertDialogBuilder(this@LectureActivity)
                    builder.setTitle("알림")
                        .setMessage(message)
                        .setPositiveButton("확인") { dialog, id ->
                            result?.confirm()
                        }
                        .setCancelable(false)
                        .show()
                }
                return true
            }

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

    fun getCurrentYear(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return currentYear.toString()
    }

    fun getCurrentSemester(): String {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)

        return if (currentMonth < 7) "1" else "2" // 8월 기준
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == uploadMessage) return
            val result = if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
            uploadMessage?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent))
            uploadMessage = null
        } else {
            webView.loadUrl("https://klas.kw.ac.kr/mst/cmn/frame/Frame.do")
        }
    }

    override fun onBackPressed() {
        if(webView.canGoBack()){
            webView.goBack()

        } else {
            super.onBackPressed()
        }
    }

}