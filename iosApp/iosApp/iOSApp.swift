import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var webViewHolder = WebViewHolder()

    var body: some Scene {
        WindowGroup {
            ContentView(holder: webViewHolder)
        }
    }
}
