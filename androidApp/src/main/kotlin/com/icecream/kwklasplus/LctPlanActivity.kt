package com.icecream.kwklasplus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import androidx.lifecycle.lifecycleScope
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.bridge.legacy.LecturePlanLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.platform.navigation.openWebRoute
import com.icecream.kwklasplus.ui.theme.KlasPlusTheme
import com.icecream.kwklasplus.ui.web.ComposeWebViewHost

class LctPlanActivity : AppCompatActivity() {
    lateinit var sessionIdForOtherClass: String
    lateinit var webView: WebView
    lateinit var subjID: String
    private var isLoading by mutableStateOf(true)
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhone()

        sessionIdForOtherClass =
            intent.getStringExtra(IntentExtras.LEGACY_SESSION_ID)
                ?: intent.getStringExtra(IntentExtras.SESSION_ID).toString()
        subjID = intent.getStringExtra(IntentExtras.SUBJECT_ID).toString()

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

        val bridgeDelegate = LecturePlanBridgeDelegate(this)
        webView.configureAppWebView(
            supportMultipleWindows = true,
            javaScriptCanOpenWindowsAutomatically = true,
            transparentBackground = false,
            disableScrollBars = false
        )
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.LECTURE_PLAN,
            lifecycleScope,
            LecturePlanLegacyBridgeCommandHandler(bridgeDelegate),
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
        isLoading = true
    }

    private fun hideLoading() {
        isLoading = false
    }
    override fun onDestroy() {
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroy()
    }
}


class LecturePlanBridgeDelegate(private val lctPlanActivity: LctPlanActivity) {
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

    fun openPage(url: String) {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.openWebRoute(url, lctPlanActivity.sessionIdForOtherClass)
        }
    }

    fun openExternalPage(url: String) {
        lctPlanActivity.runOnUiThread {
            lctPlanActivity.openValidatedExternalDestination(url)
        }
    }
}
