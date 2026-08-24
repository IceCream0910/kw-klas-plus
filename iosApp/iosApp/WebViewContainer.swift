import SwiftUI
import WebKit

struct WebSurfaceLayoutPolicy: Equatable {
    var extendsUnderHomeIndicator: Bool

    static let product = WebSurfaceLayoutPolicy(extendsUnderHomeIndicator: true)
    static let embedded = WebSurfaceLayoutPolicy(extendsUnderHomeIndicator: false)
}

enum WebSurfaceViewportScript {
    static let source = """
    (function() {
      if (window.__klasPlusViewportPolicyInstalled) { return; }
      window.__klasPlusViewportPolicyInstalled = true;
      function publishViewport() {
        var viewport = window.visualViewport;
        var height = viewport ? viewport.height : window.innerHeight;
        var offsetTop = viewport ? viewport.offsetTop : 0;
        document.documentElement.style.setProperty('--klas-visual-viewport-height', height + 'px');
        document.documentElement.style.setProperty('--klas-visual-viewport-offset-top', offsetTop + 'px');
        window.dispatchEvent(new Event('resize'));
      }
      if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', publishViewport);
        window.visualViewport.addEventListener('scroll', publishViewport);
      }
      window.addEventListener('resize', publishViewport);
      publishViewport();
    })();
    """
}

// 이미 생성된 WKWebView를 SwiftUI에 표시
struct WebViewContainer: UIViewRepresentable {
    let webView: WKWebView
    let accessibilityIdentifier: String?

    init(webView: WKWebView, accessibilityIdentifier: String? = nil) {
        self.webView = webView
        self.accessibilityIdentifier = accessibilityIdentifier
    }

    func makeUIView(context: Context) -> WKWebView {
        if let accessibilityIdentifier {
            webView.accessibilityIdentifier = accessibilityIdentifier
        }
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // SwiftUI 재렌더링에서 WKWebView를 재생성 하지 않음
    }
}

extension View {
    func webSurfaceLayout(_ policy: WebSurfaceLayoutPolicy = .product) -> some View {
        Group {
            if policy.extendsUnderHomeIndicator {
                self.ignoresSafeArea(.container, edges: .bottom)
            } else {
                self
            }
        }
    }
}
