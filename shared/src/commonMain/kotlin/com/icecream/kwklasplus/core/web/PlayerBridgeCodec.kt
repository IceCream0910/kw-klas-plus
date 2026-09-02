package com.icecream.kwklasplus.core.web

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

sealed interface OnlineContentDecodeResult {
    data class Success(val request: PlayerWebScripts.OnlineContentRequest) : OnlineContentDecodeResult
    data object Malformed : OnlineContentDecodeResult
}

data class PlayerStateSnapshot(
    val currentSeconds: Float,
    val durationSeconds: Float,
    val isMuted: Boolean,
    val isPlaying: Boolean,
    val isFullscreen: Boolean,
) {
    val progressFraction: Float = (currentSeconds / durationSeconds).coerceIn(0f, 1f)
}

data class LecturePlaybackProgress(
    val displayText: String,
    val playedSeconds: Int,
)

class PlayerBridgeCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val htmlTextParser: HtmlTextParser = HtmlTextParser(),
) {
    fun formatTime(seconds: Float): String {
        val safeSeconds = seconds.takeIf(Float::isFinite)?.coerceAtLeast(0f)?.toInt() ?: 0
        val hours = safeSeconds / 3600
        val minutes = safeSeconds % 3600 / 60
        val remainingSeconds = safeSeconds % 60
        return if (hours > 0) {
            "${hours.pad2()}:${minutes.pad2()}:${remainingSeconds.pad2()}"
        } else {
            "${minutes.pad2()}:${remainingSeconds.pad2()}"
        }
    }

    fun decodeOnlineContent(value: String): OnlineContentDecodeResult = try {
        val data = json.parseToJsonElement(value).jsonObject
        OnlineContentDecodeResult.Success(
            PlayerWebScripts.OnlineContentRequest(
                groupCode = data.string("grcode"),
                subjectId = data.string("subj"),
                year = data.string("year"),
                semester = data.string("hakgi"),
                classNumber = data.string("bunban"),
                module = data.string("module"),
                lesson = data.string("lesson"),
                objectId = data.string("oid"),
                starting = data.string("starting"),
                contentsType = data.string("contentsType"),
                weekNumber = data.int("weekNo"),
                weeklySequence = data.int("weeklyseq"),
                width = data.int("width"),
                height = data.int("height"),
                today = data.string("today"),
                startDate = data.string("sdate"),
                endDate = data.string("edate"),
                playerType = data.string("ptype"),
                learnTime = data.string("learnTime"),
                progress = data.int("prog").coerceIn(0, 100),
                playTime = data.string("ptime"),
            ),
        )
    } catch (_: SerializationException) {
        OnlineContentDecodeResult.Malformed
    } catch (_: IllegalArgumentException) {
        OnlineContentDecodeResult.Malformed
    }

    fun playerState(
        currentTime: String,
        duration: String,
        isMuted: String,
        isPlaying: String,
        isFullscreen: String,
    ): PlayerStateSnapshot {
        val current = currentTime.toFloatOrNull()?.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val total = duration.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f
        return PlayerStateSnapshot(
            currentSeconds = current.coerceAtMost(total),
            durationSeconds = total,
            isMuted = isMuted == "true",
            isPlaying = isPlaying == "true",
            isFullscreen = isFullscreen == "true",
        )
    }

    fun lectureProgress(progressHtml: String, timeHtml: String): LecturePlaybackProgress? {
        val progress = htmlTextParser.plainText(progressHtml)
        val time = htmlTextParser.plainText(timeHtml)
        val minutes = PLAYED_MINUTES_PATTERN.find(time)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return LecturePlaybackProgress(
            displayText = listOf(progress, time).filter(String::isNotBlank).joinToString(", "),
            playedSeconds = minutes * 60,
        )
    }

    private fun JsonObject.string(key: String): String =
        (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int {
        val value = get(key) as? JsonPrimitive ?: return 0
        return value.intOrNull ?: value.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
    }

    companion object {
        fun create(): PlayerBridgeCodec = PlayerBridgeCodec()

        private val PLAYED_MINUTES_PATTERN = Regex("""학습시간\s*(\d+)\s*분""")
    }
}

private fun Int.pad2(): String = toString().padStart(2, '0')
