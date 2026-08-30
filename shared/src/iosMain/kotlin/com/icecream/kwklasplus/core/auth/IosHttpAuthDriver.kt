package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.network.createIosKlasHttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage

class IosHttpAuthDriver(
    private val tokenEncryptor: LoginTokenEncryptor,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : WebAuthDriver {
    override suspend fun authenticate(credential: StoredCredential): WebAuthResult {
        val cookies = AcceptAllCookiesStorage()
        val client = createIosKlasHttpClient(
            timeoutMillis = timeoutMillis,
            cookieStorage = cookies,
        )
        return try {
            KlasHttpAuthDriver(client, cookies, tokenEncryptor).authenticate(credential)
        } finally {
            client.close()
            cookies.close()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
