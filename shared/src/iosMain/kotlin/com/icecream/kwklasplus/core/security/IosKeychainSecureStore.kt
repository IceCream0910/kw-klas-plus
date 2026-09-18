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
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Foundation.NSDictionary

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSecureStore private constructor(
    private val service: String,
    private val accessGroup: String?,
) : SecureStore {
    constructor(service: String = DEFAULT_SERVICE) : this(service, accessGroup = null)
    override suspend fun read(key: SecureKey): SecretValue? = readNow(key)

    fun readNow(key: SecureKey): SecretValue? = readAccount(accountName(key), groupFor(key))

    fun readAccount(account: String): SecretValue? = readAccount(account, accessGroup = null)

    fun readAccount(account: String, accessGroup: String?): SecretValue? {
        val query = mutableDictionary(capacity = 6) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, account)
            addAccessGroup(accessGroup)
            addCf(kSecReturnData, kCFBooleanTrue)
            addCf(kSecMatchLimit, kSecMatchLimitOne)
        }
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            when {
                status == errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData ?: return null
                    val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: return null
                    SecretValue.of(text)
                }
                else -> null
            }
        }
    }

    override suspend fun write(key: SecureKey, value: SecretValue) = writeNow(key, value)

    fun writeNow(key: SecureKey, value: SecretValue) = writeAccount(accountName(key), value, groupFor(key))

    fun writeAccount(account: String, value: SecretValue) = writeAccount(account, value, accessGroup = null)

    fun writeAccount(account: String, value: SecretValue, accessGroup: String?) {
        if (writeAccountOnce(account, value, accessGroup)) return
    }

    private fun writeAccountOnce(account: String, value: SecretValue, itemGroup: String?): Boolean {
        val data = NSString.create(string = value.reveal())
            .dataUsingEncoding(NSUTF8StringEncoding)
            ?: return false
        val attributes = mutableDictionary(capacity = 6) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, account)
            addAccessGroup(itemGroup)
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val addStatus = SecItemAdd(attributes, null)
        if (addStatus == errSecSuccess) return true
        if (isUnavailableStatus(addStatus)) return false
        if (addStatus != errSecDuplicateItem) return false
        val query = baseQuery(account, itemGroup)
        val update = mutableDictionary(capacity = 2) {
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        return SecItemUpdate(query, update) == errSecSuccess
    }

    override suspend fun remove(key: SecureKey) = removeNow(key)

    fun removeNow(key: SecureKey) = removeAccount(accountName(key), groupFor(key))

    fun removeAccount(account: String) = removeAccount(account, accessGroup = null)

    fun removeAccount(account: String, accessGroup: String?) {
        SecItemDelete(baseQuery(account, accessGroup))
    }

    private fun groupFor(key: SecureKey): String? =
        accessGroup.takeIf { key in ACADEMIC_SHARED_KEYS }

    private fun baseQuery(account: String, accessGroup: String?): CFDictionaryRef =
        mutableDictionary(capacity = 4) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, account)
            addAccessGroup(accessGroup)
        }

    companion object {
        fun withAcademicSessionGroup(
            service: String = DEFAULT_SERVICE,
            accessGroup: String?,
        ): IosKeychainSecureStore = IosKeychainSecureStore(service, accessGroup)

        const val DEFAULT_SERVICE = "com.icecream.kwklasplus.secure"
        const val ACADEMIC_SESSION_GROUP_SUFFIX = "com.icecream.kwklasplus.academic-session"
        val ACADEMIC_SHARED_KEYS = setOf(SecureKey.SESSION_TOKEN, SecureKey.ENCRYPTED_KLAS_PASSWORD)

        fun accountName(key: SecureKey): String = "secret_${key.name.lowercase()}"

        fun resolvedAcademicSessionGroup(): String? {
            val prefix = bundleSeedPrefix() ?: return null
            val group = "$prefix$ACADEMIC_SESSION_GROUP_SUFFIX"
            return group.takeIf { accessGroupIsWritable(group) }
        }

        private fun accessGroupIsWritable(group: String): Boolean {
            val probe = IosKeychainSecureStore(service = "com.icecream.kwklasplus.academic-session.probe")
            if (!probe.writeAccountOnce("probe", SecretValue.of("1"), group)) return false
            probe.removeAccount("probe", group)
            return true
        }

        private const val ERR_SEC_INTERACTION_NOT_ALLOWED: Int = -25308
        private const val ERR_SEC_MISSING_ENTITLEMENT: Int = -34018
        private const val ERR_SEC_NOT_AVAILABLE: Int = -25291

        fun isUnavailableStatus(status: Int): Boolean =
            status == errSecItemNotFound ||
                status == ERR_SEC_INTERACTION_NOT_ALLOWED ||
                status == ERR_SEC_MISSING_ENTITLEMENT ||
                status == ERR_SEC_NOT_AVAILABLE
    }
}

private val SecureKey.accountName: String
    get() = IosKeychainSecureStore.accountName(this)

@OptIn(ExperimentalForeignApi::class)
private fun bundleSeedPrefix(): String? {
    val query = mutableDictionary(capacity = 5) {
        addCf(kSecClass, kSecClassGenericPassword)
        addBridged(kSecAttrAccount, "bundleSeedID")
        addBridged(kSecAttrService, "com.icecream.kwklasplus.bundle-seed")
        addCf(kSecReturnAttributes, kCFBooleanTrue)
        addCf(kSecMatchLimit, kSecMatchLimitOne)
    }
    return memScoped {
        val result = alloc<CFTypeRefVar>()
        var status = SecItemCopyMatching(query, result.ptr)
        if (status == errSecItemNotFound) {
            val attributes = mutableDictionary(capacity = 4) {
                addCf(kSecClass, kSecClassGenericPassword)
                addBridged(kSecAttrAccount, "bundleSeedID")
                addBridged(kSecAttrService, "com.icecream.kwklasplus.bundle-seed")
                addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            }
            status = SecItemAdd(attributes, result.ptr)
            if (status == errSecSuccess) {
                status = SecItemCopyMatching(query, result.ptr)
            }
        }
        if (status != errSecSuccess) return@memScoped null
        val attrs = CFBridgingRelease(result.value) as? NSDictionary ?: return@memScoped null
        val group = attrs.objectForKey("agrp") as? String
            ?: attrs.objectForKey(kSecAttrAccessGroup) as? String
            ?: return@memScoped null
        val team = group.substringBefore('.', missingDelimiterValue = "")
        if (team.isBlank()) null else "$team."
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

    fun addAccessGroup(accessGroup: String?) {
        if (!accessGroup.isNullOrBlank()) {
            addBridged(kSecAttrAccessGroup, accessGroup)
        }
    }
}
