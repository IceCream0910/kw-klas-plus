import Foundation
import Shared
import Sentry

enum TelemetryEvent: String, CaseIterable {
    case appInitialized = "app.initialized"
    case reminderScheduleRejected = "reminder.schedule_rejected"
    case reminderRefreshFailed = "reminder.refresh_failed"
    case reminderRefreshExpired = "reminder.refresh_expired"
}

enum SentryTelemetry {
    static let dsn = "https://9ca84d9bf6f4c8e8ab122f5c46a29a13@o4508145411031040.ingest.us.sentry.io/4512226470133760"
    static var environment: String {
        #if DEBUG
        return "development"
        #else
        return "production"
        #endif
    }
    static var release: String {
        let info = Bundle.main.infoDictionary ?? [:]
        let version = info["CFBundleShortVersionString"] as? String ?? "unknown"
        let build = info["CFBundleVersion"] as? String ?? "unknown"
        return "com.icecream.kwklasplus@\(version)+\(build)"
    }

    private final class RateLimiter: @unchecked Sendable {
        private let lock = NSLock()
        private var lastReport: [String: TimeInterval] = [:]

        func shouldReport(_ key: String) -> Bool {
            lock.lock()
            defer { lock.unlock() }
            let now = ProcessInfo.processInfo.systemUptime
            if let previous = lastReport[key], now - previous < 60 { return false }
            lastReport = lastReport.filter { now - $0.value < 60 }
            guard lastReport.count < 100 else { return false }
            lastReport[key] = now
            return true
        }
    }
    private static let limiter = RateLimiter()

    static func start() {
        guard NSClassFromString("XCTestCase") == nil else { return }
        SentrySDK.start { options in
            options.dsn = dsn
            options.environment = environment
            options.releaseName = release
            options.sendDefaultPii = false
            options.attachScreenshot = false
            options.attachViewHierarchy = false
            options.enableAutoBreadcrumbTracking = false
            options.enableNetworkTracking = false
            options.enableCaptureFailedRequests = false
            options.enableAutoPerformanceTracing = false
            options.beforeBreadcrumb = { _ in nil }
            options.beforeSend = { sanitizeError($0) }
            options.enableLogs = true
            options.beforeSendLog = { sanitizeLog($0) }
        }
        NativeDiagnostics.shared.install { attributes in
            let diagnostics = NativeDiagnostics.shared
            if limiter.shouldReport(diagnostics.deduplicationKey(attributes: attributes)) {
                if diagnostics.isExpected(attributes: attributes) {
                    SentrySDK.logger.info(diagnostics.eventName, attributes: attributes)
                } else {
                    SentrySDK.logger.warn(diagnostics.eventName, attributes: attributes)
                    var tags = attributes
                    tags[diagnostics.eventMarker] = diagnostics.eventName
                    if !diagnostics.errorEventFields(tags: tags).isEmpty {
                        let event = Event(level: .error)
                        event.tags = tags
                        SentrySDK.capture(event: event)
                    }
                }
            }
        }
        log(.appInitialized)
        #if DEBUG
        if CommandLine.arguments.contains("-sentry-smoke-test") {
            SentrySDK.capture(error: NSError(domain: "KLASPlus.SentryVerification", code: 1))
        }
        #endif
    }

    static func log(_ event: TelemetryEvent) {
        switch event {
        case .appInitialized:
            SentrySDK.logger.info(SentryLogMessage(stringLiteral: event.rawValue))
        case .reminderScheduleRejected, .reminderRefreshExpired:
            SentrySDK.logger.warn(SentryLogMessage(stringLiteral: event.rawValue))
        case .reminderRefreshFailed:
            SentrySDK.logger.error(SentryLogMessage(stringLiteral: event.rawValue))
        }
    }

    static func recordDeadlineDeliveryFailure() {
        NativeDiagnostics.shared.recordDeadlineDeliveryFailure()
    }

    static func sanitizeLog(_ log: SentryLog) -> SentryLog? {
        let diagnostics = NativeDiagnostics.shared
        let fields: [String: String]
        if log.body == diagnostics.eventName {
            fields = diagnostics.sanitize(attributes: log.attributes.compactMapValues { $0.value as? String })
            guard !fields.isEmpty else { return nil }
        } else {
            guard TelemetryEvent(rawValue: log.body) != nil else { return nil }
            fields = [:]
        }
        log.attributes = [
            "app.event": SentryAttribute(string: log.body),
            "app.platform": SentryAttribute(string: "ios"),
            "sentry.release": SentryAttribute(string: release),
            "sentry.environment": SentryAttribute(string: environment)
        ]
        for (key, value) in fields {
            log.attributes[key] = Int(value).map { SentryAttribute(integer: $0) } ?? SentryAttribute(string: value)
        }
        return log
    }

    static func sanitizeError(_ event: Event) -> Event {
        let diagnostics = NativeDiagnostics.shared
        let fields = diagnostics.errorEventFields(tags: event.tags ?? [:])
        event.user = nil
        event.request = nil
        event.message = fields.isEmpty ? nil : SentryMessage(formatted: diagnostics.errorSummary(fields: fields))
        event.extra = nil
        event.tags = fields.isEmpty ? nil : fields
        event.fingerprint = fields.isEmpty ? nil : diagnostics.errorFingerprint(fields: fields)
        event.breadcrumbs = nil
        event.exceptions?.forEach { $0.value = "[redacted]" }
        event.context = event.context?.filter { ["app", "device", "os", "runtime"].contains($0.key) }
        return event
    }
}
