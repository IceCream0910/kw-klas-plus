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
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.kwklasplus.manager.AppDownloadManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LinkViewActivity : AppCompatActivity() {
    lateinit var sessionId: String
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_RESULTCODE = 1
    lateinit var webView: WebView
    lateinit var loadingIndicator: LinearLayout
    lateinit var onBackPressedCallback: OnBackPressedCallback
    var isOpenWebViewBottomSheet: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link_view)

        applyEdgeToEdgeInsets()

        onBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(isOpenWebViewBottomSheet) {
                    webView.evaluateJavascript("window.closeWebViewBottomSheet();", null)
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

        val url = intent.getStringExtra("url")?.let {
            val sanitizedUrl = Uri.parse(it).toString()
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
        sessionId = intent.getStringExtra(IntentExtras.SESSION_ID).toString()

        webView = findViewById<WebView>(R.id.webView)
        loadingIndicator = findViewById(R.id.progressBar)

        webView.configureAppWebView(
            javaScriptInterface = JavaScriptInterfaceForLinkView(this),
            allowFileAccess = true,
            allowContentAccess = true,
            javaScriptCanOpenWindowsAutomatically = true,
            disableScrollBars = false
        )
        AppDownloadManager(this).attachTo(webView)
        if (url != null) {
            webView.loadUrl(url)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                hideLoading()
                webView.visibility = View.VISIBLE

                if(url.contains("UserFindMemberNoPage.do")) {
                    webView.evaluateJavascript(
                        "document.querySelectorAll('[data-page-btn=\"close\"]').forEach(button => {\n" +
                                "    button.onclick = function(e) {\n" +
                                "        e.preventDefault();\n" +
                                "        e.stopPropagation();\n" +
                                "        window.close();\n" +
                                "    };\n" +
                                "});" +
                                "var container = document.querySelector('.ax-search-tbl > div:first-child');\n" +
                                "    \n" +
                                "    if (container) {\n" +
                                "        // A div에 display: flex; flex-direction: column; 적용\n" +
                                "        container.style.display = 'flex';\n" +
                                "        container.style.flexDirection = 'column';\n" +
                                "        \n" +
                                "        // 2. A div의 첫번째 child div, 두번째 child div 선택\n" +
                                "        var child1 = container.children[0];\n" +
                                "        var child2 = container.children[1];\n" +
                                "        \n" +
                                "        // 3. 각각 style.width를 100%로 적용\n" +
                                "        if (child1) {\n" +
                                "            child1.style.width = '100%';\n" +
                                "            child1.style.boxSizing = 'border-box'; // 패딩/보더 포함 100% 설정 권장\n" +
                                "        }\n" +
                                "        if (child2) {\n" +
                                "            child2.style.width = '100%';\n" +
                                "            child2.style.boxSizing = 'border-box';\n" +
                                "        }\n" +
                                "    }",
                        null
                    )
                } else if(url.contains("UserFrstModPwdPage.do") || url.contains("UserFindPwdPage.do")) {
                    webView.evaluateJavascript(
                        "document.querySelector('.closeB').onclick = function(e) {\n" +
                                "e.preventDefault();\n" +
                                "e.stopPropagation();\n" +
                                "window.close();\n" +
                                "    };",
                        null
                    )
                } else if(url.contains("notice.jsp")) {
                    webView.evaluateJavascript(
                        "document.querySelector('.contents').style.overflowY = 'scroll';",
                        null
                    )
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
                    if (uri.contains("klas.kw.ac.kr") || uri.contains("klasplus.yuntae.in")) {
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

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
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


class JavaScriptInterfaceForLinkView(private val activity: LinkViewActivity) {
    @JavascriptInterface
    fun openPage(url: String) {
        activity.runOnUiThread {
            val intent = Intent(activity, LinkViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra(IntentExtras.SESSION_ID, activity.sessionId)
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openLecturePlanPage(id: String) {
        activity.runOnUiThread {
            val intent = Intent(activity, LctPlanActivity::class.java)
            intent.putExtra("subjID", id)
            intent.putExtra(IntentExtras.LEGACY_SESSION_ID, activity.sessionId)
            activity.startActivity(intent)
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
            activity.webView.evaluateJavascript(
                "javascript:window.receiveToken('${activity.sessionId}')",
                null
            )
        }
    }

}
