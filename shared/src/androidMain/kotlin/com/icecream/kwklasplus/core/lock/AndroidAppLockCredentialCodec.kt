package com.icecream.kwklasplus.core.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AndroidAppLockCredentialCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) : AppLockCredentialCodec {
    override fun generateSalt(): String {
        val salt = ByteArray(SALT_SIZE_BYTES)
        secureRandom.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    override fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hash = digest.digest((password + salt).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val SALT_SIZE_BYTES = 16
    }
}
