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
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.net.MailTo
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.kwklasplus.manager.AppDownloadManager
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.web.KlasWebAutomationScripts
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.bridge.legacy.LinkLegacyBridgeCommandHandler
import com.icecream.kwklasplus.platform.file.AndroidFilePicker
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.web.AndroidLegacyBridgeExposure
import com.icecream.kwklasplus.platform.navigation.openLecturePlanRoute
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.web.ComposeWebViewHost

class LinkViewActivity : AppCompatActivity() {
    lateinit var sessionId: String
    private val filePicker = AndroidFilePicker(this)
    lateinit var webView: WebView
    lateinit var onBackPressedCallback: OnBackPressedCallback
    var isOpenWebViewBottomSheet: Boolean = false
    private var isLoading by mutableStateOf(true)
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null
    private var legacyBridgeExposure: AndroidLegacyBridgeExposure? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(isOpenWebViewBottomSheet) {
                    webView.executeWebScript(KlasWebAutomationScripts.closeBottomSheet())
                    isOpenWebViewBottomSheet = false
                } else if(webView.canGoBack()){
                    webView.goBack()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        lockPortraitOnPhone()

        val url = intent.getStringExtra("url")?.takeIf(String::isAllowedWebUrl) ?: run {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        sessionId = intent.getStringExtra(IntentExtras.SESSION_ID).toString()

        webView = WebView(this)
        webSurface = AndroidWebSurface(webView)
        setContent {
            KlasPlusTheme {
                ComposeWebViewHost(
                    webView = webView,
                    isLoading = isLoading,
                )
            }
        }

        val legacyFacade = JavaScriptInterfaceForLinkView(this)
        webView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.LINK_VIEW,
            lifecycleScope,
            LinkLegacyBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        legacyBridgeExposure = AndroidLegacyBridgeExposure(webView, legacyFacade).also {
            it.update(url)
        }
        appDependencies.fileTransfer(this).attachTo(webView)
        webView.loadUrl(url)

        webView.webViewClient = object : AndroidWebSurfaceClient(requireNotNull(webSurface)) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                legacyBridgeExposure?.update(url)
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                hideLoading()

                if(url.contains("UserFindMemberNoPage.do")) {
                    webView.executeWebScript(KlasWebAutomationScripts.configureMemberNumberRecoveryPage())
                } else if(url.contains("UserFrstModPwdPage.do") || url.contains("UserFindPwdPage.do")) {
                    webView.executeWebScript(KlasWebAutomationScripts.configurePasswordRecoveryPage())
                } else if(url.contains("notice.jsp")) {
                    webView.executeWebScript(KlasWebAutomationScripts.makeNoticeScrollable())
                }
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
                    if(!isFinishing) {
                        val builder = MaterialAlertDialogBuilder(this@LinkViewActivity)
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


            // Enable file upload
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean = filePicker.showForWeb(filePathCallback, fileChooserParams)
        }
    }

    private fun showLoading() {
        isLoading = true
    }

    private fun hideLoading() {
        isLoading = false
    }

    override fun onDestroy() {
        legacyBridgeExposure?.dispose()
        legacyBridgeExposure = null
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroy()
    }
}


class JavaScriptInterfaceForLinkView(private val activity: LinkViewActivity) {
    @JavascriptInterface
    fun openPage(url: String) {
        activity.runOnUiThread {
            activity.openWebRoute(url, activity.sessionId)
        }
    }

    @JavascriptInterface
    fun openLecturePlanPage(id: String) {
        activity.runOnUiThread {
            activity.openLecturePlanRoute(id, activity.sessionId)
        }
    }

    @JavascriptInterface
    fun openWebViewBottomSheet() {
        activity.runOnUiThread {
            activity.isOpenWebViewBottomSheet = true
        }
    }

    @JavascriptInterface
    fun closeWebViewBottomSheet() {
        activity.runOnUiThread {
            activity.isOpenWebViewBottomSheet = false
        }
    }

    @JavascriptInterface
    fun completePageLoad() {
        activity.runOnUiThread {
            activity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_TOKEN,
                    JavaScriptArgument.Text(activity.sessionId),
                ),
            )
        }
    }

}
