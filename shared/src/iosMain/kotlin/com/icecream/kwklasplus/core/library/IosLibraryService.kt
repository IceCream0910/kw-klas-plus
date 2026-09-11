package com.icecream.kwklasplus.core.library

import com.icecream.kwklasplus.core.legacy.LegacyPreferenceKeys
import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import com.icecream.kwklasplus.core.security.IosKeychainSecureStore
import com.icecream.kwklasplus.core.security.SecretValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

class IosKeychainLibraryAccountSecrets(
    private val keychain: IosKeychainSecureStore,
) : LibraryAccountSecretStore {
    override fun readAccount(account: String): SecretValue? = keychain.readAccount(account)

    override fun writeAccount(account: String, value: SecretValue) {
        keychain.writeAccount(account, value)
    }

    override fun removeAccount(account: String) {
        keychain.removeAccount(account)
    }
}

class IosLibraryService(
    gateway: LibraryGateway,
    private val defaults: NSUserDefaults,
    private val secureStore: SecureStore,
    private val cache: IosLibrarySessionCache,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val repository = LibraryRepository(
        gateway = gateway,
        codec = IosLibraryCredentialCodec(),
        cache = cache,
    )
    private var cachedPassword: String? = null

    fun hasConfiguredCredentials(): Boolean {
        val student = storedStudentNumber()
        val phone = storedPhoneNumber()
        val password = passwordNow()
        return !student.isNullOrBlank() && !phone.isNullOrBlank() && !password.isNullOrBlank()
    }

    fun settingsStudentNumber(): String =
        storedStudentNumber()?.takeIf { it.isNotBlank() }
            ?: defaults.stringForKey(LegacyPreferenceKeys.KW_ID).orEmpty()

    fun settingsPhoneNumber(): String = storedPhoneNumber().orEmpty()

    fun settingsPassword(): String = passwordNow().orEmpty()

    suspend fun saveCredentials(
        studentNumber: String,
        phoneNumber: String,
        password: String,
    ) {
        persistCredentials(studentNumber, phoneNumber, password)
    }

    fun saveCredentials(
        studentNumber: String,
        phoneNumber: String,
        password: String,
        onDone: () -> Unit,
    ) {
        scope.launch {
            saveCredentials(studentNumber, phoneNumber, password)
            onDone()
        }
    }

    fun getLibraryQrData(onResult: (LibraryQrResult) -> Unit) {
        scope.launch {
            onResult(fetchQrData())
        }
    }

    fun clearCache(onDone: () -> Unit) {
        scope.launch {
            clearSessionCache()
            onDone()
        }
    }

    suspend fun clearAll() {
        clearSessionCache()
        cachedPassword = null
        runCatching { secureStore.remove(SecureKey.LIBRARY_PASSWORD) }
        defaults.removeObjectForKey(LegacyPreferenceKeys.LIBRARY_STD_NUMBER)
        defaults.removeObjectForKey(LegacyPreferenceKeys.LIBRARY_PHONE)
        defaults.synchronize()
    }

    private suspend fun persistCredentials(
        studentNumber: String,
        phoneNumber: String,
        password: String,
    ) {
        clearSessionCache()
        defaults.setObject(studentNumber, LegacyPreferenceKeys.LIBRARY_STD_NUMBER)
        defaults.setObject(phoneNumber, LegacyPreferenceKeys.LIBRARY_PHONE)
        defaults.synchronize()
        secureStore.write(SecureKey.LIBRARY_PASSWORD, SecretValue.of(password))
        cachedPassword = password
    }

    private suspend fun fetchQrData(): LibraryQrResult {
        val credentials = loadCredentials() ?: return LibraryQrResult.InvalidResponse
        return try {
            repository.getQrData(credentials)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            LibraryQrResult.InvalidResponse
        }
    }

    private suspend fun clearSessionCache() {
        val credentials = loadCredentials()
        if (credentials != null) {
            cache.clear(LibraryCacheIdentity.from(credentials))
        } else {
            cache.clearTracked()
        }
    }

    private suspend fun loadCredentials(): LibraryCredentials? {
        val student = storedStudentNumber()?.takeIf { it.isNotBlank() } ?: return null
        val phone = storedPhoneNumber()?.takeIf { it.isNotBlank() } ?: return null
        val password = secureStore.read(SecureKey.LIBRARY_PASSWORD)?.reveal()
            ?: cachedPassword
            ?: return null
        if (password.isBlank()) return null
        cachedPassword = password
        return LibraryCredentials(student, phone, SecretValue.of(password))
    }

    private fun storedStudentNumber(): String? =
        defaults.stringForKey(LegacyPreferenceKeys.LIBRARY_STD_NUMBER)?.takeIf { it.isNotBlank() }

    private fun storedPhoneNumber(): String? =
        defaults.stringForKey(LegacyPreferenceKeys.LIBRARY_PHONE)?.takeIf { it.isNotBlank() }

    private fun passwordNow(): String? {
        val keychain = secureStore as? IosKeychainSecureStore
        val stored = keychain?.readNow(SecureKey.LIBRARY_PASSWORD)?.reveal()
        if (!stored.isNullOrBlank()) {
            cachedPassword = stored
            return stored
        }
        return cachedPassword?.takeIf { it.isNotBlank() }
    }
}
