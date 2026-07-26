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
import com.google.android.material.loadingindicator.LoadingIndicator
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.kwklasplus.core.attendance.QrAttendancePayloadCodec
import com.icecream.kwklasplus.core.attendance.QrPreparationRequest
import com.icecream.kwklasplus.core.attendance.QrPreparationResult
import com.icecream.kwklasplus.core.attendance.QrScanLaunchGuard
import com.icecream.kwklasplus.core.academic.AcademicTermKey
import com.icecream.kwklasplus.core.network.KlasUserAgent
import com.icecream.kwklasplus.core.security.SecretValue
import com.icecream.kwklasplus.manager.AppDownloadManager
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.web.KlasWebAutomationScripts
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.bridge.legacy.LectureLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.platform.file.AndroidFilePicker
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.navigation.openBoardListRoute
import com.icecream.kwklasplus.platform.navigation.openBoardViewRoute
import com.icecream.kwklasplus.platform.navigation.openLecturePlanRoute
import com.icecream.kwklasplus.platform.navigation.openVideoRoute
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.dialog.ComposeLoadingDialog
import com.icecream.kwklasplus.ui.web.ComposePlatformViewHost
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess


class LectureActivity : AppCompatActivity() {
    private val qrScanLaunchGuard = QrScanLaunchGuard()
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        qrScanLaunchGuard.release()
    }
    var boardNoticePath: String = ""
    var boardPdsPath: String = ""
    lateinit var webView: WebView
    lateinit var uiWebView: WebView
    lateinit var scrollView: SwipeRefreshLayout
    lateinit var LctName: TextView
    lateinit var subjID: String
    lateinit var subjName: String
    lateinit var sessionId: String
    lateinit var yearHakgi: String
    lateinit var loadingDialog: ComposeLoadingDialog
    var isShowingKLAS : Boolean = false
    private var isPageLoading by mutableStateOf(true)

    private val filePicker = AndroidFilePicker(this)
    private val bridgeMessageAdapters = mutableListOf<AndroidBridgeMessageAdapter>()
    private val webSurfaces = mutableListOf<AndroidWebSurface>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()

        subjID = intent.getStringExtra("subjID").toString()
        subjName = intent.getStringExtra("subjName").toString()
        sessionId = intent.getStringExtra(IntentExtras.SESSION_ID)!!
        yearHakgi = intent.getStringExtra(IntentExtras.YEAR_HAKGI)!!


        val legacyFacade = WebAppInterfaceLectureHome(this)
        uiWebView = WebView(this)
        val uiSurface = AndroidWebSurface(uiWebView).also(webSurfaces::add)
        uiWebView.configureAppWebView(
            javaScriptInterface = legacyFacade,
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true
        )
        bridgeMessageAdapters += AndroidBridgeMessageAdapter(
            uiWebView,
            BridgeSurface.LECTURE,
            lifecycleScope,
            LectureLegacyBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        uiWebView.overScrollMode = WebView.OVER_SCROLL_NEVER
        uiWebView.webViewClient = AndroidWebSurfaceClient(uiSurface)
        uiWebView.loadUrl(AppUrls.LECTURE_HOME)
        appDependencies.fileTransfer(this).attachTo(uiWebView)

        webView = WebView(this)
        val klasSurface = AndroidWebSurface(webView).also(webSurfaces::add)
        scrollView = SwipeRefreshLayout(this).apply {
            addView(
                uiWebView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val webContainer = FrameLayout(this).apply {
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContent {
            KlasPlusTheme {
                ComposePlatformViewHost(
                    contentView = webContainer,
                    isLoading = isPageLoading,
                    contentTag = "lecture_web_container",
                )
            }
        }
        webView.configureAppWebView(
            javaScriptInterface = legacyFacade,
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            transparentBackground = false,
            disableScrollBars = false
        )
        bridgeMessageAdapters += AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.LECTURE,
            lifecycleScope,
            LectureLegacyBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        webView.loadUrl(AppUrls.KLAS_FRAME)
        appDependencies.fileTransfer(this).attachTo(webView)

        scrollView.isEnabled = false
        scrollView.visibility = View.GONE
        webView.visibility = View.GONE

        scrollView.setOnRefreshListener {
            webView.executeWebScript(KlasWebAutomationScripts.reloadPage())
        }

        webView.webViewClient = object : AndroidWebSurfaceClient(klasSurface) {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()

                if (url.contains("LctrumHomeStdPage.do")) {
                    webView.visibility = View.GONE
                    scrollView.visibility = View.VISIBLE
                    isShowingKLAS = false
                }

                if (!url.isTrustedAppWebUrl()) {
                    openValidatedExternalDestination(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.contains("OnlineCntntsStdPage.do")) {
                    webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectYearhakgi", yearHakgi))
                    webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectSubj", subjID))
                    webView.loadUrl(AppUrls.KLAS_LECTURE_HOME)
                    openVideoRoute(subjID, yearHakgi, sessionId)
                }
                scrollView.isRefreshing = false
                webView.executeWebScript(KlasWebAutomationScripts.styleContentPage())
                if (url.contains("Frame.do")) {
                    webView.executeWebScript(KlasWebAutomationScripts.openLecture(yearHakgi, subjID))
                    scrollView.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    isPageLoading = false
                }
                if(url.contains("LctrumHomeStdPage.do")) {
                    webView.executeWebScript(KlasWebAutomationScripts.collectLectureBoardPaths())
                    webView.clearHistory()
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
                    if(!isFinishing) {
                        val builder = MaterialAlertDialogBuilder(this@LectureActivity)
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
            ): Boolean = filePicker.showForWeb(filePathCallback, fileChooserParams)
        }

    }


    fun openQRScan() {
        if (!qrScanLaunchGuard.tryAcquire()) return
        loadingDialog = ComposeLoadingDialog(this)
        loadingDialog.show()


        val term = AcademicTermKey.parse(yearHakgi)
        if (
            subjName.isBlank() || subjID.isBlank() || sessionId.isBlank() ||
            term == null
        ) {
            Toast.makeText(
                this,
                "QR출석을 위한 정보를 불러오지 못했어요. 다시 시도해주세요.",
                Toast.LENGTH_SHORT,
            ).show()
            loadingDialog.dismiss()
            qrScanLaunchGuard.release()
            return
        }

        lifecycleScope.launch {
            var scannerLaunched = false
            try {
                val result = appDependencies.attendanceRepository.prepareCheckIn(
                    session = SecretValue.of(sessionId),
                    userAgent = KlasUserAgent.fromPlatform(WebSettings.getDefaultUserAgent(this@LectureActivity)),
                    request = QrPreparationRequest(
                        year = term.year,
                        semester = term.semester,
                        subjectId = subjID,
                        subjectName = subjName,
                    ),
                )
                when (result) {
                    is QrPreparationResult.Success -> {
                        val intent = Intent(this@LectureActivity, QRScanActivity::class.java)
                        intent.putExtra(
                            IntentExtras.BODY_JSON,
                            QrAttendancePayloadCodec().encode(result.payload),
                        )
                        intent.putExtra(IntentExtras.SUBJECT_ID, subjID)
                        intent.putExtra(IntentExtras.SUBJECT_NAME, subjName)
                        intent.putExtra(IntentExtras.SESSION_ID, sessionId)
                        loadingDialog.dismiss()
                        qrScanLauncher.launch(intent)
                        scannerLaunched = true
                    }
                    QrPreparationResult.UnsupportedSubject -> Toast.makeText(
                        this@LectureActivity,
                        "QR출석이 지원되지 않는 강의입니다.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    QrPreparationResult.SessionExpired -> showSessionExpiredDialog()
                    else -> Toast.makeText(
                        this@LectureActivity,
                        "출석 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                if (!scannerLaunched) {
                    if (loadingDialog.isShowing) loadingDialog.dismiss()
                    qrScanLaunchGuard.release()
                }
            }
        }
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

    override fun onBackPressed() {
        if(isShowingKLAS) {
            if(webView.canGoBack()) {
                webView.goBack()
            } else {
                webView.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                isShowingKLAS = false
                webView.loadUrl(AppUrls.KLAS_LECTURE_HOME)
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webSurfaces.forEach(AndroidWebSurface::dispose)
        webSurfaces.clear()
        bridgeMessageAdapters.forEach(AndroidBridgeMessageAdapter::dispose)
        bridgeMessageAdapters.clear()
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
            lectureActivity.uiWebView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_DATA,
                    JavaScriptArgument.Text(lectureActivity.sessionId),
                    JavaScriptArgument.Text(lectureActivity.subjID),
                    JavaScriptArgument.Text(lectureActivity.yearHakgi),
                ),
            )
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        lectureActivity.runOnUiThread {
            lectureActivity.openWebRoute(url, lectureActivity.sessionId)
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
            if(lectureActivity.boardNoticePath.isNullOrEmpty() || lectureActivity.boardPdsPath.isNullOrEmpty()) {
                Toast.makeText(lectureActivity, "아직 정보를 불러오지 못했어요. 몇 초 후에 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            val path = when (type) {
                "notice" -> lectureActivity.boardNoticePath
                "pds" -> lectureActivity.boardPdsPath
                else -> ""
            }
            lectureActivity.openBoardListRoute(
                path,
                title,
                lectureActivity.subjID,
                lectureActivity.yearHakgi,
                lectureActivity.sessionId,
            )
        }
    }

    @JavascriptInterface
    fun openBoardView(type: String, boardNo: String, masterNo: String) {
        lectureActivity.runOnUiThread {
            if(lectureActivity.boardNoticePath.isNullOrEmpty() || lectureActivity.boardPdsPath.isNullOrEmpty()) {
                Toast.makeText(lectureActivity, "아직 정보를 불러오지 못했어요. 몇 초 후에 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            val path = when (type) {
                "notice" -> lectureActivity.boardNoticePath
                "pds" -> lectureActivity.boardPdsPath
                else -> ""
            }
            lectureActivity.openBoardViewRoute(
                path,
                boardNo,
                masterNo,
                lectureActivity.subjID,
                lectureActivity.yearHakgi,
                lectureActivity.sessionId,
            )
        }
    }

    @JavascriptInterface
    fun openExternalLink(url: String) {
        lectureActivity.openValidatedExternalDestination(url)
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
            lectureActivity.openVideoRoute(
                lectureActivity.subjID,
                lectureActivity.yearHakgi,
                lectureActivity.sessionId,
            )
        }
    }

    @JavascriptInterface
    fun openLecturePlan() {
        lectureActivity.openLecturePlanRoute(lectureActivity.subjID, lectureActivity.sessionId)
    }

    @JavascriptInterface
    fun openQRScan() {
        lectureActivity.runOnUiThread { lectureActivity.openQRScan() }
    }
}
