import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var holder: WebViewHolder
    @State private var tick = 0
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            ZStack(alignment: .bottom) {
                if holder.isDisposed {
                    Text("WebView disposed")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityLabel("KLAS+")
                } else {
                    ZStack {
                        WebViewContainer(webView: holder.webView)
                            .ignoresSafeArea(edges: .bottom)
                            .accessibilityLabel("KLAS+")
                        if isLoading {
                            ProgressView()
                        }
                    }
                }

                #if DEBUG
                smokeControls
                #endif
            }
            .navigationTitle("KLAS+")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: String.self) { _ in
                Text("Placeholder — pop to verify WebView identity")
                    .navigationTitle("Re-entry")
            }
            .onAppear {
                holder.loadSmokeURLIfNeeded()
            }
        }
    }

    private var isLoading: Bool {
        if case .loading = holder.navigationState.loadPhase {
            return true
        }
        return false
    }

    #if DEBUG
    private var smokeControls: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("creationID: \(holder.creationID.uuidString)")
                .font(.caption2.monospaced())
            Text("phase: \(String(describing: holder.navigationState.loadPhase))")
                .font(.caption2)
                .lineLimit(2)
            Text("back=\(holder.navigationState.canGoBack) forward=\(holder.navigationState.canGoForward)")
                .font(.caption2)
            if let external = holder.lastExternalURL {
                Text("external: \(external)")
                    .font(.caption2)
                    .lineLimit(1)
            }
            Text("tick: \(tick)")
                .font(.caption2)
            HStack {
                Button("Rerender") { tick += 1 }
                Button("Push") { path.append("placeholder") }
                Button("Back") { _ = holder.goBack() }
                    .disabled(!holder.navigationState.canGoBack)
                Button("Reload") { holder.reload() }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            HStack {
                Button("Load settings") {
                    holder.load(KlasUrls.shared.SETTINGS)
                }
                Button("Open example.com") {
                    holder.load("https://example.com")
                }
                Button("Dispose") { holder.dispose() }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial)
    }
    #endif
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView(holder: WebViewHolder())
    }
}
