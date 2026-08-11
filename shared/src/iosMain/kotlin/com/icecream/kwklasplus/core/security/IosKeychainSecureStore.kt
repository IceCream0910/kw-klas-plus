package com.icecream.kwklasplus.core.security

import com.icecream.kwklasplus.core.platform.SecureKey
import com.icecream.kwklasplus.core.platform.SecureStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSecureStore(
    private val service: String = DEFAULT_SERVICE,
) : SecureStore {
    override suspend fun read(key: SecureKey): SecretValue? {
        val query = mutableDictionary(capacity = 5) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, key.account)
            addCf(kSecReturnData, kCFBooleanTrue)
            addCf(kSecMatchLimit, kSecMatchLimitOne)
        }
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            when (status) {
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData ?: return null
                    val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: return null
                    SecretValue.of(text)
                }
                errSecItemNotFound -> null
                else -> error("Keychain read failed for ${key.name}: status=$status")
            }
        }
    }

    override suspend fun write(key: SecureKey, value: SecretValue) {
        val data = NSString.create(string = value.reveal())
            .dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Keychain write encoding failed for ${key.name}")
        val attributes = mutableDictionary(capacity = 5) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, key.account)
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val addStatus = SecItemAdd(attributes, null)
        if (addStatus == errSecSuccess) return
        check(addStatus == errSecDuplicateItem) {
            "Keychain write failed for ${key.name}: status=$addStatus"
        }

        val query = baseQuery(key)
        val update = mutableDictionary(capacity = 2) {
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val updateStatus = SecItemUpdate(query, update)
        check(updateStatus == errSecSuccess) {
            "Keychain update failed for ${key.name}: status=$updateStatus"
        }
    }

    override suspend fun remove(key: SecureKey) {
        val status = SecItemDelete(baseQuery(key))
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain remove failed for ${key.name}: status=$status"
        }
    }

    private fun baseQuery(key: SecureKey): CFDictionaryRef =
        mutableDictionary(capacity = 3) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, key.account)
        }

    private val SecureKey.account: String
        get() = "secret_${name.lowercase()}"

    companion object {
        const val DEFAULT_SERVICE = "com.icecream.kwklasplus.secure"
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun mutableDictionary(
    capacity: Int,
    builder: MutableCFDictionary.() -> Unit,
): CFDictionaryRef {
    val dictionary = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        capacity.toLong(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )
    MutableCFDictionary(requireNotNull(dictionary)).builder()
    return dictionary
}

@OptIn(ExperimentalForeignApi::class)
private class MutableCFDictionary(private val dictionary: CFDictionaryRef) {
    fun addCf(key: CFTypeRef?, value: CFTypeRef?) {
        CFDictionaryAddValue(dictionary, key, value)
    }

    fun addBridged(key: CFTypeRef?, value: Any?) {
        CFDictionaryAddValue(dictionary, key, CFBridgingRetain(value))
    }
}
