package com.icecream.kwklasplus.telemetry

import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryLogLevel
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.protocol.Request
import org.junit.Assert.*
import org.junit.Test

class SentryTelemetryTest {
    @Test
    fun rawCredentialsAndUnknownLogMessagesAreDiscarded() {
        val log = SentryLogEvent(SentryId(), 1.0, "SESSION=secret", SentryLogLevel.INFO)
        assertNull(SentryTelemetry.sanitizeLog(log))
    }

    @Test
    fun approvedLogDropsScopeAndUnexpectedAttributes() {
        val log = SentryLogEvent(SentryId(), 1.0, "reminder.schedule_rejected", SentryLogLevel.WARN)
        log.setAttribute("user.email", SentryLogEventAttributeValue("string", "private@example.com"))
        log.setAttribute("password", SentryLogEventAttributeValue("string", "secret"))
        assertSame(log, SentryTelemetry.sanitizeLog(log))
        assertEquals(setOf("app.event", "app.platform", "sentry.release", "sentry.environment"), requireNotNull(log.attributes).keys)
        assertEquals("reminder.schedule_rejected", requireNotNull(log.attributes)["app.event"]?.value)
    }

    @Test
    fun errorRetainsExceptionTypeWithoutSensitiveValues() {
        val event = SentryEvent()
        event.user = User().apply { email = "private@example.com" }
        event.request = Request().apply { url = "https://klas.kw.ac.kr/?SESSION=secret" }
        event.setExtra("bridge", "secret payload")
        event.contexts.put("credentials", mapOf("password" to "secret"))
        event.exceptions = listOf(SentryException().apply { type = "IllegalStateException"; value = "SESSION=secret" })
        SentryTelemetry.sanitizeError(event)
        assertNull(event.user)
        assertNull(event.request)
        assertTrue(requireNotNull(event.extras).isEmpty())
        assertFalse(event.contexts.containsKey("credentials"))
        assertEquals("[redacted]", requireNotNull(event.exceptions).single().value)
        assertEquals("IllegalStateException", requireNotNull(event.exceptions).single().type)
    }
}
