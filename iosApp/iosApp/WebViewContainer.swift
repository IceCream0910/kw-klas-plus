import SwiftUI
import WebKit

// 이미 생성된 WKWebView를 SwiftUI에 표시
struct WebViewContainer: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView {
        webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // SwiftUI 재렌더링에서 WKWebView를 재생성 하지 않음
    }
}
