package com.icecream.kwklasplus.core.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayerBridgeCodecTest {
    private val codec = PlayerBridgeCodec()

    @Test
    fun decodesLegacyOnlineContentPayloadWithStringNumbers() {
        val result = codec.decodeOnlineContent(
            """{"grcode":"G","subj":"S","year":"2026","hakgi":"1","weekNo":"3","weeklyseq":2,"width":1280,"height":720,"prog":30,"ptime":"10"}""",
        )

        val request = assertIs<OnlineContentDecodeResult.Success>(result).request
        assertEquals("G", request.groupCode)
        assertEquals("S", request.subjectId)
        assertEquals(3, request.weekNumber)
        assertEquals(30, request.progress)
    }

    @Test
    fun rejectsMalformedOnlineContentPayload() {
        assertEquals(OnlineContentDecodeResult.Malformed, codec.decodeOnlineContent("not-json"))
    }

    @Test
    fun normalizesPlayerStateAndProgress() {
        assertEquals(
            PlayerStateSnapshot(30f, 120f, isMuted = true, isPlaying = true, isFullscreen = false),
            codec.playerState("30", "120", "true", "true", "false"),
        )
        assertEquals(
            LecturePlaybackProgress("진도율 25%, 학습시간 2분/10분", 120),
            codec.lectureProgress(
                "<span id=\"lrnPer\">진도율 25%</span>",
                "<span id=\"lrnmin\">학습시간 2분/10분</span>",
            ),
        )
    }

    @Test
    fun malformedPlayerNumbersUseSafeLegacyFallbacks() {
        assertEquals(
            PlayerStateSnapshot(0f, 1f, false, false, false),
            codec.playerState("NaN", "0", "FALSE", "", "no"),
        )
        assertEquals(null, codec.lectureProgress("progress", "unknown"))
    }

    @Test
    fun formatsPlayerTimeWithoutPlatformFormatters() {
        assertEquals("00:00", codec.formatTime(Float.NaN))
        assertEquals("02:05", codec.formatTime(125.9f))
        assertEquals("01:02:03", codec.formatTime(3723f))
    }
}
