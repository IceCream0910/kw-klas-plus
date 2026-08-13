import Shared
import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var webViewHolder = WebViewHolder.withSmokeBridge(
        surface: .home,
        handler: AcceptingBridgeCommandHandler()
    )

    var body: some Scene {
        WindowGroup {
            ContentView(holder: webViewHolder)
        }
    }
}
