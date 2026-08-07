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
                    WebViewContainer(webView: holder.webView)
                        .ignoresSafeArea(edges: .bottom)
                        .accessibilityLabel("KLAS+")
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

    #if DEBUG
    private var smokeControls: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("creationID: \(holder.creationID.uuidString)")
                .font(.caption2.monospaced())
            Text("tick: \(tick)")
                .font(.caption2)
            if let url = holder.lastFinishedURL {
                Text("finished: \(url)")
                    .font(.caption2)
                    .lineLimit(1)
            }
            HStack {
                Button("Rerender") { tick += 1 }
                Button("Push") { path.append("placeholder") }
                Button("Dispose WebView") { holder.dispose() }
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
