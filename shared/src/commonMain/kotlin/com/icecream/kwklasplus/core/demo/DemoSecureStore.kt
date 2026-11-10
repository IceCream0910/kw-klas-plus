package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.SecretValue

class DemoSecureStore : SecureStore {
    private val values = mutableMapOf<SecureKey, SecretValue>()
    override suspend fun read(key: SecureKey): SecretValue? = values[key]
    override suspend fun write(key: SecureKey, value: SecretValue) { values[key] = value }
    override suspend fun remove(key: SecureKey) { values.remove(key) }
}
