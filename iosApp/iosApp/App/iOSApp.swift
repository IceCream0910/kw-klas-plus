import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(ReminderAppDelegate.self) private var reminderDelegate
    init() {
        SentryTelemetry.start()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                #if DEBUG
                if M6011UITestConfiguration.isEnabled {
                    M6011FixtureRootView()
                        .environment(\.dynamicTypeSize, M6011UITestConfiguration.dynamicTypeSize)
                } else {
                    StartupRootView()
                }
                #else
                StartupRootView()
                #endif
            }
                .tint(KlasTheme.primary)
                .background(KlasTheme.background)
        }
    }
}
