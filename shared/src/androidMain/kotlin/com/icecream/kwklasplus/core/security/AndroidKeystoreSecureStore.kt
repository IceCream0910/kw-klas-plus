package com.icecream.kwklasplus.core.security

import android.content.Context
import android.util.Base64
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class AndroidKeystoreSecureStore(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecureStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun read(key: SecureKey): SecretValue? = readNow(key)

    fun readNow(key: SecureKey): SecretValue? {
        val encoded = preferences.getString(key.storageKey, null) ?: return null
        val parts = encoded.split(SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_VERSION)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
        )
        cipher.updateAAD(key.storageKey.encodeToByteArray())
        val plaintext = cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
        return SecretValue.of(plaintext.decodeToString())
    }

    override suspend fun write(key: SecureKey, value: SecretValue) = writeNow(key, value)

    fun writeNow(key: SecureKey, value: SecretValue) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(key.storageKey.encodeToByteArray())
        val ciphertext = cipher.doFinal(value.reveal().encodeToByteArray())
        val encoded = listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(SEPARATOR)
        check(preferences.edit().putString(key.storageKey, encoded).commit())
    }

    override suspend fun remove(key: SecureKey) = removeNow(key)

    fun removeNow(key: SecureKey) {
        check(preferences.edit().remove(key.storageKey).commit())
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private val SecureKey.storageKey: String
        get() = "secret_${name.lowercase()}"

    companion object {
        const val PREFERENCES_NAME = "kmp_secure_store_v1"
        const val DEFAULT_KEY_ALIAS = "kw_klas_plus_kmp_secure_store_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val FORMAT_VERSION = "1"
        private const val SEPARATOR = ":"
    }
}
