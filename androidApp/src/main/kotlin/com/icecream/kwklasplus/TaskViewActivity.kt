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
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.kwklasplus.manager.AppDownloadManager
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.web.KlasWebAutomationScripts
import com.icecream.kwklasplus.platform.file.AndroidFilePicker
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.navigation.openVideoRoute
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TaskViewActivity : AppCompatActivity() {
    private val filePicker = AndroidFilePicker(this)
    lateinit var webView: WebView
    lateinit var loadingIndicator: LinearLayout
    private lateinit var swipeLayout: SwipeRefreshLayout
    lateinit var onBackPressedCallback: OnBackPressedCallback
    private var webSurface: AndroidWebSurface? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_view)

        applyEdgeToEdgeInsets()

        onBackPressedCallback = object: OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                when {
                    webView.canGoBack() -> webView.goBack()
                }
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        lockPortraitOnPhone()

        val url = intent.getStringExtra("url")?.let {
            val sanitizedUrl = AppUrls.KLAS_BASE + Uri.parse(it).toString()
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
        val yearHakgi = intent.getStringExtra(IntentExtras.YEAR_HAKGI)
        val subj = intent.getStringExtra(IntentExtras.SUBJECT)
        var sessionId = intent.getStringExtra(IntentExtras.SESSION_ID)

        swipeLayout = findViewById<SwipeRefreshLayout>(R.id.swipeLayout)

        swipeLayout.setOnRefreshListener {
            webView.reload()
        }

        webView = findViewById<WebView>(R.id.webView)
        webSurface = AndroidWebSurface(webView)
        loadingIndicator = findViewById(R.id.progressBar)

        webView.configureAppWebView(
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            transparentBackground = false,
            disableScrollBars = false
        )
        webView.loadUrl(url)
        appDependencies.fileTransfer(this).attachTo(webView)

        var isOpenVideoAcitivity = false
        var isScriptExecuted = false

        if (url.contains("OnlineCntntsStdPage.do")) {
            isOpenVideoAcitivity = true
            finish()
            openVideoRoute(subj, yearHakgi, sessionId)
        }

        webView.webViewClient = object : AndroidWebSurfaceClient(requireNotNull(webSurface)) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showLoading()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                onBackPressedCallback.isEnabled = webView.canGoBack()
            }

            @SuppressLint("SuspiciousIndentation")
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                webView.executeWebScript(KlasWebAutomationScripts.styleContentPage())
                swipeLayout.isRefreshing = false
                hideLoading()
                webView.visibility = View.VISIBLE

                if (!isScriptExecuted) {
                    webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectYearhakgi", yearHakgi.toString()))
                    webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectSubj", subj.toString()))
                    webView.reload()
                    isScriptExecuted = true
                } else {
                    if(!isOpenVideoAcitivity && url.contains("OnlineCntntsStdPage.do")) {
                        webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectYearhakgi", yearHakgi.toString()))
                        webView.executeWebScript(LegacyWebScripts.setLocalStorage("selectSubj", subj.toString()))
                        finish()
                        openVideoRoute(subj, yearHakgi, sessionId)
                    }
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
                        openValidatedExternalDestination(uri)
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
            ): Boolean = filePicker.showForWeb(filePathCallback, fileChooserParams)
        }
    }

    private fun showLoading() {
        if (isFinishing || isDestroyed) return
        loadingIndicator.visibility = View.VISIBLE
        swipeLayout.visibility = View.GONE
    }

    private fun hideLoading() {
        if (isFinishing || isDestroyed) return
        loadingIndicator.visibility = View.GONE
        swipeLayout.visibility = View.VISIBLE
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
        super.onDestroy()
    }


}
