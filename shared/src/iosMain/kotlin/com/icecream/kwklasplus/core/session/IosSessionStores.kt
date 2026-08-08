package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.legacy.KlasUrls
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
class IosWebCookieStore private constructor(
    private val ops: HttpCookieStoreOps,
) : WebCookieStore {
    constructor() : this(
        WkHttpCookieStoreOps(WKWebsiteDataStore.defaultDataStore().httpCookieStore),
    )

    override suspend fun setSessionCookie(token: SecretValue) {
        setCookie(SessionCookieFactory.create(token.reveal()))
    }

    override suspend fun clearSessionCookie() {
        sessionCookies().forEach { cookie ->
            deleteCookie(cookie)
        }
        // Android clear는 Max-Age=0 Set-Cookie와 동일하게 만료 쿠키를 한 번 기록
        setCookie(SessionCookieFactory.createExpired())
        sessionCookies().forEach { cookie ->
            deleteCookie(cookie)
        }
    }

    // 검증·디버그용 WebCookieStore 공개 API에는 포함하지 않음
    suspend fun readSessionCookieValue(): String? =
        sessionCookies()
            .map { it.value }
            .firstOrNull { it.isNotBlank() }

    internal suspend fun sessionCookies(): List<NSHTTPCookie> =
        allCookies().filter { cookie ->
            cookie.name == SESSION_NAME && matchesSessionDomain(cookie.domain)
        }

    private suspend fun setCookie(cookie: NSHTTPCookie) = suspendCoroutine { continuation ->
        ops.setCookie(cookie) { continuation.resume(Unit) }
    }

    private suspend fun deleteCookie(cookie: NSHTTPCookie) = suspendCoroutine { continuation ->
        ops.deleteCookie(cookie) { continuation.resume(Unit) }
    }

    private suspend fun allCookies(): List<NSHTTPCookie> = suspendCoroutine { continuation ->
        ops.getAllCookies { cookies -> continuation.resume(cookies) }
    }

    private fun matchesSessionDomain(domain: String): Boolean {
        val normalized = domain.removePrefix(".")
        return normalized == SESSION_DOMAIN_HOST
    }

    companion object {
        private const val SESSION_NAME = "SESSION"
        private const val SESSION_DOMAIN_HOST = "kw.ac.kr"

        // 테스트에서 저장소 구현을 주입
        internal fun createForTests(ops: HttpCookieStoreOps): IosWebCookieStore =
            IosWebCookieStore(ops)
    }
}

internal interface HttpCookieStoreOps {
    fun setCookie(cookie: NSHTTPCookie, onComplete: () -> Unit)
    fun deleteCookie(cookie: NSHTTPCookie, onComplete: () -> Unit)
    fun getAllCookies(onComplete: (List<NSHTTPCookie>) -> Unit)
}

@OptIn(ExperimentalForeignApi::class)
internal class WkHttpCookieStoreOps(
    private val cookieStore: WKHTTPCookieStore,
) : HttpCookieStoreOps {
    override fun setCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookieStore.setCookie(cookie) { onComplete() }
    }

    override fun deleteCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookieStore.deleteCookie(cookie) { onComplete() }
    }

    override fun getAllCookies(onComplete: (List<NSHTTPCookie>) -> Unit) {
        cookieStore.getAllCookies { cookies ->
            @Suppress("UNCHECKED_CAST")
            onComplete((cookies as? List<NSHTTPCookie>).orEmpty())
        }
    }
}

// Set-Cookie 문자열 파싱은 Android CookieManager.setCookie 인자와 동일한 계약을 유지
@OptIn(ExperimentalForeignApi::class)
internal object SessionCookieFactory {
    fun create(value: String): NSHTTPCookie =
        parseSetCookie(
            "SESSION=$value; Path=/; Domain=.kw.ac.kr; Secure; HttpOnly",
        )

    fun createExpired(): NSHTTPCookie =
        parseSetCookie(
            "SESSION=; Max-Age=0; Path=/; Domain=.kw.ac.kr; Secure; HttpOnly",
        )

    private fun parseSetCookie(setCookie: String): NSHTTPCookie {
        val url = requireNotNull(NSURL.URLWithString(KlasUrls.KLAS_BASE)) {
            "KLAS_BASE is not a valid URL: ${KlasUrls.KLAS_BASE}"
        }
        @Suppress("UNCHECKED_CAST")
        val cookies = NSHTTPCookie.cookiesWithResponseHeaderFields(
            mapOf("Set-Cookie" to setCookie),
            url,
        ) as List<NSHTTPCookie>
        return requireNotNull(cookies.firstOrNull()) {
            "SESSION Set-Cookie could not be parsed: length=${setCookie.length}"
        }
    }
}
