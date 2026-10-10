package com.icecream.kwklasplus.telemetry

import android.app.Application
import com.icecream.kwklasplus.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryLogLevel
import io.sentry.android.core.SentryAndroid

enum class TelemetryEvent(val message: String, val level: SentryLogLevel) {
    APP_INITIALIZED("app.initialized", SentryLogLevel.INFO),
    REMINDER_SCHEDULE_REJECTED("reminder.schedule_rejected", SentryLogLevel.WARN),
    REMINDER_REFRESH_FAILED("reminder.refresh_failed", SentryLogLevel.ERROR),
}

object SentryTelemetry {
    fun start(application: Application) {
        SentryAndroid.init(application) { options ->
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.beforeBreadcrumb = io.sentry.SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
            options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ -> sanitizeError(event) }
            options.logs.isEnabled = true
            options.logs.beforeSend = io.sentry.SentryOptions.Logs.BeforeSendLogCallback { sanitizeLog(it) }
        }
        log(TelemetryEvent.APP_INITIALIZED)
    }

    fun log(event: TelemetryEvent) {
        Sentry.logger().log(event.level, event.message)
    }

    internal fun sanitizeLog(log: SentryLogEvent): SentryLogEvent? {
        val event = TelemetryEvent.entries.firstOrNull { it.message == log.body } ?: return null
        log.level = event.level
        log.attributes = mapOf(
            "app.event" to SentryLogEventAttributeValue("string", event.message),
            "app.platform" to SentryLogEventAttributeValue("string", "android"),
            "sentry.release" to SentryLogEventAttributeValue("string", "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"),
            "sentry.environment" to SentryLogEventAttributeValue("string", if (BuildConfig.DEBUG) "development" else "production"),
        )
        return log
    }

    internal fun sanitizeError(event: SentryEvent): SentryEvent {
        event.user = null
        event.request = null
        event.message = null
        event.extras = emptyMap()
        event.tags = emptyMap()
        event.breadcrumbs = emptyList()
        event.exceptions?.forEach { it.value = "[redacted]" }
        event.contexts.keys().toList().filter { it !in setOf("app", "device", "os", "runtime") }.forEach { event.contexts.remove(it) }
        return event
    }
}
