import Foundation
import Shared
import WebKit

// WKWebView 생성,보존,해제
final class WebViewHolder: NSObject, ObservableObject {
    let creationID = UUID()

    @Published private(set) var isDisposed = false
    @Published private(set) var lastFinishedURL: String?

    private var didLoadInitialURL = false
    private var _webView: WKWebView?
    private lazy var navigationRelay = NavigationRelay(owner: self)

    var webView: WKWebView {
        if let existing = _webView {
            return existing
        }
        precondition(!isDisposed, "disposed WebViewHolder는 WKWebView를 다시 생성하지 않는다")
        let configuration = WKWebViewConfiguration()
        let created = WKWebView(frame: .zero, configuration: configuration)
        created.navigationDelegate = navigationRelay
        _webView = created
        return created
    }

    // Android Home과 동일한 베이스 URL. yearHakgi는 M6-008 이후 연결
    // Shared KlasUrls.KLAS_PLUS_BASE와 동기화
    static var smokeURL: URL {
        let base = KlasUrls.shared.KLAS_PLUS_BASE
        return URL(string: base + "/feed")!
    }

    func loadSmokeURLIfNeeded() {
        guard !isDisposed, !didLoadInitialURL else { return }
        didLoadInitialURL = true
        webView.load(URLRequest(url: Self.smokeURL))
    }

    func dispose() {
        guard !isDisposed else { return }
        isDisposed = true
        guard let view = _webView else { return }
        view.stopLoading()
        view.navigationDelegate = nil
        view.uiDelegate = nil
        view.removeFromSuperview()
        _webView = nil
        lastFinishedURL = nil
    }

    fileprivate func navigationDidFinish(url: String?) {
        guard !isDisposed else { return }
        lastFinishedURL = url
    }

    fileprivate func navigationDidFail() {
        guard !isDisposed else { return }
    }
}

// 로딩 완료만 관찰. back/forward·cookie·외부 URL은 M6-006
private final class NavigationRelay: NSObject, WKNavigationDelegate {
    weak var owner: WebViewHolder?

    init(owner: WebViewHolder) {
        self.owner = owner
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        owner?.navigationDidFinish(url: webView.url?.absoluteString)
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation!,
        withError error: Error
    ) {
        owner?.navigationDidFail()
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        owner?.navigationDidFail()
    }
}
