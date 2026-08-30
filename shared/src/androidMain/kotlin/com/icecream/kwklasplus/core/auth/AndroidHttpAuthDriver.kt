package com.icecream.kwklasplus.core.auth

import com.icecream.kwklasplus.core.network.createKlasHttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

class AndroidHttpAuthDriver(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : WebAuthDriver {
    override suspend fun authenticate(credential: StoredCredential): WebAuthResult {
        val cookies = AcceptAllCookiesStorage()
        val client = createKlasHttpClient(
            engine = OkHttp.create(),
            timeoutMillis = timeoutMillis,
            cookieStorage = cookies,
        )
        return try {
            KlasHttpAuthDriver(client, cookies, AndroidRsaLoginTokenEncryptor)
                .authenticate(credential)
        } finally {
            client.close()
            cookies.close()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}

internal object AndroidRsaLoginTokenEncryptor : LoginTokenEncryptor {
    override fun encrypt(publicKey: String, payload: String): String? = runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKey)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        Base64.getEncoder().encodeToString(cipher.doFinal(payload.encodeToByteArray()))
    }.getOrNull()
}
