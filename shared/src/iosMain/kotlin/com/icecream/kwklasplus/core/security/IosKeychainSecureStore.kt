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
import platform.Foundation.NSBundle
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

class IosKeychainStoreException(
    val status: Int? = null,
    override val message: String,
) : IllegalStateException(message)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSecureStore private constructor(
    private val service: String,
    private val academicSessionGroup: String?,
    private val privateAccessGroup: String?,
    private val requireSharedAccessGroup: Boolean,
) : SecureStore {
    constructor(service: String = DEFAULT_SERVICE) : this(
        service,
        academicSessionGroup = null,
        privateAccessGroup = null,
        requireSharedAccessGroup = false,
    )
    override suspend fun read(key: SecureKey): SecretValue? = readNow(key)

    fun readNow(key: SecureKey): SecretValue? {
        if (!canUseSharedGroup(key)) return null
        return readAccount(accountName(key), groupFor(key))
    }

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

    fun writeNow(key: SecureKey, value: SecretValue) {
        writeAccount(accountName(key), value, groupForWrite(key))
    }

    fun writeAccount(account: String, value: SecretValue) = writeAccount(account, value, accessGroup = null)

    fun writeAccount(account: String, value: SecretValue, accessGroup: String?) {
        val status = writeAccountOnce(account, value, accessGroup)
        if (status == errSecSuccess) return
        throw IosKeychainStoreException(status, "Keychain write failed for $account: status=$status")
    }

    private fun writeAccountOnce(account: String, value: SecretValue, itemGroup: String?): Int {
        val data = NSString.create(string = value.reveal())
            .dataUsingEncoding(NSUTF8StringEncoding)
            ?: return ERR_SEC_PARAM
        val attributes = mutableDictionary(capacity = 6) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, account)
            addAccessGroup(itemGroup)
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val addStatus = SecItemAdd(attributes, null)
        if (addStatus == errSecSuccess) return errSecSuccess
        if (addStatus != errSecDuplicateItem) return addStatus
        val query = baseQuery(account, itemGroup)
        val update = mutableDictionary(capacity = 2) {
            addBridged(kSecValueData, data)
            addCf(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        return SecItemUpdate(query, update)
    }

    override suspend fun remove(key: SecureKey) = removeNow(key)

    fun removeNow(key: SecureKey) {
        removeAccount(accountName(key), groupForWrite(key))
    }

    fun removeAccount(account: String) = removeAccount(account, accessGroup = null)

    fun removeAccount(account: String, accessGroup: String?) {
        val status = SecItemDelete(baseQuery(account, accessGroup))
        if (status == errSecSuccess || status == errSecItemNotFound) return
        throw IosKeychainStoreException(status, "Keychain remove failed for $account: status=$status")
    }

    fun accessGroupFor(key: SecureKey): String? =
        if (key in ACADEMIC_SHARED_KEYS) academicSessionGroup else privateAccessGroup

    private fun canUseSharedGroup(key: SecureKey): Boolean =
        key !in ACADEMIC_SHARED_KEYS || !requireSharedAccessGroup || !academicSessionGroup.isNullOrBlank()

    private fun groupFor(key: SecureKey): String? = accessGroupFor(key)

    private fun groupForWrite(key: SecureKey): String? {
        if (key in ACADEMIC_SHARED_KEYS && requireSharedAccessGroup && academicSessionGroup.isNullOrBlank()) {
            throw IosKeychainStoreException(
                message = "academic session Keychain group is unavailable",
            )
        }
        return groupFor(key)
    }

    private fun baseQuery(account: String, accessGroup: String?): CFDictionaryRef =
        mutableDictionary(capacity = 4) {
            addCf(kSecClass, kSecClassGenericPassword)
            addBridged(kSecAttrService, service)
            addBridged(kSecAttrAccount, account)
            addAccessGroup(accessGroup)
        }

    companion object {
        fun withAccessGroups(
            service: String = DEFAULT_SERVICE,
            academicSessionGroup: String?,
            privateAccessGroup: String?,
        ): IosKeychainSecureStore = IosKeychainSecureStore(
            service,
            academicSessionGroup,
            privateAccessGroup,
            requireSharedAccessGroup = true,
        )

        fun withAcademicSessionGroup(
            service: String = DEFAULT_SERVICE,
            accessGroup: String?,
            privateAccessGroup: String? = null,
        ): IosKeychainSecureStore = withAccessGroups(service, accessGroup, privateAccessGroup)

        const val DEFAULT_SERVICE = "com.icecream.kwklasplus.secure"
        const val ACADEMIC_SESSION_GROUP_SUFFIX = "com.icecream.kwklasplus.academic-session"
        val ACADEMIC_SHARED_KEYS = setOf(SecureKey.SESSION_TOKEN, SecureKey.ENCRYPTED_KLAS_PASSWORD)

        fun accountName(key: SecureKey): String = "secret_${key.name.lowercase()}"

        fun resolvedAcademicSessionGroup(): String? {
            val prefix = bundleSeedPrefix() ?: return null
            val group = "$prefix$ACADEMIC_SESSION_GROUP_SUFFIX"
            return group.takeIf { accessGroupIsWritable(group) }
        }

        fun resolvedPrivateAccessGroup(): String? {
            val prefix = bundleSeedPrefix() ?: return null
            val bundleId = NSBundle.mainBundle.bundleIdentifier?.takeIf { it.isNotBlank() } ?: return null
            val group = "$prefix$bundleId"
            return group.takeIf { accessGroupIsWritable(group) }
        }

        private fun accessGroupIsWritable(group: String): Boolean {
            val probe = IosKeychainSecureStore(service = "com.icecream.kwklasplus.academic-session.probe")
            if (probe.writeAccountOnce("probe", SecretValue.of("1"), group) != errSecSuccess) return false
            return runCatching { probe.removeAccount("probe", group) }.isSuccess
        }

        private const val ERR_SEC_PARAM: Int = -50
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
