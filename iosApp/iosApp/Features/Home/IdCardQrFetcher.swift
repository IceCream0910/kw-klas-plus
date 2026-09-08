import Foundation
import Shared
import UIKit
import WebKit

enum IdCardQrWebProbe {
    static let startURL = URL(string: "https://klas.kw.ac.kr/mst/sys/optrn/MyNumberQrStdPage.do")!

    static func isStudentIdQrURL(_ url: URL) -> Bool {
        let value = url.absoluteString
        return value.contains("myidv2_main.php") && value.contains("menu=qid")
    }
}

@MainActor
final class IdCardQrFetcher: NSObject, WKNavigationDelegate {
    private let repository: IdCardQrRepository
    private let originPolicy = KlasContentOriginPolicy()
    private var completion: ((String) -> Void)?
    private var webView: WKWebView?
    private var finished = false
    private var intercepted = false
    private var timeoutTask: Task<Void, Never>?

    init(repository: IdCardQrRepository, completion: @escaping (String) -> Void) {
        self.repository = repository
        self.completion = completion
    }

    func start() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WebViewHolder.websiteDataStore
        let view = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: configuration)
        view.isHidden = true
        view.navigationDelegate = self
        webView = view
        hostWindow()?.addSubview(view)
        timeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            await MainActor.run { self?.finish("") }
        }
        view.load(URLRequest(url: IdCardQrWebProbe.startURL))
    }

    func cancel() {
        timeoutTask?.cancel()
        timeoutTask = nil
        completion = nil
        tearDown()
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        if let url = navigationAction.request.url, IdCardQrWebProbe.isStudentIdQrURL(url) {
            intercepted = true
            decisionHandler(.allow)
            Task { await self.fetchFromInterceptedURL(url) }
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if !intercepted { finish("") }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if !intercepted { finish("") }
    }

    private func fetchFromInterceptedURL(_ url: URL) async {
        guard !finished else { return }
        guard originPolicy.isTrustedUrl(url: url.absoluteString) else {
            finish("")
            return
        }
        let cookies = await WKWebsiteCookieProvider(store: WebViewHolder.websiteDataStore.httpCookieStore)
            .cookieHeader(for: url)
        guard let cookies, !cookies.isEmpty else {
            finish("")
            return
        }
        let request = IdCardQrRequest(url: url.absoluteString, cookies: SecretValue.companion.of(value: cookies))
        let value: String = await withCheckedContinuation { continuation in
            repository.fetch(request: request, completionHandler: { result, _ in
                let parsed = (result as? IdCardQrResultSuccess)?.value ?? ""
                continuation.resume(returning: parsed)
            })
        }
        finish(value)
    }

    private func finish(_ value: String) {
        guard !finished else { return }
        finished = true
        timeoutTask?.cancel()
        timeoutTask = nil
        let done = completion
        completion = nil
        tearDown()
        done?(value)
    }

    private func tearDown() {
        webView?.stopLoading()
        webView?.navigationDelegate = nil
        webView?.removeFromSuperview()
        webView = nil
    }

    private func hostWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
            ?? UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first
    }
}
