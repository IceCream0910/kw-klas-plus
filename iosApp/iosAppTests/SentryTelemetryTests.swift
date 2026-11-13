import XCTest
import Sentry
@testable import kw_klas_plus

final class SentryTelemetryTests: XCTestCase {
    func testUnknownLogWithCredentialsIsDropped() {
        XCTAssertNil(SentryTelemetry.sanitizeLog(SentryLog(level: .info, body: "SESSION=secret")))
    }

    func testApprovedLogDropsScopeAndPayloadAttributes() throws {
        let log = SentryLog(level: .warn, body: "reminder.schedule_rejected", attributes: [
            "user.email": SentryAttribute(string: "private@example.com"),
            "password": SentryAttribute(string: "secret")
        ])
        let sanitized = try XCTUnwrap(SentryTelemetry.sanitizeLog(log))
        XCTAssertEqual(Set(sanitized.attributes.keys), ["app.event", "app.platform", "sentry.release", "sentry.environment"])
        XCTAssertEqual(sanitized.attributes["app.event"]?.value as? String, "reminder.schedule_rejected")
    }

    func testDiagnosticSanitizesFieldsAndPreservesStatus() throws {
        let log = SentryLog(level: .warn, body: "klas.operation_failed", attributes: [
            "klas.stage": SentryAttribute(string: "API"),
            "klas.failure": SentryAttribute(string: "HTTP"),
            "klas.endpoint": SentryAttribute(string: "/private?SESSION=secret"),
            "http.status_code": SentryAttribute(string: "503"),
            "klas.success_count": SentryAttribute(string: "4"),
            "password": SentryAttribute(string: "secret")
        ])
        let sanitized = try XCTUnwrap(SentryTelemetry.sanitizeLog(log))
        XCTAssertEqual(sanitized.attributes["http.status_code"]?.value as? Int, 503)
        XCTAssertNil(sanitized.attributes["klas.endpoint"])
        XCTAssertNil(sanitized.attributes["klas.success_count"])
        XCTAssertNil(sanitized.attributes["password"])
    }

    func testKnownFailureRebuildsTheErrorMessageFromApprovedTags() {
        let event = Event(level: .error)
        event.message = SentryMessage(formatted: "SESSION=secret")
        event.tags = [
            "klas.diagnostic_event": "klas.operation_failed",
            "klas.stage": "DEADLINE_DATE",
            "klas.failure": "MALFORMED",
            "klas.endpoint": "TASK_DEADLINES",
            "url": "https://klas.kw.ac.kr/?SESSION=secret"
        ]
        event.fingerprint = ["SESSION=secret"]

        let sanitized = SentryTelemetry.sanitizeError(event)
        XCTAssertEqual(sanitized.message?.formatted, "KLAS DEADLINE_DATE [TASK_DEADLINES]: MALFORMED")
        XCTAssertEqual(Set(sanitized.tags?.keys.map { $0 } ?? []), ["klas.stage", "klas.failure", "klas.endpoint"])
        XCTAssertEqual(sanitized.fingerprint, ["klas.operation_failed", "DEADLINE_DATE", "MALFORMED", "TASK_DEADLINES"])
    }

    func testUnrecognizedFieldsNeverBecomeAnErrorSummary() {
        let event = Event(level: .error)
        event.message = SentryMessage(formatted: "secret payload")
        event.tags = [
            "klas.diagnostic_event": "klas.operation_failed",
            "klas.stage": "https://klas.kw.ac.kr/?SESSION=secret",
            "klas.failure": "HTTP"
        ]
        let sanitized = SentryTelemetry.sanitizeError(event)
        XCTAssertNil(sanitized.message)
        XCTAssertNil(sanitized.tags)
    }

    func testErrorRemovesSensitiveContext() {
        let event = Event()
        event.extra = ["bridge": "secret payload"]
        event.context = ["credentials": ["password": "secret"], "app": ["app_version": "2.0.0"]]
        let sanitized = SentryTelemetry.sanitizeError(event)
        XCTAssertNil(sanitized.extra)
        XCTAssertNil(sanitized.context?["credentials"])
        XCTAssertNotNil(sanitized.context?["app"])
        XCTAssertNil(sanitized.breadcrumbs)
    }
}
