package com.icecream.kwklasplus.modal

import LibraryQRModal
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
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.icecream.kwklasplus.HomeActivity
import com.icecream.kwklasplus.JavaScriptInterface
import com.icecream.kwklasplus.LinkViewActivity
import com.icecream.kwklasplus.R
import com.icecream.kwklasplus.TaskViewActivity

class WebViewBottomSheetDialog(url: String, cancelable: Boolean = true) :
    BottomSheetDialogFragment() {
    private val url: String = url
    private val cancelable: Boolean = cancelable

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
        val webViewProgress: LinearLayout = view.findViewById(R.id.webViewProgress)
        val webView: WebView = view.findViewById(R.id.webview)

        webViewProgress.visibility = View.VISIBLE
        webView.visibility = View.GONE

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.setOnTouchListener { _, event ->
            event.action == MotionEvent.ACTION_MOVE
        }
        webView.setBackgroundColor(0)
        webView.addJavascriptInterface(
            JavaScriptInterfaceForWebViewModal(requireActivity(), this),
            "Android"
        )

        try {
            val pInfo: PackageInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.longVersionCode
            webView.settings.userAgentString += " AndroidApp_v${version}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.visibility = View.VISIBLE
                webViewProgress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
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
        val sharedPreferences =
            activity.getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)

        activity.runOnUiThread {
            val webView = dialog.view?.findViewById<WebView>(R.id.webview)
            webView?.evaluateJavascript("javascript:window.receiveToken('${sessionId}')", null)
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun openLibraryQR() {
        LibraryQRModal(false).show((activity as HomeActivity).supportFragmentManager, MenuBottomSheetDialog.TAG)

    }

    @JavascriptInterface
    fun openPage(url: String) {
        val sharedPreferences =
            activity.getSharedPreferences("com.icecream.kwklasplus", MODE_PRIVATE)
        val sessionId = sharedPreferences.getString("kwSESSION", null)

        val intent = Intent(activity, LinkViewActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("sessionID", sessionId)
        activity.startActivity(intent)
    }
}
