package com.icecream.kwklasplus.core.platform

sealed interface FileTransferValidation {
    data class Accepted(val request: FileTransferRequest) : FileTransferValidation
    data object Rejected : FileTransferValidation
}

class FileTransferPolicy(
    private val navigationPolicy: ExternalNavigationPolicy = ExternalNavigationPolicy(),
) {
    fun validate(request: FileTransferRequest): FileTransferValidation {
        val destination = navigationPolicy.resolve(request.url)
        if (destination !is ExternalNavigationResolution.Allowed ||
            destination.destination !is ExternalDestination.Web
        ) {
            return FileTransferValidation.Rejected
        }
        val sanitizedName = request.suggestedFileName
            ?.takeIf { it.isNotBlank() }
            ?.map { character -> if (character in INVALID_FILE_NAME_CHARACTERS || character.isISOControl()) '_' else character }
            ?.joinToString("")
            ?.trim()
            ?.take(180)
            ?.takeIf(String::isNotEmpty)
        val mimeType = request.mimeType
            ?.trim()
            ?.takeIf { '/' in it && !it.any(Char::isISOControl) }
        return FileTransferValidation.Accepted(
            request.copy(
                suggestedFileName = sanitizedName,
                mimeType = mimeType,
                userAgent = request.userAgent?.takeIf { !it.any(Char::isISOControl) },
                contentDisposition = request.contentDisposition?.takeIf { !it.any(Char::isISOControl) },
            ),
        )
    }

    private companion object {
        const val INVALID_FILE_NAME_CHARACTERS = "\\/:*?\"<>|"
    }
}
