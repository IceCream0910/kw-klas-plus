package com.icecream.kwklasplus.platform.web

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.auth.StoredCredential
import com.icecream.kwklasplus.core.auth.WebAuthDriver
import com.icecream.kwklasplus.core.auth.WebAuthObservationPolicy
import com.icecream.kwklasplus.core.auth.WebAuthPageObservation
import com.icecream.kwklasplus.core.auth.WebAuthResult
import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.web.JavaScriptArgument
import com.icecream.kwklasplus.core.web.LegacyWebCallback
import com.icecream.kwklasplus.core.web.LegacyWebScripts
import com.icecream.kwklasplus.executeWebScript
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidWebAuthDriver(
    private val webView: WebView,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val onInvalidCredentialAlert: (String?) -> Unit = {},
) : WebAuthDriver {
    private val policy = WebAuthObservationPolicy(KlasUrls.KLAS_LOGIN, "kw.ac.kr")

    override suspend fun authenticate(credential: StoredCredential): WebAuthResult =
        withTimeoutOrNull(timeoutMillis) {
            observeAuthentication(credential)
        } ?: WebAuthResult.Failure(AuthFailure.Timeout)

    private suspend fun observeAuthentication(credential: StoredCredential): WebAuthResult =
        suspendCancellableCoroutine { continuation ->
            var credentialInjected = false

            fun complete(result: WebAuthResult) {
                if (continuation.isActive) continuation.resume(result)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

                override fun onPageFinished(view: WebView, url: String) {
                    val observation = policy.pageFinished(
                        url,
                        credentialInjected,
                        cookieManager.getCookie(url),
                    )
                    when (observation) {
                        WebAuthPageObservation.InjectCredential -> {
                            view.executeWebScript(
                                LegacyWebScripts.call(
                                    LegacyWebCallback.LOGIN_SET_INITIAL,
                                    JavaScriptArgument.Text("on"),
                                    JavaScriptArgument.Text(credential.accountId),
                                    JavaScriptArgument.Text(credential.encryptedPassword.reveal()),
                                ),
                            )
                            credentialInjected = true
                        }
                        is WebAuthPageObservation.Authenticated -> complete(
                            WebAuthResult.SessionObserved(observation.token),
                        )
                        is WebAuthPageObservation.Failed -> complete(
                            WebAuthResult.Failure(observation.failure),
                        )
                        WebAuthPageObservation.Ignore -> Unit
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) complete(WebAuthResult.Failure(AuthFailure.Network))
                }
            }
            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?,
                ): Boolean {
                    webView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    result?.confirm()
                    val failure = policy.alert(message)
                    if (failure == AuthFailure.InvalidCredentials) {
                        onInvalidCredentialAlert(message)
                    }
                    complete(WebAuthResult.Failure(failure))
                    return true
                }
            }
            webView.loadUrl(KlasUrls.KLAS_LOGIN)
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
