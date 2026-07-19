package com.icecream.kwklasplus.modal

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwklasplus.AppPrefs
import com.icecream.kwklasplus.HomeActivity
import com.icecream.kwklasplus.IntentExtras
import com.icecream.kwklasplus.JavaScriptInterface
import com.icecream.kwklasplus.LinkViewActivity
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.TaskViewActivity
import com.icecream.kwklasplus.appPreferences
import com.icecream.kwklasplus.configureAppWebView
import com.icecream.kwklasplus.executeWebScript
import com.icecream.kwklasplus.isAllowedWebUrl
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.platform.web.AndroidBridgeMessageAdapter
import com.icecream.kwklasplus.platform.web.AndroidWebSurface
import com.icecream.kwklasplus.platform.web.AndroidWebSurfaceClient
import com.icecream.kwklasplus.platform.web.AndroidLegacyBridgeExposure
import com.icecream.kwklasplus.platform.bridge.legacy.WebViewModalLegacyBridgeCommandHandler
import com.icecream.kwklasplus.core.platform.openValidatedExternalDestination
import com.icecream.kwklasplus.platform.navigation.openWebRoute

class WebViewBottomSheetDialog(url: String, cancelable: Boolean = true) :
    BottomSheetDialogFragment() {
    private val url: String = url
    private val cancelable: Boolean = cancelable
    private var bridgeMessageAdapter: AndroidBridgeMessageAdapter? = null
    private var webSurface: AndroidWebSurface? = null
    private var legacyBridgeExposure: AndroidLegacyBridgeExposure? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = cancelable
        return inflater.inflate(R.layout.bottom_sheet_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!url.isAllowedWebUrl()) {
            dismissAllowingStateLoss()
            return
        }
        val webViewProgress: LinearLayout = view.findViewById(R.id.webViewProgress)
        val webView: WebView = view.findViewById(R.id.webview)
        webSurface = AndroidWebSurface(webView)

        webViewProgress.visibility = View.VISIBLE
        webView.visibility = View.GONE

        val legacyFacade = JavaScriptInterfaceForWebViewModal(requireActivity(), this)
        webView.configureAppWebView()
        bridgeMessageAdapter = AndroidBridgeMessageAdapter(
            webView,
            BridgeSurface.WEB_VIEW_MODAL,
            viewLifecycleOwner.lifecycleScope,
            WebViewModalLegacyBridgeCommandHandler(legacyFacade),
        ).also { it.install() }
        legacyBridgeExposure = AndroidLegacyBridgeExposure(webView, legacyFacade).also {
            it.update(url)
        }
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.setOnTouchListener { _, event ->
            event.action == MotionEvent.ACTION_MOVE
        }

        try {
            val pInfo: PackageInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.longVersionCode
            webView.settings.userAgentString += " AndroidApp_v${version}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        webView.webViewClient = object : AndroidWebSurfaceClient(requireNotNull(webSurface)) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                legacyBridgeExposure?.update(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                webView.visibility = View.VISIBLE
                webViewProgress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                webViewProgress.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                webViewProgress.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                super.onReceivedSslError(view, handler, error)
                webViewProgress.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
        }

        webView.loadUrl(url)

        // FIX: 태블릿에서 완전히 펼쳐지지 않는 이슈
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val dialog = dialog as BottomSheetDialog?
            val bottomSheet =
                dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.peekHeight = view.measuredHeight
        }
    }

    override fun onDestroyView() {
        legacyBridgeExposure?.dispose()
        legacyBridgeExposure = null
        webSurface?.dispose()
        webSurface = null
        bridgeMessageAdapter?.dispose()
        bridgeMessageAdapter = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "WebViewBottomSheetDialog"
    }
}

class JavaScriptInterfaceForWebViewModal(
    private val activity: Activity,
    private val dialog: WebViewBottomSheetDialog
) {
    @JavascriptInterface
    fun completePageLoad() {
        val sessionId = activity.appPreferences.getString(AppPrefs.KW_SESSION, null)

        activity.runOnUiThread {
            val webView = dialog.view?.findViewById<WebView>(R.id.webview)
            webView?.executeWebScript(
                LegacyWebScripts.call(
                    LegacyWebCallback.RECEIVE_TOKEN,
                    JavaScriptArgument.Text(sessionId.orEmpty()),
                ),
            )
        }
    }

    @JavascriptInterface
    fun closeModal() {
        activity.runOnUiThread {
            dialog.dismiss()
        }
    }

    @JavascriptInterface
    fun showToast(toast: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun openExternalPage(url: String) {
        activity.runOnUiThread {
            activity.openValidatedExternalDestination(url)
        }
    }

    @JavascriptInterface
    fun openLibraryQR() {
        LibraryQRModal.newInstance(false)
            .show((activity as HomeActivity).supportFragmentManager, MenuBottomSheetDialog.TAG)

    }

    @JavascriptInterface
    fun openPage(url: String) {
        val sessionId = activity.appPreferences.getString(AppPrefs.KW_SESSION, null)
        activity.openWebRoute(url, sessionId)
    }
}
