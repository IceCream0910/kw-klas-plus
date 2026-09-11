package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class IosLibraryCredentialCodec : LibraryCredentialCodec {
    override fun encode(value: String): String = Base64.encode(value.encodeToByteArray())

    override fun encryptPassword(password: SecretValue, secret: SecretValue): String {
        val key = secret.reveal().encodeToByteArray()
        val data = password.reveal().encodeToByteArray()
        val blockSize = kCCBlockSizeAES128.toInt()
        val out = ByteArray(data.size + blockSize)
        val iv = ByteArray(blockSize)
        val written = memScoped {
            val moved = alloc<size_tVar>()
            val status = key.usePinned { keyPinned ->
                data.usePinned { dataPinned ->
                    out.usePinned { outPinned ->
                        iv.usePinned { ivPinned ->
                            CCCrypt(
                                kCCEncrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                keyPinned.addressOf(0),
                                key.size.convert(),
                                ivPinned.addressOf(0),
                                dataPinned.addressOf(0),
                                data.size.convert(),
                                outPinned.addressOf(0),
                                out.size.convert(),
                                moved.ptr,
                            )
                        }
                    }
                }
            }
            check(status == kCCSuccess) { "AES encrypt failed: status=$status" }
            moved.value.toInt()
        }
        return Base64.encode(out.copyOf(written))
    }
}
