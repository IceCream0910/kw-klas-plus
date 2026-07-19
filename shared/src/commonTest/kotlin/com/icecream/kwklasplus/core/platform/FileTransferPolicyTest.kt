package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FileTransferPolicyTest {
    private val policy = FileTransferPolicy()

    @Test
    fun validatesWebDownloadAndSanitizesMetadata() {
        val result = policy.validate(
            FileTransferRequest(
                url = "https://klas.kw.ac.kr/file?id=1",
                suggestedFileName = " report:2026?.pdf ",
                mimeType = " application/pdf ",
                userAgent = "KLAS Android",
            ),
        )

        val accepted = assertIs<FileTransferValidation.Accepted>(result)
        assertEquals("report_2026_.pdf", accepted.request.suggestedFileName)
        assertEquals("application/pdf", accepted.request.mimeType)
    }

    @Test
    fun rejectsNonWebAndControlCharacterUrls() {
        listOf("file:///data/file", "javascript:download()", "https://example.com\nfile:///x").forEach { url ->
            assertIs<FileTransferValidation.Rejected>(
                policy.validate(FileTransferRequest(url, null, null)),
            )
        }
    }
}
