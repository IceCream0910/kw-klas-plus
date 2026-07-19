package com.icecream.kwklasplus.core.platform

import com.icecream.kwklasplus.core.security.SecretValue

enum class SecureKey {
    ENCRYPTED_KLAS_PASSWORD,
    SESSION_TOKEN,
    LIBRARY_PASSWORD,
    LIBRARY_SECRET,
    LIBRARY_AUTH_KEY,
    APP_LOCK_HASH,
    APP_LOCK_SALT,
}

interface SecureStore {
    suspend fun read(key: SecureKey): SecretValue?
    suspend fun write(key: SecureKey, value: SecretValue)
    suspend fun remove(key: SecureKey)
}

enum class PreferenceKey {
    ACCOUNT_ID,
    APP_THEME,
    YEAR_SEMESTER,
    YEAR_SEMESTER_LIST,
    APP_LOCK_ENABLED,
    BIOMETRIC_ENABLED,
}

interface PreferencesStore {
    suspend fun read(key: PreferenceKey): String?
    suspend fun write(key: PreferenceKey, value: String)
    suspend fun remove(key: PreferenceKey)
}

enum class PlatformCapability {
    BIOMETRICS,
    QR_SCANNER,
    PICTURE_IN_PICTURE,
    FILE_PICKER,
    DOWNLOAD,
    HOME_WIDGET,
    HAPTICS,
}

enum class CapabilityAvailability {
    AVAILABLE,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
}

fun interface PlatformCapabilities {
    fun availability(capability: PlatformCapability): CapabilityAvailability
}

sealed interface ExternalDestination {
    data class Web(val url: String) : ExternalDestination
    data class Email(val address: String) : ExternalDestination
    data class Telephone(val number: String) : ExternalDestination
    data class PlatformUri(val uri: String) : ExternalDestination
}

fun interface ExternalNavigator {
    suspend fun open(destination: ExternalDestination): PlatformActionResult
}

enum class BiometricPurpose {
    UNLOCK_APP,
    ENABLE_BIOMETRICS,
    DISABLE_APP_LOCK,
}

fun interface Biometrics {
    suspend fun authenticate(purpose: BiometricPurpose): PlatformActionResult
}

enum class HapticEffect {
    SELECTION,
    CONFIRM,
    REJECT,
    LONG_PRESS,
}

fun interface Haptics {
    fun perform(effect: HapticEffect): PlatformActionResult
}

data class FileTransferRequest(
    val url: String,
    val suggestedFileName: String?,
    val mimeType: String?,
    val userAgent: String? = null,
    val contentDisposition: String? = null,
)

fun interface FileTransfer {
    suspend fun download(request: FileTransferRequest): PlatformActionResult
}

data class FilePickerRequest(
    val acceptedMimeTypes: List<String> = emptyList(),
    val allowMultiple: Boolean = false,
) {
    fun normalizedMimeTypes(): List<String> = acceptedMimeTypes
        .map(String::trim)
        .filter { it.isNotEmpty() && '/' in it && !it.any(Char::isISOControl) }
        .distinct()
}

sealed interface FilePickerResult {
    data class Selected(val references: List<String>) : FilePickerResult
    data object Cancelled : FilePickerResult
    data object Unsupported : FilePickerResult
    data class Failed(val reason: String) : FilePickerResult
}

fun interface FilePicker {
    suspend fun pick(request: FilePickerRequest): FilePickerResult
}

sealed interface QrScanResult {
    data class Success(val value: String) : QrScanResult
    data object Cancelled : QrScanResult
    data object PermissionRequired : QrScanResult
    data class Failed(val reason: String) : QrScanResult
}

fun interface QrScanner {
    suspend fun scan(): QrScanResult
}

data class PictureInPictureState(
    val isPlaying: Boolean,
    val aspectRatioWidth: Int,
    val aspectRatioHeight: Int,
)

interface PictureInPicture {
    suspend fun enter(state: PictureInPictureState): PlatformActionResult
    suspend fun exit(): PlatformActionResult
}

sealed interface PlatformActionResult {
    data object Success : PlatformActionResult
    data object Cancelled : PlatformActionResult
    data object PermissionRequired : PlatformActionResult
    data object Unsupported : PlatformActionResult
    data class Failed(val reason: String) : PlatformActionResult
}
