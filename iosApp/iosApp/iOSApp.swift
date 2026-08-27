import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            Group {
                if M6011UITestConfiguration.isEnabled {
                    M6011FixtureRootView()
                        .environment(\.dynamicTypeSize, M6011UITestConfiguration.dynamicTypeSize)
                } else {
                    StartupRootView()
                }
            }
                .tint(KlasTheme.primary)
                .background(KlasTheme.background)
        }
    }
}
