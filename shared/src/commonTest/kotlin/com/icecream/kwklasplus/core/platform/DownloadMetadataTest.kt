package com.icecream.kwklasplus.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadMetadataTest {
    @Test
    fun prefersSuggestedNameThenContentDispositionThenUrl() {
        val fromSuggested = DownloadMetadata.resolvedFileName(
            FileTransferRequest(
                url = "https://klas.kw.ac.kr/a.bin",
                suggestedFileName = "report.pdf",
                mimeType = "application/pdf",
                contentDisposition = """attachment; filename="other.hwp"""",
            ),
        )
        assertEquals("report.pdf", fromSuggested)

        val fromHeader = DownloadMetadata.resolvedFileName(
            FileTransferRequest(
                url = "https://klas.kw.ac.kr/a.bin",
                suggestedFileName = null,
                mimeType = null,
                contentDisposition = """attachment; filename="과제:1.pdf"""",
            ),
        )
        assertEquals("과제_1.pdf", fromHeader)

        val fromUrl = DownloadMetadata.resolvedFileName(
            FileTransferRequest(
                url = "https://klas.kw.ac.kr/files/notice.zip?id=1",
                suggestedFileName = null,
                mimeType = null,
            ),
        )
        assertEquals("notice.zip", fromUrl)
    }

    @Test
    fun parsesRfc5987FileName() {
        assertEquals(
            "파일.pdf",
            DownloadMetadata.parseContentDispositionFileName(
                "attachment; filename*=UTF-8''%ED%8C%8C%EC%9D%BC.pdf",
            ),
        )
    }

    @Test
    fun mapsKoreanOfficeMimeTypes() {
        assertEquals("application/x-hwp", DownloadMetadata.resolvedMimeType("a.hwp", null))
        assertEquals("application/haansoft-hwpx", DownloadMetadata.resolvedMimeType("a.hwpx", "application/octet-stream"))
        assertEquals("application/pdf", DownloadMetadata.resolvedMimeType("a.bin", "application/pdf"))
    }

    @Test
    fun detectsPdfFromMimeOrFileName() {
        assertEquals(
            true,
            DownloadMetadata.looksLikePdf("application/pdf", null, "https://klas.kw.ac.kr/a.bin"),
        )
        assertEquals(
            true,
            DownloadMetadata.looksLikePdf(
                "application/octet-stream",
                """attachment; filename="컴퓨터구조_15주차_강의자료.pdf"""",
                "https://klas.kw.ac.kr/std/lis/board/FileDownStd.do",
            ),
        )
        assertEquals(
            false,
            DownloadMetadata.looksLikePdf(
                "application/octet-stream",
                """attachment; filename="note.hwp"""",
                "https://klas.kw.ac.kr/std/lis/board/FileDownStd.do",
            ),
        )
    }
}
