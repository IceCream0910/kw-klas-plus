package com.icecream.kwklasplus.platform.web

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.icecream.kwklasplus.core.bridge.BridgeCommandHandler
import com.icecream.kwklasplus.core.bridge.BridgeContext
import com.icecream.kwklasplus.core.bridge.BridgeJsonCodec
import com.icecream.kwklasplus.core.bridge.BridgeRouter
import com.icecream.kwklasplus.core.bridge.BridgeSurface
import com.icecream.kwklasplus.core.bridge.JsonBridgeRouter
import com.icecream.kwklasplus.core.bridge.SynchronousBridgeCommandHandler
import com.icecream.kwklasplus.core.legacy.KlasUrls.KLAS_BASE
import com.icecream.kwklasplus.core.legacy.KlasUrls.KLAS_PLUS_BASE
import com.icecream.kwklasplus.core.web.KlasNativeBridgeScripts
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
    private val allowedOrigins = if (surface == BridgeSurface.VIDEO) {
        VIDEO_ALLOWED_ORIGINS
    } else {
        ALLOWED_ORIGINS
    }
    private var installed = false
    private var adapterScriptHandler: ScriptHandler? = null

    fun install(): BridgeAdapterAvailability {
        if (installed) return BridgeAdapterAvailability.INSTALLED
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return BridgeAdapterAvailability.UNSUPPORTED
        }
        WebViewCompat.addWebMessageListener(
            webView,
            NATIVE_OBJECT_NAME,
            allowedOrigins,
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
        adapterScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            KlasNativeBridgeScripts.installAdapter().reveal(),
            allowedOrigins,
        )
        installed = true
        return BridgeAdapterAvailability.INSTALLED
    }

    fun dispose() {
        if (!installed) return
        adapterScriptHandler?.remove()
        adapterScriptHandler = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, NATIVE_OBJECT_NAME)
        }
        installed = false
    }

    companion object {
        const val NATIVE_OBJECT_NAME = "KlasNativeBridgeNative"
        val ALLOWED_ORIGINS = setOf(
            KLAS_BASE,
            KLAS_PLUS_BASE
        )
        val VIDEO_ALLOWED_ORIGINS = ALLOWED_ORIGINS + "https://*.kw.ac.kr"
    }
}
