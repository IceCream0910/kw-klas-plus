package com.icecream.kwklasplus.core.lock

interface AppLockCredentialCodec {
    fun generateSalt(): String
    fun hash(password: String, salt: String): String

    fun verify(password: String, savedHash: String, savedSalt: String): Boolean =
        hash(password, savedSalt) == savedHash
}
