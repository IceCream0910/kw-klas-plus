package com.icecream.kwklasplus.core.platform

object DownloadMetadata {
    fun resolvedFileName(request: FileTransferRequest): String {
        val guessed = request.suggestedFileName
            ?: parseContentDispositionFileName(request.contentDisposition)
            ?: fileNameFromUrl(request.url)
            ?: "download"
        return sanitizeDownloadFileName(ensureExtension(guessed, request.mimeType)) ?: "download"
    }

    fun looksLikePdf(mimeType: String?, contentDisposition: String?, url: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (mime == "application/pdf") return true
        val name = parseContentDispositionFileName(contentDisposition) ?: url?.let(::fileNameFromUrl)
        return name != null && extensionOf(name) == "pdf"
    }

    fun resolvedMimeType(fileNameOrUrl: String, mimeType: String?): String {
        val trimmed = mimeType?.trim()?.takeIf { it.isNotEmpty() }
        if (!trimmed.isNullOrEmpty() && trimmed != "application/octet-stream") {
            return trimmed
        }
        val extension = extensionOf(fileNameOrUrl)
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank() || header.any(Char::isISOControl)) return null
        RFC5987_FILE_NAME.find(header)?.let { match ->
            return percentDecodeUtf8(match.groupValues[1].trim().trim('"'))
                .takeIf { it.isNotBlank() }
        }
        QUOTED_FILE_NAME.find(header)?.let { match ->
            return match.groupValues[1].takeIf { it.isNotBlank() }
        }
        BARE_FILE_NAME.find(header)?.let { match ->
            return match.groupValues[1].trim().trim('"').takeIf { it.isNotBlank() }
        }
        return null
    }

    internal fun fileNameFromUrl(url: String): String? {
        val path = url.substringAfter("://", missingDelimiterValue = url)
            .substringAfter('/')
            .substringBefore('?')
            .substringBefore('#')
        val last = path.substringAfterLast('/').trim()
        return last.takeIf { it.isNotEmpty() }
    }

    private fun ensureExtension(fileName: String, mimeType: String?): String {
        if (extensionOf(fileName).isNotEmpty()) return fileName
        val extension = EXTENSION_BY_MIME[mimeType?.trim()?.lowercase()] ?: return fileName
        return "$fileName.$extension"
    }

    private fun extensionOf(fileNameOrUrl: String): String {
        val last = fileNameOrUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if ('.' !in last) return ""
        return last.substringAfterLast('.').lowercase()
    }

    private fun percentDecodeUtf8(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '%' && index + 2 < value.length) {
                val parsed = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (parsed != null) {
                    bytes.add(parsed.toByte())
                    index += 3
                    continue
                }
            }
            bytes.add(current.code.toByte())
            index += 1
        }
        return bytes.toByteArray().decodeToString()
    }

    private val RFC5987_FILE_NAME =
        Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
    private val QUOTED_FILE_NAME =
        Regex("""filename\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val BARE_FILE_NAME =
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)

    private val MIME_BY_EXTENSION = mapOf(
        "pdf" to "application/pdf",
        "hwp" to "application/x-hwp",
        "hwpx" to "application/haansoft-hwpx",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "txt" to "text/plain",
        "csv" to "text/csv",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "zip" to "application/zip",
    )

    private val EXTENSION_BY_MIME = MIME_BY_EXTENSION.entries
        .groupBy({ it.value }, { it.key })
        .mapValues { (_, extensions) -> extensions.first() }
}
