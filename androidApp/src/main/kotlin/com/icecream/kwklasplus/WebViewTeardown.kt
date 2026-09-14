package com.icecream.kwklasplus

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient

fun WebView.destroyOwnedWebView() {
    stopLoading()
    webChromeClient = null
    webViewClient = WebViewClient()
    setDownloadListener(null)
    (parent as? ViewGroup)?.removeView(this)
    removeAllViews()
    destroy()
}
