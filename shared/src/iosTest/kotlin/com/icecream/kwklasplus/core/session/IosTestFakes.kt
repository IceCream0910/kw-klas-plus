package com.icecream.kwklasplus.core.session

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHTTPCookie
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<SecureKey, SecretValue>()

    override suspend fun read(key: SecureKey): SecretValue? = values[key]

    override suspend fun write(key: SecureKey, value: SecretValue) {
        values[key] = value
    }

    override suspend fun remove(key: SecureKey) {
        values.remove(key)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class InMemoryHttpCookieStoreOps : HttpCookieStoreOps {
    private val cookies = mutableListOf<NSHTTPCookie>()

    override fun setCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookies.removeAll {
            it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
        }
        cookies.add(cookie)
        onComplete()
    }

    override fun deleteCookie(cookie: NSHTTPCookie, onComplete: () -> Unit) {
        cookies.removeAll {
            it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
        }
        onComplete()
    }

    override fun getAllCookies(onComplete: (List<NSHTTPCookie>) -> Unit) {
        onComplete(cookies.toList())
    }
}

internal fun <T> runSuspendTest(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return requireNotNull(outcome) { "suspend block did not complete synchronously" }.getOrThrow()
}
