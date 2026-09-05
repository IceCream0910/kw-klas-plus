package com.icecream.kwklasplus.feature.player

import org.junit.Assert.*
import org.junit.Test

class LectureCertificationContinuationTest {
    @Test fun onlyExplicitSuccessConsumesThePendingLectureOnce() {
        val continuation = LectureCertificationContinuation<String>()
        continuation.begin("viewer-A")
        assertNull(continuation.onAlert("인증에 실패하였습니다."))
        assertNull(continuation.onAlert("인증이 취소되었습니다."))
        assertNull(continuation.onAlert("인증 완료 후 다시 시도해주세요."))
        assertEquals("viewer-A", continuation.onAlert("본인 인증이 완료 되었습니다."))
        assertNull(continuation.onAlert("인증이 완료되었습니다."))
    }

    @Test fun navigationOrCancellationClearsThePendingLecture() {
        val continuation = LectureCertificationContinuation<String>()
        continuation.begin("viewer-A")
        continuation.clear()
        assertNull(continuation.onAlert("인증이 완료되었습니다."))
        continuation.begin("viewer-B")
        assertEquals("viewer-B", continuation.onAlert("인증되었습니다."))
    }
}
