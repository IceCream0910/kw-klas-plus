package com.icecream.kwklasplus.platform.web

import android.webkit.WebView
import com.icecream.kwklasplus.BuildConfig
import com.icecream.kwklasplus.appDependencies
import com.icecream.kwklasplus.core.bridge.KlasApiBridgeHandler
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
        BridgeRouter(KlasApiBridgeHandler(webView.context.appDependencies.klasApiCore, handler), synchronousHandler),
        codec,
    )
    private val allowedOrigins = if (surface == BridgeSurface.VIDEO) {
        VIDEO_ALLOWED_ORIGINS
    } else {
        ALLOWED_ORIGINS
    }
    private var navigationGeneration = 0L
    private var installed = false
    private var apiIdentityScriptHandler: ScriptHandler? = null
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
            val generation = navigationGeneration
            scope.launch {
                val response = router.route(
                        payload,
                        BridgeContext(surface, origin, isMainFrame, payloadSizeBytes = 0),
                    )
                if (navigationGeneration == generation) replyProxy.postMessage(response)
            }
        }
        adapters[webView] = java.lang.ref.WeakReference(this)
        apiIdentityScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            KlasNativeBridgeScripts.installApiAppIdentity("android", BuildConfig.VERSION_CODE).reveal(),
            setOf("https://klasplus.yuntae.in", KLAS_PLUS_BASE),
        )
        adapterScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            KlasNativeBridgeScripts.installAdapter().reveal(),
            allowedOrigins,
        )
        installed = true
        return BridgeAdapterAvailability.INSTALLED
    }

    private fun resetForNavigation() {
        navigationGeneration++
        router.reset()
    }

    fun dispose() {
        resetForNavigation()
        adapters.remove(webView)
        if (!installed) return
        apiIdentityScriptHandler?.remove()
        apiIdentityScriptHandler = null
        adapterScriptHandler?.remove()
        adapterScriptHandler = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, NATIVE_OBJECT_NAME)
        }
        installed = false
    }

    companion object {
        private val adapters = java.util.WeakHashMap<WebView, java.lang.ref.WeakReference<AndroidBridgeMessageAdapter>>()
        fun resetForNavigation(view: WebView) { adapters[view]?.get()?.resetForNavigation() }
        fun navigationGeneration(view: WebView): Long? = adapters[view]?.get()?.navigationGeneration

        const val NATIVE_OBJECT_NAME = "KlasNativeBridgeNative"
        val ALLOWED_ORIGINS = setOf(
            KLAS_BASE,
            "https://klasplus.yuntae.in",
            KLAS_PLUS_BASE
        )
        val VIDEO_ALLOWED_ORIGINS = ALLOWED_ORIGINS + "https://*.kw.ac.kr"
    }
}
