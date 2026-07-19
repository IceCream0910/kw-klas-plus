package com.icecream.kwklasplus.core.academic

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AndroidDeadlineDateParsers {
    val onlineLecture = DeadlineDateParser { value ->
        parseLocal(value, ONLINE_LECTURE_FORMAT)
    }

    val assignment = DeadlineDateParser { value ->
        runCatching {
            OffsetDateTime.parse(value, ASSIGNMENT_OFFSET_FORMAT).toInstant().toEpochMilli()
        }.getOrNull() ?: parseLocal(value, LOCAL_DATE_TIME_FORMAT)
    }

    private fun parseLocal(value: String, formatter: DateTimeFormatter): Long? =
        runCatching {
            LocalDateTime.parse(value, formatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

    private val ONLINE_LECTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val ASSIGNMENT_OFFSET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private val LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}
