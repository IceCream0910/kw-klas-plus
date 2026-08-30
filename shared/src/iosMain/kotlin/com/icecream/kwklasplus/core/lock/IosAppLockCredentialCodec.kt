package com.icecream.kwklasplus.core.lock

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class IosAppLockCredentialCodec : AppLockCredentialCodec {
    override fun generateSalt(): String {
        val salt = ByteArray(SALT_SIZE_BYTES)
        val status = salt.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, SALT_SIZE_BYTES.convert(), pinned.addressOf(0))
        }
        check(status == errSecSuccess) { "Failed to generate app lock salt: status=$status" }
        return Base64.encode(salt)
    }

    override fun hash(password: String, salt: String): String {
        val input = (password + salt).encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { inputPinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(
                    inputPinned.addressOf(0),
                    input.size.convert(),
                    digestPinned.addressOf(0),
                )
            }
        }
        return Base64.encode(digest.asByteArray())
    }

    private companion object {
        const val SALT_SIZE_BYTES = 16
    }
}
