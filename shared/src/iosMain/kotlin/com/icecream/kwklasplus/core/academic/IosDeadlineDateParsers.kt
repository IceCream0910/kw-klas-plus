package com.icecream.kwklasplus.core.academic

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970

object IosDeadlineDateParsers {
    val onlineLecture = DeadlineDateParser { parseLocal(it) }

    val assignment = DeadlineDateParser { parseOffset(it) ?: parseLocal(it) }

    private fun parseLocal(value: String): Long? = parse(value, "yyyy-MM-dd HH:mm:ss", useLocalZone = true)

    private fun parseOffset(value: String): Long? =
        parse(value, "yyyy-MM-dd'T'HH:mm:ss.SSSZ", useLocalZone = false)

    private fun parse(value: String, pattern: String, useLocalZone: Boolean): Long? {
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            dateFormat = pattern
            if (useLocalZone) {
                timeZone = NSTimeZone.localTimeZone
            }
        }
        val date = formatter.dateFromString(value) ?: return null
        return (date.timeIntervalSince1970 * 1000.0).toLong()
    }
}
