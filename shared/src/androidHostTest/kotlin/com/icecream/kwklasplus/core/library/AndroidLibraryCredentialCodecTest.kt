package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.security.SecretValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLibraryCredentialCodecTest {
    private val codec = AndroidLibraryCredentialCodec()

    @Test
    fun base64EncodingMatchesLegacyNoWrapOutput() {
        assertEquals("MDIwMjAxMjM0NTY=", codec.encode("02020123456"))
    }

    @Test
    fun aesCbcZeroIvEncryptionMatchesLegacyFixture() {
        assertEquals(
            "R4lDIBO/32oRLZSjtsPrGQ==",
            codec.encryptPassword(
                SecretValue.of("password"),
                SecretValue.of("0123456789abcdef"),
            ),
        )
    }
}
