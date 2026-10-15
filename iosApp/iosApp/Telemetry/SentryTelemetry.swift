import Foundation
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

    static func sanitizeLog(_ log: SentryLog) -> SentryLog? {
        guard TelemetryEvent(rawValue: log.body) != nil else { return nil }
        log.attributes = [
            "app.event": SentryAttribute(string: log.body),
            "app.platform": SentryAttribute(string: "ios"),
            "sentry.release": SentryAttribute(string: release),
            "sentry.environment": SentryAttribute(string: environment)
        ]
        return log
    }

    static func sanitizeError(_ event: Event) -> Event {
        event.user = nil
        event.request = nil
        event.message = nil
        event.extra = nil
        event.tags = nil
        event.breadcrumbs = nil
        event.exceptions?.forEach { $0.value = "[redacted]" }
        event.context = event.context?.filter { ["app", "device", "os", "runtime"].contains($0.key) }
        return event
    }
}
