package com.icecream.kwklasplus.core.lock

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosAppLockCredentialCodecTest {
    private val codec = IosAppLockCredentialCodec()

    @Test
    fun hashMatchesReleasedSha256PasswordSaltFormat() {
        assertEquals(
            "eje4XIkY6sGakInA+loqtNzj+QUo3N7sEIsj3fNge5k=",
            codec.hash("password", "salt"),
        )
        assertTrue(codec.verify("password", codec.hash("password", "salt"), "salt"))
        assertFalse(codec.verify("wrong", codec.hash("password", "salt"), "salt"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun generatedSaltKeepsLegacySixteenByteBase64Format() {
        assertEquals(16, Base64.decode(codec.generateSalt()).size)
    }
}
