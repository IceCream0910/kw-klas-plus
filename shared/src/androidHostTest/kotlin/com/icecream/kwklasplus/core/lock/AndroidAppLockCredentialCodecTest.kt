package com.icecream.kwklasplus.core.lock

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAppLockCredentialCodecTest {
    private val codec = AndroidAppLockCredentialCodec()

    @Test
    fun hashMatchesReleasedSha256PasswordSaltFormat() {
        assertEquals(
            "eje4XIkY6sGakInA+loqtNzj+QUo3N7sEIsj3fNge5k=",
            codec.hash("password", "salt"),
        )
        assertTrue(codec.verify("password", codec.hash("password", "salt"), "salt"))
        assertFalse(codec.verify("wrong", codec.hash("password", "salt"), "salt"))
    }

    @Test
    fun generatedSaltKeepsLegacySixteenByteBase64Format() {
        assertEquals(16, Base64.getDecoder().decode(codec.generateSalt()).size)
    }
}
