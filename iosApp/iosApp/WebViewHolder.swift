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
    @Published private(set) var downloadProgress: DownloadProgressState?
    @Published private(set) var downloadErrorMessage: String?
    @Published private(set) var shareableFileURL: URL?

    private var _webView: WKWebView?
    private var bridgeAdapter: IosBridgeMessageAdapter?
    private var javaScriptAlertCompletion: (() -> Void)?
    private lazy var navigationRelay = NavigationRelay(owner: self)
    private lazy var uiRelay = UIRelay(owner: self)
    private let trustedOrigins = TrustedOriginPolicy(trustedOrigins: TrustedOriginPolicy.companion.DEFAULT_TRUSTED_ORIGINS)
    private let fileTransferPolicy = FileTransferPolicy.companion.create()
    private let navigator: IosExternalNavigator
    private let filePicker: IosFilePicker
    private let fileTransfer: IosFileTransfer
    private var activeDownloadTask: Task<Void, Never>?
    private var loadingLocalPdf = false

    static var websiteDataStore: WKWebsiteDataStore { .default() }

    /// 웹 `bottomNav.js`가 `iOSApp_v숫자`로 앱 WebView를 구분한다. Android `AndroidApp_v`와 별개 토큰이다.
    static var iosAppUserAgentToken: String {
        let build = Bundle.main.object(forInfoDictionaryKey: kCFBundleVersionKey as String) as? String ?? "1"
        let digits = build.split(whereSeparator: { !$0.isNumber }).first.flatMap { Int($0) } ?? 1
        return "iOSApp_v\(max(digits, 1))"
    }

    init(
        navigator: IosExternalNavigator = IosExternalNavigator.companion.system(),
        filePicker: IosFilePicker = IosFilePicker(),
        fileTransfer: IosFileTransfer = IosFileTransfer()
    ) {
        self.navigator = navigator
        self.filePicker = filePicker
        self.fileTransfer = fileTransfer
        super.init()
        fileTransfer.onProgress = { [weak self] fileName, fraction in
            self?.downloadProgress = DownloadProgressState(fileName: fileName, fraction: fraction)
        }
        fileTransfer.onCompletedFile = { [weak self] url in
            self?.handleCompletedFile(url)
        }
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

    func shareCurrentFile() {
        guard let shareableFileURL else { return }
        presentShareSheet(url: shareableFileURL, deleteWhenDone: false)
    }

    func cancelDownload() {
        fileTransfer.cancel()
        clearDownload()
    }

    func clearDownloadError() {
        downloadErrorMessage = nil
    }

    func dispose() {
        guard !isDisposed else { return }
        isDisposed = true
        bridgeAdapter?.dispose()
        bridgeAdapter = nil
        cancelDownload()
        clearInlinePdf()
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

    func handleDecidePolicy(urlString: String, isMainFrame: Bool) -> Bool {
        guard !isDisposed else { return false }
        guard isMainFrame else { return true }
        if let url = URL(string: urlString), IosDownloadFileStore.isStoredFile(url) {
            return true
        }

        if trustedOrigins.isTrustedUrl(url: urlString) {
            return true
        }

        openExternal(urlString)
        return false
    }

    fileprivate func handleCreateWindow(urlString: String?) {
        guard !isDisposed else { return }
        guard let urlString, !urlString.isEmpty else { return }
        if trustedOrigins.isTrustedUrl(url: urlString) {
            load(urlString)
        } else {
            openExternal(urlString)
        }
    }

    fileprivate func navigationDidStart(url: String?) {
        guard !isDisposed, let view = _webView else { return }
        if loadingLocalPdf {
            loadingLocalPdf = false
        } else {
            clearInlinePdf()
        }
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

    func handleNavigationResponse(
        _ response: URLResponse,
        isMainFrame: Bool,
        canShowMIMEType: Bool
    ) -> WKNavigationResponsePolicy {
        guard !isDisposed else { return .cancel }
        if isMainFrame,
           let httpResponse = response as? HTTPURLResponse,
           (400...599).contains(httpResponse.statusCode) {
            guard let view = _webView else { return .cancel }
            navigationState = WebNavigationState(
                loadPhase: .failed(url: response.url?.absoluteString, category: .http),
                canGoBack: view.canGoBack,
                canGoForward: view.canGoForward
            )
            return .cancel
        }

        if let url = response.url, IosDownloadFileStore.isStoredFile(url) {
            return .allow
        }

        // PDF는 공유 시트로 보내지 않는다. attachment/octet-stream은 받아서 웹뷰에 연다.
        if looksLikePdf(response) {
            let request = makeDownloadRequest(response)
            let accepted = fileTransferPolicy.validate(request: request) is FileTransferValidationAccepted
            if canShowInWebView(response, canShowMIMEType: canShowMIMEType), accepted {
                return .allow
            }
            if accepted {
                startDownload(request)
            }
            return .cancel
        }

        if isDownloadCandidate(response, canShowMIMEType: canShowMIMEType) {
            if isAcceptedDownload(response) {
                startDownload(makeDownloadRequest(response))
            }
            return .cancel
        }
        clearInlinePdf()
        return .allow
    }

    private func looksLikePdf(_ response: URLResponse) -> Bool {
        if DownloadMetadata.shared.looksLikePdf(
            mimeType: response.mimeType,
            contentDisposition: (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition"),
            url: response.url?.absoluteString
        ) {
            return true
        }
        return (response.suggestedFilename ?? "").lowercased().hasSuffix(".pdf")
    }

    private func canShowInWebView(_ response: URLResponse, canShowMIMEType: Bool) -> Bool {
        guard canShowMIMEType else { return false }
        let disposition = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition")?.lowercased() ?? ""
        if disposition.contains("attachment") { return false }
        let mime = response.mimeType?
            .split(separator: ";", maxSplits: 1, omittingEmptySubsequences: true)
            .first
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        if mime == "application/octet-stream" { return false }
        return true
    }

    private func clearInlinePdf() {
        if let local = shareableFileURL {
            Self.removeDownloadDirectory(for: local)
        }
        shareableFileURL = nil
    }

    func isDownloadCandidate(_ response: URLResponse, canShowMIMEType: Bool) -> Bool {
        let disposition = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition")
        return fileTransferPolicy.shouldTreatAsDownload(
            mimeType: response.mimeType,
            contentDisposition: disposition,
            canShowMimeType: canShowMIMEType,
            url: response.url?.absoluteString
        )
    }

    func isAcceptedDownload(_ response: URLResponse) -> Bool {
        fileTransferPolicy.validate(request: makeDownloadRequest(response)) is FileTransferValidationAccepted
    }

    private func makeDownloadRequest(_ response: URLResponse) -> FileTransferRequest {
        FileTransferRequest(
            url: response.url?.absoluteString ?? "",
            suggestedFileName: response.suggestedFilename,
            mimeType: response.mimeType,
            userAgent: _webView?.value(forKey: "userAgent") as? String,
            contentDisposition: (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Disposition")
        )
    }

    fileprivate func navigationDidFail(url: String?, error: Error) {
        guard !isDisposed, let view = _webView else { return }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain, nsError.code == NSURLErrorCancelled {
            return
        }
        // 다운로드, 외부 이동으로 내비게이션을 끊으면 WebKit이 102를 남긴다.
        if nsError.code == 102,
           nsError.domain == "WebKitErrorDomain" || nsError.domain == WKError.errorDomain {
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

    private func startDownload(_ request: FileTransferRequest) {
        guard activeDownloadTask == nil else { return }
        activeDownloadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.activeDownloadTask = nil }
            let result = await self.fileTransfer.download(request: request)
            self.handleDownloadResult(result)
        }
    }

    private func handleCompletedFile(_ url: URL) {
        if url.pathExtension.lowercased() == "pdf" {
            openLocalPdf(url)
            return
        }
        presentShareSheet(url: url, deleteWhenDone: true)
    }

    private func openLocalPdf(_ url: URL) {
        if let previous = shareableFileURL, previous != url {
            Self.removeDownloadDirectory(for: previous)
        }
        shareableFileURL = url
        loadingLocalPdf = true
        webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
    }

    private func handleDownloadResult(_ result: PlatformActionResult) {
        clearDownload()
        if result is PlatformActionResultFailed {
            downloadErrorMessage = "다운로드에 실패했습니다."
        }
    }

    fileprivate func handleOpenPanel(allowMultiple: Bool, completionHandler: @escaping ([URL]?) -> Void) {
        filePicker.pickForWeb(
            allowMultiple: allowMultiple,
            from: _webView?.klas_presentingViewController,
            completion: completionHandler
        )
    }

    private func openExternal(_ raw: String) {
        let result = navigator.openValidated(rawValue: raw)
        if result is PlatformActionResultSuccess {
            lastExternalURL = raw
        }
    }

    private func presentShareSheet(url: URL, deleteWhenDone: Bool) {
        guard FileManager.default.fileExists(atPath: url.path) else {
            if deleteWhenDone {
                Self.removeDownloadDirectory(for: url)
            }
            downloadErrorMessage = "다운로드에 실패했습니다."
            return
        }
        // 진행 overlay가 내려간 다음 런루프에서 올려야 iPhone에서 sheet가 가려지지 않는다.
        DispatchQueue.main.async { [weak self] in
            self?.presentActivityController(for: url, deleteWhenDone: deleteWhenDone)
        }
    }

    private func presentActivityController(for url: URL, deleteWhenDone: Bool) {
        let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if deleteWhenDone {
            activity.completionWithItemsHandler = { _, _, _, _ in
                Self.removeDownloadDirectory(for: url)
            }
        }
        if let popover = activity.popoverPresentationController, let webView = _webView {
            popover.sourceView = webView
            popover.sourceRect = CGRect(x: webView.bounds.midX, y: webView.bounds.midY, width: 1, height: 1)
        }
        var presenter = _webView?.klas_presentingViewController ?? UIView.klas_keyWindowRootViewController
        while let presented = presenter?.presentedViewController {
            presenter = presented
        }
        guard let presenter else {
            if deleteWhenDone {
                Self.removeDownloadDirectory(for: url)
            }
            downloadErrorMessage = "다운로드에 실패했습니다."
            return
        }
        presenter.present(activity, animated: true)
    }

    private static func removeDownloadDirectory(for fileURL: URL) {
        try? FileManager.default.removeItem(at: fileURL.deletingLastPathComponent())
    }

    private func publishNavigationFlags(from view: WKWebView) {
        var next = navigationState
        next.canGoBack = view.canGoBack
        next.canGoForward = view.canGoForward
        navigationState = next
    }

    private func clearDownload() {
        downloadProgress = nil
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
        let policy = owner?.handleNavigationResponse(
            navigationResponse.response,
            isMainFrame: navigationResponse.isForMainFrame,
            canShowMIMEType: navigationResponse.canShowMIMEType
        ) ?? .cancel
        decisionHandler(policy)
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
        self.owner = owner
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

    @available(iOS 18.4, *)
    func webView(
        _ webView: WKWebView,
        runOpenPanelWith parameters: WKOpenPanelParameters,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping ([URL]?) -> Void
    ) {
        owner?.handleOpenPanel(allowMultiple: parameters.allowsMultipleSelection, completionHandler: completionHandler)
    }
}
