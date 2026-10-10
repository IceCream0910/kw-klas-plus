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
