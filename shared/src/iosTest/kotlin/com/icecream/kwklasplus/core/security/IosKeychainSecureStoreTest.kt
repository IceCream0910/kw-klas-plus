package com.icecream.kwklasplus.core.security

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.session.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosKeychainSecureStoreTest {
    @Test
    fun readWriteRemoveRoundTrip() = runSuspendTest {
        val store = IosKeychainSecureStore(service = "com.icecream.kwklasplus.test.keychain.${hashCode()}")
        val key = SecureKey.ENCRYPTED_KLAS_PASSWORD

        // Kotlin/Native 테스트 바이너리에는 securityd가 없어 Keychain이 불가할 수 있음 (status=-25291)
        // 앱 프로세스 검증은 IosAuthSecurityTests에서 수행
        try {
            store.remove(key)
        } catch (error: IllegalStateException) {
            if (error.message?.contains("status=-25291") == true) return@runSuspendTest
            throw error
        }

        assertNull(store.read(key))

        store.write(key, SecretValue.of("secret-value"))
        assertEquals(SecretValue.of("secret-value"), store.read(key))

        store.write(key, SecretValue.of("updated-value"))
        assertEquals(SecretValue.of("updated-value"), store.read(key))

        store.remove(key)
        assertNull(store.read(key))
    }
}
