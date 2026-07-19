package com.icecream.kwklasplus

import androidx.test.core.app.ApplicationProvider
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.security.AndroidKeystoreSecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AndroidKeystoreSecureStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val alias = "kw_klas_plus_test_secure_store"
    private lateinit var store: AndroidKeystoreSecureStore

    @Before
    fun setUp() {
        context.getSharedPreferences(
            AndroidKeystoreSecureStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        ).edit().clear().commit()
        deleteKey()
        store = AndroidKeystoreSecureStore(context, alias)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(
            AndroidKeystoreSecureStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        ).edit().clear().commit()
        deleteKey()
    }

    @Test
    fun writeReadAndRemoveRoundTrip() = runBlocking {
        val value = SecretValue.of("encrypted-password")

        store.write(SecureKey.ENCRYPTED_KLAS_PASSWORD, value)

        assertEquals(value, store.read(SecureKey.ENCRYPTED_KLAS_PASSWORD))
        assertNull(store.read(SecureKey.SESSION_TOKEN))

        store.remove(SecureKey.ENCRYPTED_KLAS_PASSWORD)
        assertNull(store.read(SecureKey.ENCRYPTED_KLAS_PASSWORD))
    }

    private fun deleteKey() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }
}
