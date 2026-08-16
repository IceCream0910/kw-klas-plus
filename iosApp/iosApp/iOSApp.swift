import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            StartupRootView()
                .tint(KlasTheme.primary)
                .background(KlasTheme.background)
        }
    }
}
