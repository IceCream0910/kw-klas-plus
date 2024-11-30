package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_VISIBLE
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets


class LectureActivity : AppCompatActivity() {
    lateinit var boardNoticePath: String
    lateinit var boardPdsPath: String
    lateinit var webView: WebView
    lateinit var uiWebView: WebView
    lateinit var scrollView: SwipeRefreshLayout
    lateinit var LctName: TextView
    private lateinit var bodyJSON: JSONObject
    lateinit var subjID: String
    lateinit var subjName: String
    lateinit var sessionId: String
    lateinit var yearHakgi: String
    lateinit var loadingDialog: AlertDialog
    var isShowingKLAS : Boolean = false

    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecture)

        window.statusBarColor = ContextCompat.getColor(this, R.color.md_theme_background)

        // 모바일에서는 세로 모드 고정
        if (isTablet(this)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        subjID = intent.getStringExtra("subjID").toString()
        subjName = intent.getStringExtra("subjName").toString()
        sessionId = intent.getStringExtra("sessionID")!!
        yearHakgi = intent.getStringExtra("yearHakgi")!!


        uiWebView = findViewById<WebView>(R.id.uiWebView)
        uiWebView.settings.javaScriptEnabled = true
        uiWebView.settings.domStorageEnabled = true
        uiWebView.settings.allowFileAccess = true
        uiWebView.settings.allowContentAccess = true
        uiWebView.settings.supportMultipleWindows()
        uiWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        uiWebView.overScrollMode = WebView.OVER_SCROLL_NEVER
        uiWebView.isVerticalScrollBarEnabled = false
        uiWebView.isHorizontalScrollBarEnabled = false
        uiWebView.addJavascriptInterface(WebAppInterfaceLectureHome(this), "Android")
        uiWebView.loadUrl("https://klasplus.yuntae.in/lectureHome")

        webView = findViewById<WebView>(R.id.webView)
        scrollView = findViewById<SwipeRefreshLayout>(R.id.scrollView)
        val progressBar = findViewById<LinearLayout>(R.id.progressBar)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportMultipleWindows()
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(WebAppInterfaceLectureHome(this), "Android")
        webView.loadUrl("https://klas.kw.ac.kr/std/cmn/frame/Frame.do")

        scrollView.visibility = ScrollView.GONE
        progressBar.visibility = ProgressBar.VISIBLE

        scrollView.setOnRefreshListener {
            webView.evaluateJavascript("javascript:pageReload()", null)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()

                Log.e("taein", url)
                if (url.contains("LctrumHomeStdPage.do")) {
                    webView.visibility = View.GONE
                    scrollView.visibility = View.VISIBLE
                    isShowingKLAS = false
                }

                if(!url.contains("klas.kw.ac.kr")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("OnlineCntntsStdPage.do")) {
                    webView.evaluateJavascript(
                        "javascript:localStorage.setItem('selectYearhakgi', '$yearHakgi');" +
                                "javascript:localStorage.setItem('selectSubj', '$subjID');",
                        null
                    )
                    val intent = Intent(this@LectureActivity, VideoPlayerActivity::class.java)
                    intent.putExtra("sessionID", sessionId)
                    intent.putExtra("subj", subjID)
                    intent.putExtra("yearHakgi", yearHakgi)
                    webView.loadUrl("https://klas.kw.ac.kr/std/lis/evltn/LctrumHomeStdPage.do")
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
                    scrollView.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    progressBar.visibility = ProgressBar.GONE
                }
                if(url.contains("LctrumHomeStdPage.do")) {
                    webView.evaluateJavascript("""
                        const noticePath = $("a:contains('강의 공지사항')").attr("onclick").split("linkUrl('/std/lis/sport/")[1].split("/")[0];
                        const pdsPath = $("a:contains('강의 자료실')").attr("onclick").split("linkUrl('/std/lis/sport/")[1].split("/")[0];
                        Android.getBoardPath(noticePath, pdsPath);
                    """.trimIndent(),
                        null
                    )
                    webView.clearHistory()
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


    fun openQRScan() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(R.layout.layout_loading_dialog)
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog.show()


        if(subjName.isNullOrEmpty() || subjID.isNullOrEmpty()) {
            runOnUiThread {
                Toast.makeText(
                    this@LectureActivity,
                    "QR출석을 위한 정보를 불러오지 못했어요. 다시 시도해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                loadingDialog.dismiss()
            }
            return
        }

        fetchSubjectDetail(sessionId, subjName, subjID) { subjDetail2 ->
            postTransformedData(sessionId, subjDetail2) { subjDetail3 ->
                postRandomKey(sessionId, subjDetail3) { transformedJson ->
                    val intent = Intent(this@LectureActivity, QRScanActivity::class.java)
                    intent.putExtra("bodyJSON", transformedJson.toString())
                    intent.putExtra("subjID", subjID)
                    intent.putExtra("subjName", subjName)
                    intent.putExtra("sessionID", sessionId)
                    startActivity(intent)
                }
            }
        }
    }

    fun fetchSubjectDetail(
        sessionId: String,
        subjName: String,
        subjID: String,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            val json = JSONObject()
                .put("list", JSONArray())
                .put("selectYear", yearHakgi.split(",")[0])
                .put("selectHakgi", yearHakgi.split(",")[1])
                .put("openMajorCode", "")
                .put("openGrade", "")
                .put("openGwamokNo", "")
                .put("bunbanNo", "")
                .put("gwamokKname", "")
                .put("codeName1", "")
                .put("hakjumNum", "")
                .put("sisuNum", "")
                .put("memberName", "")
                .put("currentNum", "")
                .put("yoil", "")

            val requestBody =
                RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())

            val request = buildRequest(
                "https://klas.kw.ac.kr/std/ads/admst/KwAttendStdGwakmokList.do",
                sessionId,
                requestBody
            )

            var found = false
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        if (jsonObject.getString("gwamokKname") == subjName) {
                            val transformedJson = JSONObject()
                                .put("list", JSONArray())
                                .put("selectYear", jsonObject.getString("thisYear"))
                                .put("selectHakgi", jsonObject.getString("hakgi"))
                                .put("openMajorCode", jsonObject.getString("openMajorCode"))
                                .put("openGrade", jsonObject.getString("openGrade"))
                                .put("openGwamokNo", jsonObject.getString("openGwamokNo"))
                                .put("bunbanNo", jsonObject.getString("bunbanNo"))
                                .put("gwamokKname", jsonObject.getString("gwamokKname"))
                                .put("codeName1", jsonObject.getString("codeName1"))
                                .put("hakjumNum", jsonObject.getString("hakjumNum"))
                                .put("sisuNum", jsonObject.getString("sisuNum"))
                                .put("memberName", jsonObject.getString("memberName"))
                                .put("currentNum", jsonObject.getString("currentNum"))
                                .put("yoil", jsonObject.getString("yoil"))
                                .put("subj", subjID)
                            callback(transformedJson)
                            found = true
                            break
                        }
                    }
                }
                if (!found) {
                    runOnUiThread {
                        Toast.makeText(
                            this@LectureActivity,
                            "QR출석이 지원되지 않는 강의입니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadingDialog.dismiss()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
            }
        }
        return JSONObject()
    }

    fun postTransformedData(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            try {
                val requestBody = RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    transformedJson.toString()
                )

                val request = buildRequest(
                    "https://klas.kw.ac.kr/mst/ads/admst/KwAttendStdAttendList.do",
                    sessionId,
                    requestBody
                )

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val responseJson = JSONArray(responseBody)
                    transformedJson.put("list", responseJson)
                    callback(transformedJson)
                } else {
                    callback(JSONObject())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
                callback(JSONObject())
            }
        }
        return JSONObject()
    }

    fun postRandomKey(
        sessionId: String,
        transformedJson: JSONObject,
        callback: (JSONObject) -> Unit
    ): JSONObject {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            try {
                val requestBody = RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    transformedJson.toString()
                )

                val request = buildRequest(
                    "https://klas.kw.ac.kr/std/lis/evltn/CertiPushSucStd.do",
                    sessionId,
                    requestBody
                )

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val responseJson = JSONObject(responseBody).getString("randomKey")
                    transformedJson.put("randomKey", responseJson)
                    callback(transformedJson)
                } else {
                    callback(JSONObject())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSessionExpiredDialog()
                    loadingDialog.dismiss()
                }
                callback(JSONObject())
            }
        }
        return JSONObject()
    }

    private fun buildRequest(
        url: String,
        sessionId: String,
        requestBody: RequestBody? = null
    ): Request {
        val defaultUserAgent = WebSettings.getDefaultUserAgent(this)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Cookie", "SESSION=$sessionId")
            .header(
                "User-Agent",
                "$defaultUserAgent NuriwareApp"
            )

        if (requestBody != null) {
            requestBuilder.post(requestBody)
        }

        return requestBuilder.build()
    }

    private fun showSessionExpiredDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("인증 오류")
            .setMessage("로그인 후 일정 시간이 지나 세션이 만료되었어요. 앱을 재시작하면 정상적으로 정보가 표시될 거예요.")
            .setPositiveButton(
                "확인"
            ) { _, _ ->
                finish()
                startActivity(Intent(this@LectureActivity, MainActivity::class.java))
            }
        builder.show()
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
            webView.loadUrl("https://klas.kw.ac.kr/std/cmn/frame/Frame.do")
        }
    }

    override fun onBackPressed() {
        if(isShowingKLAS) {
            if(webView.canGoBack()) {
                webView.goBack()
            } else {
                webView.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                isShowingKLAS = false
                webView.loadUrl("https://klas.kw.ac.kr/std/lis/evltn/LctrumHomeStdPage.do")
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(::loadingDialog.isInitialized) {
            loadingDialog.dismiss()
        }
    }

    override fun onPause() {
        super.onPause()
        if(::loadingDialog.isInitialized) {
            loadingDialog.dismiss()
        }
    }


}

class WebAppInterfaceLectureHome(private val lectureActivity: LectureActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun completePageLoad() {
        lectureActivity.runOnUiThread {
            lectureActivity.uiWebView.evaluateJavascript(
                "javascript:window.receivedData('${lectureActivity.sessionId}', '${lectureActivity.subjID}', '${lectureActivity.yearHakgi}')",
                null
            )
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        lectureActivity.runOnUiThread {
            val intent = Intent(lectureActivity, LinkViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("sessionID", lectureActivity.sessionId)
            lectureActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun getBoardPath(noticePath: String, pdsPath: String) {
        lectureActivity.runOnUiThread {
            lectureActivity.boardNoticePath = noticePath
            lectureActivity.boardPdsPath = pdsPath
        }
    }

    @JavascriptInterface
    fun openBoardList(type: String, title: String) {
        lectureActivity.runOnUiThread {
            val intent = Intent(lectureActivity, BoardActivity::class.java)
            if(type == "notice") {
                intent.putExtra("path", lectureActivity.boardNoticePath)
            } else if(type == "pds") {
                intent.putExtra("path", lectureActivity.boardPdsPath)
            }
            intent.putExtra("title", title)
            intent.putExtra("type", "list")
            intent.putExtra("sessionID", lectureActivity.sessionId)
            intent.putExtra("subjID", lectureActivity.subjID)
            intent.putExtra("yearHakgi", lectureActivity.yearHakgi)
            lectureActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openBoardView(type: String, boardNo: String, masterNo: String) {
        lectureActivity.runOnUiThread {
            val intent = Intent(lectureActivity, BoardActivity::class.java)
            if(type == "notice") {
                intent.putExtra("path", lectureActivity.boardNoticePath)
            } else if(type == "pds") {
                intent.putExtra("path", lectureActivity.boardPdsPath)
            }
            intent.putExtra("title", "")
            intent.putExtra("type", "view")
            intent.putExtra("boardNo", boardNo)
            intent.putExtra("masterNo", masterNo)
            intent.putExtra("sessionID", lectureActivity.sessionId)
            intent.putExtra("subjID", lectureActivity.subjID)
            intent.putExtra("yearHakgi", lectureActivity.yearHakgi)
            lectureActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openExternalLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        lectureActivity.startActivity(intent)
    }

    @JavascriptInterface
    fun evaluteKLASScript(script: String) {
        lectureActivity.runOnUiThread {
            lectureActivity.webView.evaluateJavascript(script, null)
            lectureActivity.scrollView.visibility = View.GONE
            lectureActivity.webView.visibility = View.VISIBLE
            lectureActivity.isShowingKLAS = true
        }
    }

    @JavascriptInterface
    fun openOnlineLecture() {
        lectureActivity.runOnUiThread {
            val intent = Intent(lectureActivity, VideoPlayerActivity::class.java)
            intent.putExtra("sessionID", lectureActivity.sessionId)
            intent.putExtra("subj", lectureActivity.subjID)
            intent.putExtra("yearHakgi", lectureActivity.yearHakgi)
            lectureActivity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openLecturePlan() {
        val intent = Intent(lectureActivity, LctPlanActivity::class.java)
        intent.putExtra("sessionId", lectureActivity.sessionId)
        intent.putExtra("subjID", lectureActivity.subjID)
        lectureActivity.startActivity(intent)
    }

    @JavascriptInterface
    fun openQRScan() {
        lectureActivity.runOnUiThread { lectureActivity.openQRScan() }
    }
}