package com.icecream.kwklasplus.core.academic

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val localDeadline = Regex("""([0-9]{4}-[0-9]{2}-[0-9]{2})[ T]([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?""")

internal fun normalizeDeadlineDate(value: String, defaultSecond: String = "00"): String {
    val trimmed = value.trim()
    val (date, hour, minute, second) = localDeadline.matchEntire(trimmed)?.destructured ?: return trimmed
    return "$date ${hour.padStart(2, '0')}:$minute:${second.ifEmpty { defaultSecond }}"
}

internal fun JsonObject.deadlineLectureProgress(): Double? =
    (get("prog") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it.isFinite() }
