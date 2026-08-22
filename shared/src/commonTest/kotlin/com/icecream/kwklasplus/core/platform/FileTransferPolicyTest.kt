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
    fun acceptsLongHttpsDownloadUrls() {
        val query = "enc=" + "a".repeat(4_000)
        val result = policy.validate(
            FileTransferRequest(
                url = "https://klas.kw.ac.kr/std/lis/board/FileDownStd.do?$query",
                suggestedFileName = "a.pdf",
                mimeType = "application/pdf",
            ),
        )
        assertIs<FileTransferValidation.Accepted>(result)
    }

    @Test
    fun doesNotAutoDownloadPdfsEvenWhenAttachmentOrUnshowable() {
        assertEquals(
            false,
            policy.shouldTreatAsDownload(
                mimeType = "application/pdf",
                contentDisposition = """inline; filename="컴퓨터구조_15주차_강의자료.pdf"""",
                canShowMimeType = true,
            ),
        )
        assertEquals(
            false,
            policy.shouldTreatAsDownload(
                mimeType = "application/pdf",
                contentDisposition = """attachment; filename="컴퓨터구조_15주차_강의자료.pdf"""",
                canShowMimeType = true,
            ),
        )
        assertEquals(
            false,
            policy.shouldTreatAsDownload(
                mimeType = "application/octet-stream",
                contentDisposition = """attachment; filename="컴퓨터구조_15주차_강의자료.pdf"""",
                canShowMimeType = false,
                url = "https://klas.kw.ac.kr/std/lis/board/FileDownStd.do?file=1",
            ),
        )
        assertEquals(
            true,
            policy.shouldTreatAsDownload(
                mimeType = "application/x-hwp",
                contentDisposition = """attachment; filename="note.hwp"""",
                canShowMimeType = true,
            ),
        )
        assertEquals(
            false,
            policy.shouldTreatAsDownload(
                mimeType = "text/html",
                contentDisposition = null,
                canShowMimeType = true,
            ),
        )
    }

    @Test
    fun respectsDispositionTypeInsteadOfFilenameParameter() {
        assertEquals(
            false,
            policy.shouldTreatAsDownload(
                mimeType = "text/plain",
                contentDisposition = """inline; filename="note.txt"""",
                canShowMimeType = true,
            ),
        )
        assertEquals(
            true,
            policy.shouldTreatAsDownload(
                mimeType = "text/plain",
                contentDisposition = """attachment; filename="note.txt"""",
                canShowMimeType = true,
            ),
        )
        assertEquals(
            true,
            policy.shouldTreatAsDownload(
                mimeType = "application/x-hwp",
                contentDisposition = """inline; filename="note.hwp"""",
                canShowMimeType = true,
            ),
        )
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
