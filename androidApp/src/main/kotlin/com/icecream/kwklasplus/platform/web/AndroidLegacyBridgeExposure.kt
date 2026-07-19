package com.icecream.kwklasplus.platform.web

import android.webkit.WebView
import com.icecream.kwklasplus.core.bridge.TrustedOriginPolicy

class AndroidLegacyBridgeExposure(
    private val webView: WebView,
    private val facade: Any,
    private val originPolicy: TrustedOriginPolicy = TrustedOriginPolicy(),
) {
    private var exposed = false

    fun update(topLevelUrl: String?) {
        val shouldExpose = topLevelUrl?.let(originPolicy::isTrustedUrl) == true
        if (shouldExpose == exposed) return
        if (shouldExpose) {
            webView.addJavascriptInterface(facade, INTERFACE_NAME)
        } else {
            webView.removeJavascriptInterface(INTERFACE_NAME)
        }
        exposed = shouldExpose
    }

    fun dispose() {
        webView.removeJavascriptInterface(INTERFACE_NAME)
        exposed = false
    }

    private companion object {
        const val INTERFACE_NAME = "Android"
    }
}
