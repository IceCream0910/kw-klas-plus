package com.icecream.kwklasplus.telemetry

import android.app.Application
import android.os.SystemClock
import com.icecream.kwklasplus.core.diagnostics.NativeDiagnostics
import io.sentry.SentryAttributes
import io.sentry.logger.SentryLogParameters
import com.icecream.kwklasplus.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
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
    private val lastReport = mutableMapOf<String, Long>()

    @Synchronized
    private fun shouldReport(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastReport[key]?.let { now - it < 60_000 } == true) return false
        lastReport.entries.removeAll { now - it.value >= 60_000 }
        if (lastReport.size >= 100) return false
        lastReport[key] = now
        return true
    }

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
        NativeDiagnostics.install { attributes ->
            if (shouldReport(NativeDiagnostics.deduplicationKey(attributes))) {
                Sentry.logger().log(
                    if (NativeDiagnostics.isExpected(attributes)) SentryLogLevel.INFO else SentryLogLevel.WARN,
                    SentryLogParameters.create(SentryAttributes.fromMap(attributes)),
                    NativeDiagnostics.eventName,
                )
                val fields = NativeDiagnostics.errorEventFields(
                    attributes + (NativeDiagnostics.eventMarker to NativeDiagnostics.eventName),
                )
                if (fields.isNotEmpty()) {
                    Sentry.captureEvent(SentryEvent().apply {
                        level = SentryLevel.ERROR
                        tags = fields + (NativeDiagnostics.eventMarker to NativeDiagnostics.eventName)
                    })
                }
            }
        }
        log(TelemetryEvent.APP_INITIALIZED)
    }

    fun log(event: TelemetryEvent) {
        Sentry.logger().log(event.level, event.message)
    }

    internal fun sanitizeLog(log: SentryLogEvent): SentryLogEvent? {
        val event = TelemetryEvent.entries.firstOrNull { it.message == log.body }
        val diagnostic = if (log.body == NativeDiagnostics.eventName) {
            val attributes = log.attributes.orEmpty().mapNotNull { (key, value) ->
                (value.value as? String)?.let { key to it }
            }.toMap()
            NativeDiagnostics.sanitize(attributes).takeIf { it.isNotEmpty() } ?: return null
        } else {
            if (event == null) return null
            emptyMap()
        }
        log.level = event?.level ?: if (NativeDiagnostics.isExpected(diagnostic)) SentryLogLevel.INFO else SentryLogLevel.WARN
        log.attributes = mapOf(
            "app.event" to SentryLogEventAttributeValue("string", log.body),
            "app.platform" to SentryLogEventAttributeValue("string", "android"),
            "sentry.release" to SentryLogEventAttributeValue("string", "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"),
            "sentry.environment" to SentryLogEventAttributeValue("string", if (BuildConfig.DEBUG) "development" else "production"),
        ) + diagnostic.mapValues { (key, value) ->
            if (key in setOf("klas.stage", "klas.failure", "klas.endpoint")) SentryLogEventAttributeValue("string", value)
            else SentryLogEventAttributeValue("integer", value.toLong())
        }
        return log
    }

    internal fun sanitizeError(event: SentryEvent): SentryEvent {
        val diagnostic = NativeDiagnostics.errorEventFields(event.tags.orEmpty())
        event.user = null
        event.request = null
        event.message = diagnostic.takeIf { it.isNotEmpty() }?.let {
            Message().apply { formatted = NativeDiagnostics.errorSummary(it) }
        }
        event.extras = emptyMap()
        event.tags = diagnostic
        event.fingerprints = if (diagnostic.isEmpty()) null else NativeDiagnostics.errorFingerprint(diagnostic)
        event.breadcrumbs = emptyList()
        event.exceptions?.forEach { it.value = "[redacted]" }
        event.contexts.keys().toList().filter { it !in setOf("app", "device", "os", "runtime") }.forEach { event.contexts.remove(it) }
        return event
    }
}
