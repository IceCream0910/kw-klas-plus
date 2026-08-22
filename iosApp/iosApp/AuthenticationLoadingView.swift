import SwiftUI
import WebKit

struct AuthenticationLoadingView: View {
    var message: String
    var webView: WKWebView

    var body: some View {
        ZStack {
            KlasTheme.background.ignoresSafeArea()
            AuthWebViewHost(webView: webView)
                .opacity(0.01)
                .allowsHitTesting(false)
                .accessibilityIdentifier("authentication_web_view")
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.large)
                    .tint(KlasTheme.primary)
                Text(message)
                    .font(.body)
                    .foregroundStyle(KlasTheme.onBackground)
                    .frame(maxWidth: 480)
                    .multilineTextAlignment(.center)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("authentication_loading")
        }
    }
}

private struct AuthWebViewHost: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView {
        webView.isHidden = true
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        uiView.isHidden = true
    }
}
