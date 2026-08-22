package com.icecream.kwklasplus.core.platform

sealed interface FileTransferValidation {
    data class Accepted(val request: FileTransferRequest) : FileTransferValidation
    data object Rejected : FileTransferValidation
}

class FileTransferPolicy(
    private val navigationPolicy: ExternalNavigationPolicy = ExternalNavigationPolicy(
        maximumLength = DOWNLOAD_URL_MAXIMUM_LENGTH,
    ),
) {
    fun validate(request: FileTransferRequest): FileTransferValidation {
        val destination = navigationPolicy.resolve(request.url)
        if (destination !is ExternalNavigationResolution.Allowed ||
            destination.destination !is ExternalDestination.Web
        ) {
            return FileTransferValidation.Rejected
        }
        return FileTransferValidation.Accepted(
            request.copy(
                suggestedFileName = sanitizeDownloadFileName(request.suggestedFileName),
                mimeType = request.mimeType
                    ?.trim()
                    ?.takeIf { '/' in it && !it.any(Char::isISOControl) },
                userAgent = request.userAgent?.takeIf { !it.any(Char::isISOControl) },
                contentDisposition = request.contentDisposition?.takeIf { !it.any(Char::isISOControl) },
            ),
        )
    }

    fun shouldTreatAsDownload(
        mimeType: String?,
        contentDisposition: String?,
        canShowMimeType: Boolean,
        url: String? = null,
    ): Boolean {
        // PDF는 iOS에서 인라인으로 연다. attachment/octet-stream이어도 공유 시트로 보내지 않는다.
        if (DownloadMetadata.looksLikePdf(mimeType, contentDisposition, url)) return false
        val dispositionType = contentDisposition
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (dispositionType == "attachment") return true
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (mime != null && mime in SAVEABLE_DOCUMENT_MIME_TYPES) return true
        return !canShowMimeType
    }

    companion object {
        fun create(): FileTransferPolicy = FileTransferPolicy()
    }
}

fun sanitizeDownloadFileName(name: String?): String? = name
    ?.takeIf { it.isNotBlank() }
    ?.map { character -> if (character in INVALID_DOWNLOAD_FILE_NAME_CHARACTERS || character.isISOControl()) '_' else character }
    ?.joinToString("")
    ?.trim()
    ?.take(180)
    ?.takeIf(String::isNotEmpty)

private const val INVALID_DOWNLOAD_FILE_NAME_CHARACTERS = "\\/:*?\"<>|"
private const val DOWNLOAD_URL_MAXIMUM_LENGTH = 16_384

// 웹뷰가 인라인 렌더하지 못하는 문서 타입은 canShow/attachment 여부와 무관하게 다운로드로 처리한다.
// PDF는 파일명·MIME으로 따로 식별해 인라인 표시한다.
private val SAVEABLE_DOCUMENT_MIME_TYPES = setOf(
    "application/x-hwp",
    "application/haansoft-hwpx",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/zip",
)
