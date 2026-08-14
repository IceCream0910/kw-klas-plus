import Foundation
import Shared
import UIKit
import WebKit

// WKWebView 생성·보존·해제와 navigation/cookie store 연결
final class WebViewHolder: NSObject, ObservableObject {
    let creationID = UUID()

    @Published private(set) var isDisposed = false
    @Published private(set) var navigationState = WebNavigationState()
    @Published private(set) var lastExternalURL: String?
    @Published var javaScriptAlertMessage: String?

    private var _webView: WKWebView?
    private var bridgeAdapter: IosBridgeMessageAdapter?
    private var javaScriptAlertCompletion: (() -> Void)?
    private lazy var navigationRelay = NavigationRelay(owner: self)
    private lazy var uiRelay = UIRelay(owner: self)
    private let trustedOrigins = TrustedOriginPolicy(trustedOrigins: TrustedOriginPolicy.companion.DEFAULT_TRUSTED_ORIGINS)
    private let externalPolicy = ExternalNavigationPolicy(maximumLength: 2048)

    static var websiteDataStore: WKWebsiteDataStore { .default() }

    /// 웹 `bottomNav.js`가 `iOSApp_v숫자`로 앱 WebView를 구분한다. Android `AndroidApp_v`와 별개 토큰이다.
    static var iosAppUserAgentToken: String {
        let build = Bundle.main.object(forInfoDictionaryKey: kCFBundleVersionKey as String) as? String ?? "1"
        let digits = build.split(whereSeparator: { !$0.isNumber }).first.flatMap { Int($0) } ?? 1
        return "iOSApp_v\(max(digits, 1))"
    }

    var webView: WKWebView {
        if let existing = _webView {
            return existing
        }
        precondition(!isDisposed, "disposed WebViewHolder는 WKWebView를 다시 생성하지 않는다")
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = Self.websiteDataStore
        configuration.applicationNameForUserAgent = Self.iosAppUserAgentToken
        bridgeAdapter?.install(into: configuration)
        let created = WKWebView(frame: .zero, configuration: configuration)
        created.navigationDelegate = navigationRelay
        created.uiDelegate = uiRelay
        Self.configureWebScrollView(created.scrollView)
        _webView = created
        return created
    }

    /// SwiftUI가 safe area를 이미 적용한다. WKWebView가 inset을 또 넣으면
    /// 캘린더처럼 `dvh`/`position:fixed` 페이지에서 스크롤 시 visual viewport가 줄어 bottomNav가 올라간다.
    static func configureWebScrollView(_ scrollView: UIScrollView) {
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.automaticallyAdjustsScrollIndicatorInsets = false
        scrollView.contentInset = .zero
        scrollView.scrollIndicatorInsets = .zero
        scrollView.keyboardDismissMode = .interactive
    }

    /// WKWebView 생성 전에만 호출
    func installBridge(
        surface: BridgeSurface,
        handler: BridgeCommandHandler,
        synchronousHandler: SynchronousBridgeCommandHandler? = nil,
        bridgeTimeoutMillis: Int32 = KlasNativeBridgeScripts.shared.DEFAULT_BRIDGE_TIMEOUT_MILLIS
    ) {
        precondition(_webView == nil, "bridge는 WKWebView 생성 전에 설치해야 한다")
        bridgeAdapter?.dispose()
        bridgeAdapter = IosBridgeMessageAdapter(
            surface: surface,
            handler: handler,
            synchronousHandler: synchronousHandler,
            bridgeTimeoutMillis: bridgeTimeoutMillis
        )
    }

    static func withLegacyBridge(
        surface: BridgeSurface,
        handler: BridgeCommandHandler,
        synchronousHandler: SynchronousBridgeCommandHandler? = nil
    ) -> WebViewHolder {
        let holder = WebViewHolder()
        holder.installBridge(
            surface: surface,
            handler: handler,
            synchronousHandler: synchronousHandler
        )
        return holder
    }

    func evaluate(_ script: WebScript, completion: ((Any?) -> Void)? = nil) {
        guard !isDisposed else {
            completion?(nil)
            return
        }
        webView.evaluateJavaScript(script.reveal(), completionHandler: { result, _ in
            completion?(result)
        })
    }

    func evaluateRaw(_ source: String, completion: ((Any?) -> Void)? = nil) {
        guard !isDisposed else {
            completion?(nil)
            return
        }
        webView.evaluateJavaScript(source, completionHandler: { result, _ in
            completion?(result)
        })
    }

    func confirmJavaScriptAlert() {
        let completion = javaScriptAlertCompletion
        javaScriptAlertCompletion = nil
        javaScriptAlertMessage = nil
        completion?()
    }

    func presentJavaScriptAlert(message: String, completion: @escaping () -> Void) {
        javaScriptAlertCompletion?()
        javaScriptAlertCompletion = completion
        javaScriptAlertMessage = message
    }

    func load(_ urlString: String) {
        guard !isDisposed, let url = URL(string: urlString) else { return }
        webView.load(URLRequest(url: url))
    }

    func reload() {
        guard !isDisposed, _webView != nil else { return }
        webView.reload()
    }

    func stopLoading() {
        guard !isDisposed, let view = _webView else { return }
        view.stopLoading()
    }

    @discardableResult
    func goBack() -> Bool {
        guard !isDisposed, let view = _webView, view.canGoBack else { return false }
        view.goBack()
        publishNavigationFlags(from: view)
        return true
    }

    @discardableResult
    func goForward() -> Bool {
        guard !isDisposed, let view = _webView, view.canGoForward else { return false }
        view.goForward()
        publishNavigationFlags(from: view)
        return true
    }

    func dispose() {
        guard !isDisposed else { return }
        isDisposed = true
        bridgeAdapter?.dispose()
        bridgeAdapter = nil
        guard let view = _webView else {
            navigationState = WebNavigationState(loadPhase: .disposed)
            return
        }
        view.stopLoading()
        view.navigationDelegate = nil
        view.uiDelegate = nil
        view.removeFromSuperview()
        _webView = nil
        javaScriptAlertCompletion?()
        javaScriptAlertCompletion = nil
        javaScriptAlertMessage = nil
        navigationState = WebNavigationState(loadPhase: .disposed)
    }

    fileprivate func handleDecidePolicy(urlString: String, isMainFrame: Bool) -> Bool {
        guard !isDisposed else { return false }
        guard isMainFrame else { return true }

        if trustedOrigins.isTrustedUrl(url: urlString) {
            return true
        }

        let resolution = externalPolicy.resolve(rawValue: urlString)
        if let allowed = resolution as? ExternalNavigationResolutionAllowed {
            openExternal(allowed.destination)
        }
        return false
    }

    fileprivate func handleCreateWindow(urlString: String?) {
        guard !isDisposed else { return }
        guard let urlString, !urlString.isEmpty else { return }
        if trustedOrigins.isTrustedUrl(url: urlString) {
            load(urlString)
        } else {
            let resolution = externalPolicy.resolve(rawValue: urlString)
            if let allowed = resolution as? ExternalNavigationResolutionAllowed {
                openExternal(allowed.destination)
            }
        }
    }

    fileprivate func navigationDidStart(url: String?) {
        guard !isDisposed, let view = _webView else { return }
        let phase: WebLoadPhase = url.map { .loading(url: $0) } ?? .loading(url: view.url?.absoluteString ?? "")
        navigationState = WebNavigationState(
            loadPhase: phase,
            canGoBack: view.canGoBack,
            canGoForward: view.canGoForward
        )
    }

    fileprivate func navigationDidFinish(url: String?) {
        guard !isDisposed, let view = _webView else { return }
        let finished = url ?? view.url?.absoluteString ?? ""
        navigationState = WebNavigationState(
            loadPhase: .ready(url: finished),
            canGoBack: view.canGoBack,
            canGoForward: view.canGoForward
        )
    }

    func handleNavigationResponse(_ response: URLResponse, isMainFrame: Bool) -> Bool {
        guard !isDisposed else { return false }
        guard isMainFrame,
              let httpResponse = response as? HTTPURLResponse,
              (400...599).contains(httpResponse.statusCode) else {
            return true
        }
        guard let view = _webView else { return false }
        navigationState = WebNavigationState(
            loadPhase: .failed(url: response.url?.absoluteString, category: .http),
            canGoBack: view.canGoBack,
            canGoForward: view.canGoForward
        )
        return false
    }

    fileprivate func navigationDidFail(url: String?, error: Error) {
        guard !isDisposed, let view = _webView else { return }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain, nsError.code == NSURLErrorCancelled {
            return
        }
        let category: WebNavFailureCategory
        switch nsError.code {
        case NSURLErrorSecureConnectionFailed,
             NSURLErrorServerCertificateUntrusted,
             NSURLErrorServerCertificateHasBadDate,
             NSURLErrorServerCertificateHasUnknownRoot,
             NSURLErrorClientCertificateRejected,
             NSURLErrorClientCertificateRequired:
            category = .tls
        case NSURLErrorBadServerResponse:
            category = .http
        default:
            category = .network
        }
        navigationState = WebNavigationState(
            loadPhase: .failed(url: url ?? view.url?.absoluteString, category: category),
            canGoBack: view.canGoBack,
            canGoForward: view.canGoForward
        )
    }

    private func publishNavigationFlags(from view: WKWebView) {
        var next = navigationState
        next.canGoBack = view.canGoBack
        next.canGoForward = view.canGoForward
        navigationState = next
    }

    private func openExternal(_ destination: ExternalDestination) {
        let raw: String
        if let web = destination as? ExternalDestinationWeb {
            raw = web.url
        } else if let email = destination as? ExternalDestinationEmail {
            raw = "mailto:\(email.address)"
        } else if let tel = destination as? ExternalDestinationTelephone {
            raw = "tel:\(tel.number)"
        } else if let platform = destination as? ExternalDestinationPlatformUri {
            raw = platform.uri
        } else {
            return
        }
        lastExternalURL = raw
        guard let url = URL(string: raw) else { return }
        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }
    }
}

private final class NavigationRelay: NSObject, WKNavigationDelegate {
    weak var owner: WebViewHolder?

    init(owner: WebViewHolder) {
        self.owner = owner
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        let urlString = navigationAction.request.url?.absoluteString ?? ""
        let isMainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        let allow = owner?.handleDecidePolicy(urlString: urlString, isMainFrame: isMainFrame) ?? false
        decisionHandler(allow ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        owner?.navigationDidStart(url: webView.url?.absoluteString)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        let allow = owner?.handleNavigationResponse(
            navigationResponse.response,
            isMainFrame: navigationResponse.isForMainFrame
        ) ?? false
        decisionHandler(allow ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        owner?.navigationDidFinish(url: webView.url?.absoluteString)
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation!,
        withError error: Error
    ) {
        owner?.navigationDidFail(url: webView.url?.absoluteString, error: error)
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        owner?.navigationDidFail(url: webView.url?.absoluteString, error: error)
    }
}

private final class UIRelay: NSObject, WKUIDelegate {
    weak var owner: WebViewHolder?

    init(owner: WebViewHolder) {
        self.owner = owner;
    }

    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        owner?.handleCreateWindow(urlString: navigationAction.request.url?.absoluteString)
        return nil
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping () -> Void
    ) {
        owner?.presentJavaScriptAlert(message: message, completion: completionHandler)
    }
}
