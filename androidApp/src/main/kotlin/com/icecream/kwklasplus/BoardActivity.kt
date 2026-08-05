package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.net.MailTo
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.kwklasplus.manager.AppDownloadManager
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.bridge.legacy.BoardLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.platform.file.AndroidFilePicker
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.web.ComposeRefreshableWebViewHost

class BoardActivity : AppCompatActivity() {
    lateinit var sessionId: String
    lateinit var path: String
    lateinit var title: String
    lateinit var yearHakgi: String
    lateinit var subjID: String
    lateinit var boardNo: String
    lateinit var masterNo: String
    private val filePicker = AndroidFilePicker(this)
    lateinit var webView: WebView
    private lateinit var swipeLayout: SwipeRefreshLayout
    lateinit var onBackPressedCallback: OnBackPressedCallback
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = object: OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                when {
                    webView.canGoBack() -> webView.goBack()
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        lockPortraitOnPhone()

        val type = intent.getStringExtra("type").toString()
        sessionId = intent.getStringExtra(IntentExtras.SESSION_ID).toString()
        yearHakgi = intent.getStringExtra(IntentExtras.YEAR_HAKGI).toString()
        subjID = intent.getStringExtra(IntentExtras.SUBJECT_ID).toString()
        title = intent.getStringExtra("title").toString()
        path = intent.getStringExtra("path").toString()

        webView = WebView(this)
        swipeLayout = SwipeRefreshLayout(this).apply {
            addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            setOnRefreshListener { webView.reload() }
        }
        webSurface = AndroidWebSurface(webView)
        setContent {
            KlasPlusTheme {
                ComposeRefreshableWebViewHost(
                    refreshLayout = swipeLayout,
                    isLoading = false,
                )
            }
        }
        val bridgeDelegate = BoardBridgeDelegate(this)
        webView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true
        )
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.BOARD,
            lifecycleScope,
            BoardLegacyBridgeCommandHandler(bridgeDelegate),
        ).also { it.install() }
        appDependencies.fileTransfer(this).attachTo(webView)

        if(type == "list") {
            webView.loadUrl("${AppUrls.KLAS_PLUS_BASE}/boardList?title=$title")
        } else if(type == "view"){
            boardNo = intent.getStringExtra("boardNo").toString()
            masterNo = intent.getStringExtra("masterNo").toString()
            webView.loadUrl("${AppUrls.KLAS_PLUS_BASE}/boardView?boardNo=$boardNo&masterNo=$masterNo")
        } else {
            var builder = MaterialAlertDialogBuilder(this)
            builder.setTitle("안내")
                .setMessage("잘못된 접근입니다.")
                .setPositiveButton("확인") { dialog, id ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }

        webView.webViewClient = object : AndroidWebSurfaceClient(requireNotNull(webSurface)) {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                onBackPressedCallback.isEnabled = webView.canGoBack()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                swipeLayout.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(webView: WebView, webResourceRequest: WebResourceRequest): Boolean {
                val uri = webResourceRequest.url.toString()
                if (uri.startsWith("sms:") || uri.startsWith("tel:") || uri.startsWith(MailTo.MAILTO_SCHEME) || uri.startsWith("geo:")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    if (intent.resolveActivity(webView.context.packageManager) != null) {
                        webView.context.startActivity(intent)
                    }
                    return true
                }
                
                if (uri.startsWith("http:") || uri.startsWith("https:")) {
                    if (uri.isTrustedAppWebUrl()) {
                        return false
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        webView.context.startActivity(intent)
                        return true
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
                    val builder = MaterialAlertDialogBuilder(this@BoardActivity)
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
            ): Boolean = filePicker.showForWeb(filePathCallback, fileChooserParams)
        }
    }

    override fun onBackPressed() {
        if(webView.canGoBack()){
            webView.goBack()

        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroy()
    }
}


class BoardBridgeDelegate(private val activity: BoardActivity) {
    fun openPage(url: String) {
        activity.runOnUiThread {
            activity.openWebRoute(url, activity.sessionId)
        }
    }

    fun openExternalLink(url: String) {
        activity.openValidatedExternalDestination(url)
    }

    fun completePageLoad() {
        activity.runOnUiThread {
            activity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_DATA,
                    JavaScriptArgument.Text(activity.sessionId),
                    JavaScriptArgument.Text(activity.subjID),
                    JavaScriptArgument.Text(activity.yearHakgi),
                    JavaScriptArgument.Text(activity.path),
                ),
            )
        }
    }

}
