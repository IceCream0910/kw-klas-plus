package com.icecream.kwklasplus.core.diagnostics

import com.icecream.kwklasplus.core.auth.AuthFailure
import com.icecream.kwklasplus.core.network.AuthenticatedKlasEndpoint
import kotlinx.coroutines.flow.MutableStateFlow

enum class DiagnosticStage {
    API, WEB_API_GET, WEB_API_POST, TERM_SUBJECTS, DEADLINE_PAYLOAD, DEADLINE_DATE, DEADLINE_REFRESH, DEADLINE_WEBVIEW,
    CREDENTIAL_PREPARE, PASSWORD_ENCRYPT, CREDENTIAL_STORE, LOGIN_PAGE, LOGIN_SECURITY,
    LOGIN_TOKEN, LOGIN_CAPTCHA, LOGIN_CONFIRM, LOGIN_COOKIE, SESSION_STORE, SESSION_RESTORE,
    SESSION_INFO, SESSION_EXTEND,
}

enum class DiagnosticFailure {
    TIMEOUT, NETWORK, HTTP, EMPTY, MALFORMED, SESSION_EXPIRED, PARTIAL, DELIVERY, STORAGE,
    INVALID_CREDENTIALS, CAPTCHA_REQUIRED, ADDITIONAL_AUTH, RESPONSE_LIMIT,
}

object NativeDiagnostics {
    const val eventName = "klas.operation_failed"
    const val eventMarker = "klas.diagnostic_event"
    private val sink = MutableStateFlow<((Map<String, String>) -> Unit)?>(null)

    fun install(reporter: (Map<String, String>) -> Unit) {
        sink.value = reporter
    }

    fun report(
        stage: DiagnosticStage,
        failure: DiagnosticFailure,
        endpoint: AuthenticatedKlasEndpoint? = null,
        statusCode: Int? = null,
        elapsedMs: Long? = null,
        successCount: Int? = null,
        failureCount: Int? = null,
    ) {
        val reporter = sink.value ?: return
        val attributes = buildMap {
            put("klas.stage", stage.name)
            put("klas.failure", failure.name)
            endpoint?.let { put("klas.endpoint", it.name) }
            statusCode?.let { put("http.status_code", it.toString()) }
            elapsedMs?.let { put("klas.elapsed_ms", it.toString()) }
            successCount?.let { put("klas.success_count", it.toString()) }
            failureCount?.let { put("klas.failure_count", it.toString()) }
        }
        runCatching { reporter(attributes) }
    }

    fun recordDeadlineDeliveryFailure() = report(DiagnosticStage.DEADLINE_WEBVIEW, DiagnosticFailure.DELIVERY)

    fun sanitize(attributes: Map<String, String>): Map<String, String> {
        val stage = DiagnosticStage.entries.firstOrNull { it.name == attributes["klas.stage"] } ?: return emptyMap()
        val failure = DiagnosticFailure.entries.firstOrNull { it.name == attributes["klas.failure"] } ?: return emptyMap()
        return buildMap {
            put("klas.stage", stage.name)
            put("klas.failure", failure.name)
            if (stage in setOf(DiagnosticStage.API, DiagnosticStage.DEADLINE_PAYLOAD, DiagnosticStage.DEADLINE_DATE)) {
                AuthenticatedKlasEndpoint.entries.firstOrNull { it.name == attributes["klas.endpoint"] }
                    ?.let { put("klas.endpoint", it.name) }
            }
            if (stage in httpStages) {
                attributes["http.status_code"]?.toIntOrNull()?.takeIf { it in 100..599 }
                    ?.let { put("http.status_code", it.toString()) }
            }
            attributes["klas.elapsed_ms"]?.toLongOrNull()?.takeIf { it in 0..3_600_000 }
                ?.let { put("klas.elapsed_ms", it.toString()) }
            if (stage == DiagnosticStage.DEADLINE_REFRESH) {
                for (key in listOf("klas.success_count", "klas.failure_count")) {
                    attributes[key]?.toIntOrNull()?.takeIf { it in 0..10_000 }?.let { put(key, it.toString()) }
                }
            }
        }
    }

    private val httpStages = setOf(
        DiagnosticStage.API, DiagnosticStage.WEB_API_GET, DiagnosticStage.WEB_API_POST,
        DiagnosticStage.PASSWORD_ENCRYPT, DiagnosticStage.LOGIN_PAGE, DiagnosticStage.LOGIN_SECURITY,
        DiagnosticStage.LOGIN_CAPTCHA, DiagnosticStage.LOGIN_CONFIRM, DiagnosticStage.SESSION_INFO, DiagnosticStage.SESSION_EXTEND,
    )

    fun isExpected(attributes: Map<String, String>): Boolean = attributes["klas.failure"] in setOf(
        DiagnosticFailure.INVALID_CREDENTIALS.name, DiagnosticFailure.CAPTCHA_REQUIRED.name,
        DiagnosticFailure.ADDITIONAL_AUTH.name, DiagnosticFailure.SESSION_EXPIRED.name,
    )

    fun errorEventFields(tags: Map<String, String>): Map<String, String> {
        if (tags[eventMarker] != eventName) return emptyMap()
        val fields = sanitize(tags)
        if (fields.isEmpty() || isExpected(fields)) return emptyMap()
        if (fields["klas.stage"] == DiagnosticStage.DEADLINE_REFRESH.name &&
            fields["klas.failure"] != DiagnosticFailure.STORAGE.name
        ) return emptyMap()
        return fields
    }

    fun errorSummary(fields: Map<String, String>): String {
        val safe = errorEventFields(fields + (eventMarker to eventName))
        if (safe.isEmpty()) return "[redacted]"
        val stage = safe.getValue("klas.stage")
        val failure = safe.getValue("klas.failure")
        val endpoint = safe["klas.endpoint"]?.let { " [$it]" }.orEmpty()
        val status = safe["http.status_code"]?.let { " (HTTP $it)" }.orEmpty()
        return "KLAS $stage$endpoint: $failure$status"
    }

    fun errorFingerprint(fields: Map<String, String>): List<String> =
        listOfNotNull(eventName, fields["klas.stage"], fields["klas.failure"], fields["klas.endpoint"])

    fun deduplicationKey(attributes: Map<String, String>): String =
        listOf("klas.stage", "klas.failure", "klas.endpoint", "http.status_code").joinToString(":") { attributes[it].orEmpty() }
}

internal fun AuthFailure.diagnosticFailure(): DiagnosticFailure? = when (this) {
    AuthFailure.Network -> DiagnosticFailure.NETWORK
    AuthFailure.Timeout -> DiagnosticFailure.TIMEOUT
    AuthFailure.InvalidCredentials -> DiagnosticFailure.INVALID_CREDENTIALS
    AuthFailure.MalformedResponse -> DiagnosticFailure.MALFORMED
    AuthFailure.Storage -> DiagnosticFailure.STORAGE
    AuthFailure.CaptchaRequired -> DiagnosticFailure.CAPTCHA_REQUIRED
    AuthFailure.TemporaryPasswordChangeRequired -> DiagnosticFailure.ADDITIONAL_AUTH
    AuthFailure.UserCancelled -> null
}
