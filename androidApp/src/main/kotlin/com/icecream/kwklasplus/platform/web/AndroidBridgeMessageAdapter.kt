package com.icecream.kwklasplus.platform.web

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeContext
import com.icecream.kwklasplus.core.bridge.BridgeJsonCodec
import com.icecream.kwklasplus.core.bridge.BridgeRouter
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.core.bridge.JsonBridgeRouter
import com.icecream.kwklasplus.core.bridge.SynchronousBridgeCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class BridgeAdapterAvailability {
    INSTALLED,
    UNSUPPORTED,
}

class AndroidBridgeMessageAdapter(
    private val webView: WebView,
    private val surface: BridgeSurface,
    private val scope: CoroutineScope,
    handler: BridgeCommandHandler,
    synchronousHandler: SynchronousBridgeCommandHandler? = null,
) {
    private val codec = BridgeJsonCodec()
    private val router = JsonBridgeRouter(
        BridgeRouter(handler, synchronousHandler),
        codec,
    )
    private var installed = false

    fun install(): BridgeAdapterAvailability {
        if (installed) return BridgeAdapterAvailability.INSTALLED
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return BridgeAdapterAvailability.UNSUPPORTED
        }
        WebViewCompat.addWebMessageListener(
            webView,
            NATIVE_OBJECT_NAME,
            ALLOWED_ORIGINS,
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (message.type != WebMessageCompat.TYPE_STRING) {
                replyProxy.postMessage(codec.malformedResponse())
                return@addWebMessageListener
            }
            val payload = message.data ?: run {
                replyProxy.postMessage(codec.malformedResponse())
                return@addWebMessageListener
            }
            val origin = "${sourceOrigin.scheme}://${sourceOrigin.authority}"
            scope.launch {
                replyProxy.postMessage(
                    router.route(
                        payload,
                        BridgeContext(surface, origin, isMainFrame, payloadSizeBytes = 0),
                    ),
                )
            }
        }
        installed = true
        return BridgeAdapterAvailability.INSTALLED
    }

    fun dispose() {
        if (!installed) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, NATIVE_OBJECT_NAME)
        }
        installed = false
    }

    companion object {
        const val NATIVE_OBJECT_NAME = "KlasNativeBridgeNative"
        val ALLOWED_ORIGINS = setOf(
            "https://klas.kw.ac.kr",
            "https://klasplus.yuntae.in",
        )
    }
}
