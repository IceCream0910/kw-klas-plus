package com.icecream.kwklasplus.platform.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.icecream.kwklasplus.core.demo.DemoSession
import com.icecream.kwklasplus.core.demo.DemoWebScripts

open class DemoWebViewClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (DemoSession.active && DemoWebScripts.isSchoolUrl(request.url.toString())) {
            if (request.isForMainFrame) view.post {
                android.widget.Toast.makeText(view.context, "데모 모드에서는 학교 페이지를 사용할 수 없어요.", android.widget.Toast.LENGTH_LONG).show()
            }
            return WebResourceResponse("text/plain", "UTF-8", 403, "Demo network blocked", emptyMap(), "".byteInputStream())
        }
        return super.shouldInterceptRequest(view, request)
    }
}
