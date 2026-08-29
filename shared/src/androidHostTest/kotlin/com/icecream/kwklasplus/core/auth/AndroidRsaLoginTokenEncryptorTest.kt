package com.icecream.kwklasplus.core.auth

import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AndroidRsaLoginTokenEncryptorTest {
    @Test
    fun encryptsJSEncryptCompatiblePkcs1Payload() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val payload = "{\"loginId\":\"2026000000\",\"loginPwd\":\"encrypted\",\"loginTp\":\"MST\"}"

        val encrypted = assertNotNull(AndroidRsaLoginTokenEncryptor.encrypt(publicKey, payload))

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted)).decodeToString()
        assertEquals(payload, decrypted)
    }

    @Test
    fun rejectsMalformedPublicKey() {
        assertEquals(null, AndroidRsaLoginTokenEncryptor.encrypt("not-base64", "payload"))
    }
}
