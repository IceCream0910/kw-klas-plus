package com.icecream.kwklasplus.platform.web

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.icecream.kwklasplus.core.web.WebFailureCategory
import com.icecream.kwklasplus.core.web.WebLoadState
import com.icecream.kwklasplus.core.web.WebScript
import com.icecream.kwklasplus.core.web.WebSurface
import com.icecream.kwklasplus.core.web.WebSurfaceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidWebSurface(
    webView: WebView,
) : WebSurface {
    private var webView: WebView? = webView
    private val mutableSnapshot = MutableStateFlow(WebSurfaceSnapshot())
    override val snapshot = mutableSnapshot.asStateFlow()

    override fun load(url: String) {
        webView?.loadUrl(url)
    }

    override fun reload() {
        webView?.reload()
    }

    override fun stopLoading() {
        webView?.stopLoading()
    }

    override fun goBack(): Boolean {
        val view = webView ?: return false
        if (!view.canGoBack()) return false
        view.goBack()
        updateNavigationState(view)
        return true
    }

    override fun goForward(): Boolean {
        val view = webView ?: return false
        if (!view.canGoForward()) return false
        view.goForward()
        updateNavigationState(view)
        return true
    }

    override suspend fun evaluate(script: WebScript): String? = suspendCancellableCoroutine { continuation ->
        val view = webView
        if (view == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        view.evaluateJavascript(script.reveal()) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }

    fun onPageStarted(url: String) {
        val view = webView ?: return
        mutableSnapshot.value = WebSurfaceSnapshot(
            loadState = WebLoadState.Loading(url),
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }

    fun onPageFinished(url: String) {
        val view = webView ?: return
        mutableSnapshot.value = WebSurfaceSnapshot(
            loadState = WebLoadState.Ready(url),
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }

    fun onPageFailed(url: String?, category: WebFailureCategory) {
        val view = webView ?: return
        mutableSnapshot.value = WebSurfaceSnapshot(
            loadState = WebLoadState.Failed(url, category),
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }

    override fun dispose() {
        webView = null
        mutableSnapshot.value = WebSurfaceSnapshot(loadState = WebLoadState.Disposed)
    }

    private fun updateNavigationState(view: WebView) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
        )
    }
}

open class AndroidWebSurfaceClient(
    private val surface: AndroidWebSurface,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let(surface::onPageStarted)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        surface.onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            surface.onPageFailed(request.url?.toString(), WebFailureCategory.NETWORK)
        }
    }
}
