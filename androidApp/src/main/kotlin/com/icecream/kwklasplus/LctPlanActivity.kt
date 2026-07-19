package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.util.DeviceProperties.isTablet
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.bridge.legacy.LecturePlanLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.platform.navigation.openWebRoute

class LctPlanActivity : AppCompatActivity() {
    lateinit var sessionIdForOtherClass: String
    lateinit var webView: WebView
    lateinit var loadingIndicator: LinearLayout
    lateinit var subjID: String
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lct_plan)
        applyEdgeToEdgeInsets()

        lockPortraitOnPhone()

        sessionIdForOtherClass =
            intent.getStringExtra(IntentExtras.LEGACY_SESSION_ID)
                ?: intent.getStringExtra(IntentExtras.SESSION_ID).toString()
        subjID = intent.getStringExtra(IntentExtras.SUBJECT_ID).toString()

        webView = findViewById<WebView>(R.id.webView)
        webSurface = AndroidWebSurface(webView)
        loadingIndicator = findViewById(R.id.progressBar)

        val legacyFacade = JavaScriptInterfaceLecturePlan(this)
        webView.configureAppWebView(
            javaScriptInterface = legacyFacade,
            supportMultipleWindows = true,
            javaScriptCanOpenWindowsAutomatically = true,
            transparentBackground = false,
            disableScrollBars = false
        )
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.LECTURE_PLAN,
            lifecycleScope,
            LecturePlanLegacyBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        webView.loadUrl(AppUrls.LECTURE_PLAN)

        webView.webViewClient = object : AndroidWebSurfaceClient(requireNotNull(webSurface)) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                hideLoading()
                webView.visibility = View.VISIBLE
            }
        }

        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }
        })
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
    override fun onDestroy() {
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroy()
    }
}


class JavaScriptInterfaceLecturePlan(private val lctPlanActivity: LctPlanActivity) {
    @JavascriptInterface
    fun completePageLoad() {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.webView.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_DATA,
                    JavaScriptArgument.Text(lctPlanActivity.sessionIdForOtherClass),
                    JavaScriptArgument.Text(lctPlanActivity.subjID),
                ),
            )
        }
    }

    @JavascriptInterface
    fun openPage(url: String) {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.openWebRoute(url, lctPlanActivity.sessionIdForOtherClass)
        }
    }

    @JavascriptInterface
    fun openExternalPage(url: String) {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.openValidatedExternalDestination(url)
        }
    }
}
