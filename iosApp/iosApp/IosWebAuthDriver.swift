import Foundation
import Shared
import WebKit

/// Android WebAuthDriver 패리티: KLAS 로그인 페이지 관찰,주입,SESSION 감지
final class IosWebAuthDriver: NSObject, WebAuthDriver, WKNavigationDelegate, WKUIDelegate {
    static let desktopUserAgent = IosWebAuthScripts.shared.DESKTOP_LOGIN_USER_AGENT
    static let timeoutMillis = Int(IosWebAuthScripts.shared.TIMEOUT_MILLIS)

    private let webView: WKWebView
    private let loginURL: String
    private let timeoutMillis: Int
    private let policy: WebAuthObservationPolicy
    private(set) var credentialInjected = false
    private var activeCredential: StoredCredential?
    private var completion: ((WebAuthResult?, Error?) -> Void)?
    private var timeoutWorkItem: DispatchWorkItem?
    private var finished = false
    var onInvalidCredentialAlert: ((String?) -> Void)?

    init(
        webView: WKWebView,
        timeoutMillis: Int = IosWebAuthDriver.timeoutMillis,
        loginURL: String = KlasUrls.shared.KLAS_LOGIN
    ) {
        self.webView = webView
        self.loginURL = loginURL
        self.timeoutMillis = timeoutMillis
        self.policy = WebAuthObservationPolicy(
            loginUrl: loginURL,
            allowedHost: "kw.ac.kr",
            sessionCookieName: "SESSION"
        )
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.customUserAgent = Self.desktopUserAgent
    }

    func authenticate(
        credential: StoredCredential,
        completionHandler: @escaping (WebAuthResult?, Error?) -> Void
    ) {
        finished = false
        credentialInjected = false
        activeCredential = credential
        completion = completionHandler
        scheduleTimeout()
        guard let url = URL(string: loginURL) else {
            complete(WebAuthResultFailure(failure: AuthFailureNetwork.shared))
            return
        }
        webView.load(URLRequest(url: url))
    }

    func cancel() {
        timeoutWorkItem?.cancel()
        webView.stopLoading()
        if !finished {
            complete(WebAuthResultFailure(failure: AuthFailureUserCancelled.shared))
        }
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let url = webView.url?.absoluteString ?? ""
        readCookieHeader { [weak self] header in
            guard let self else { return }
            let observation = self.policy.pageFinished(
                url: url,
                credentialInjected: self.credentialInjected,
                cookieHeader: header
            )
            self.handle(observation: observation)
        }
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        complete(WebAuthResultFailure(failure: AuthFailureNetwork.shared))
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation!,
        withError error: Error
    ) {
        complete(WebAuthResultFailure(failure: AuthFailureNetwork.shared))
    }

    // MARK: - WKUIDelegate

    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping () -> Void
    ) {
        completionHandler()
        let failure = policy.alert(message: message)
        if failure is AuthFailureInvalidCredentials {
            onInvalidCredentialAlert?(message)
        }
        complete(WebAuthResultFailure(failure: failure))
    }

    // MARK: - Private

    private func handle(observation: WebAuthPageObservation) {
        if observation is WebAuthPageObservationInjectCredential {
            guard let credential = activeCredential else { return }
            let script = IosWebAuthScripts.shared.loginSetInitial(
                accountId: credential.accountId,
                encryptedPassword: credential.encryptedPassword.reveal()
            )
            webView.evaluateJavaScript(script) { [weak self] _, _ in
                self?.credentialInjected = true
            }
            return
        }
        if let authenticated = observation as? WebAuthPageObservationAuthenticated {
            complete(WebAuthResultSessionObserved(token: authenticated.token))
            return
        }
        if let failed = observation as? WebAuthPageObservationFailed {
            complete(WebAuthResultFailure(failure: failed.failure))
            return
        }
        // Ignore
    }

    private func readCookieHeader(completion: @escaping (String?) -> Void) {
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
            let sessionCookies = cookies.filter { cookie in
                cookie.name == "SESSION" && Self.matchesSessionDomain(cookie.domain)
            }
            let source = sessionCookies.isEmpty ? cookies : sessionCookies
            let header = source.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
            completion(header.isEmpty ? nil : header)
        }
    }

    private static func matchesSessionDomain(_ domain: String) -> Bool {
        let normalized = domain.hasPrefix(".") ? String(domain.dropFirst()) : domain
        return normalized == "kw.ac.kr"
    }

    private func scheduleTimeout() {
        timeoutWorkItem?.cancel()
        let work = DispatchWorkItem { [weak self] in
            self?.complete(WebAuthResultFailure(failure: AuthFailureTimeout.shared))
        }
        timeoutWorkItem = work
        DispatchQueue.main.asyncAfter(
            deadline: .now() + .milliseconds(timeoutMillis),
            execute: work
        )
    }

    private func complete(_ result: WebAuthResult) {
        guard !finished else { return }
        finished = true
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        let callback = completion
        completion = nil
        activeCredential = nil
        callback?(result, nil)
    }
}
