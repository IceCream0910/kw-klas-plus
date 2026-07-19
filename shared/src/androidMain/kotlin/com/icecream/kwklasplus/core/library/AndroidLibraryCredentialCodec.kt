package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AndroidLibraryCredentialCodec : LibraryCredentialCodec {
    override fun encode(value: String): String = Base64.getEncoder().encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
    )

    override fun encryptPassword(password: SecretValue, secret: SecretValue): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(secret.reveal().toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(ByteArray(16)),
        )
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(password.reveal().toByteArray(StandardCharsets.UTF_8)),
        )
    }
}
