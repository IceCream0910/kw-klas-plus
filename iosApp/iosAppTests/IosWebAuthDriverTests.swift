import Foundation
import Shared
import WebKit
import XCTest
@testable import kw_klas_plus

final class IosWebAuthDriverTests: XCTestCase {
    private let scheme = "klasauthtest"
    private let loginURL = "klasauthtest://login/LoginForm.do"
    private let encryptedPassword = "encrypted-pw-\(UUID().uuidString)"

    func testCaptchaAlertCompletesWithCaptchaRequired() {
        let handler = AuthTestSchemeHandler()
        handler.html = """
        <!doctype html><html><body>
        <script>alert('자동 입력 방지 문자를 입력하세요');</script>
        </body></html>
        """
        let webView = makeWebView(handler: handler)
        let driver = IosWebAuthDriver(webView: webView, timeoutMillis: 5_000, loginURL: loginURL)
        var invalidAlert: String?
        driver.onInvalidCredentialAlert = { invalidAlert = $0 }

        let result = authenticate(driver, webView: webView)

        XCTAssertTrue(result is WebAuthResultFailure, "result=\(String(describing: result))")
        XCTAssertTrue(
            (result as? WebAuthResultFailure)?.failure is AuthFailureCaptchaRequired,
            "failure=\(String(describing: (result as? WebAuthResultFailure)?.failure))"
        )
        XCTAssertNil(invalidAlert)
        XCTAssertFalse(String(describing: result).contains(encryptedPassword))
        cleanup(driver, webView)
    }

    func testTemporaryPasswordReexposureAfterCredentialInjection() {
        let handler = AuthTestSchemeHandler()
        handler.html = "<!doctype html><html><body>login</body></html>"
        let webView = makeWebView(handler: handler)
        let driver = IosWebAuthDriver(webView: webView, timeoutMillis: 8_000, loginURL: loginURL)

        let completed = expectation(description: "auth result")
        var result: WebAuthResult?
        driver.authenticate(credential: credential()) { authResult, _ in
            result = authResult
            completed.fulfill()
        }

        waitUntil("credential injected") { driver.credentialInjected }
        XCTAssertTrue(driver.credentialInjected)
        webView.reload()

        wait(for: [completed], timeout: 5)
        XCTAssertTrue(result is WebAuthResultFailure, "result=\(String(describing: result))")
        XCTAssertTrue(
            (result as? WebAuthResultFailure)?.failure is AuthFailureTemporaryPasswordChangeRequired,
            "failure=\(String(describing: (result as? WebAuthResultFailure)?.failure))"
        )
        cleanup(driver, webView)
    }

    func testHangingNavigationTimesOut() {
        let handler = AuthTestSchemeHandler()
        handler.mode = .hang
        let webView = makeWebView(handler: handler)
        let driver = IosWebAuthDriver(webView: webView, timeoutMillis: 300, loginURL: loginURL)

        let result = authenticate(driver, webView: webView, timeout: 2)

        XCTAssertTrue(result is WebAuthResultFailure, "result=\(String(describing: result))")
        XCTAssertTrue(
            (result as? WebAuthResultFailure)?.failure is AuthFailureTimeout,
            "failure=\(String(describing: (result as? WebAuthResultFailure)?.failure))"
        )
        cleanup(driver, webView)
    }

    func testSessionDomainAcceptsKwRootAndKlasHosts() {
        XCTAssertTrue(IosWebAuthDriver.matchesSessionDomain("kw.ac.kr"))
        XCTAssertTrue(IosWebAuthDriver.matchesSessionDomain(".kw.ac.kr"))
        XCTAssertTrue(IosWebAuthDriver.matchesSessionDomain("klas.kw.ac.kr"))
        XCTAssertTrue(IosWebAuthDriver.matchesSessionDomain(".klas.kw.ac.kr"))
    }

    func testSessionDomainRejectsLookalikesAndExternalHosts() {
        XCTAssertFalse(IosWebAuthDriver.matchesSessionDomain("example.com"))
        XCTAssertFalse(IosWebAuthDriver.matchesSessionDomain("kw.ac.kr.evil.example"))
        XCTAssertFalse(IosWebAuthDriver.matchesSessionDomain("akw.ac.kr"))
        XCTAssertFalse(IosWebAuthDriver.matchesSessionDomain("kw.ac.kr.com"))
    }

    func testSessionCookieHeaderKeepsOnlyMatchingSessionCookies() {
        let foreign = httpCookie(name: "SESSION", value: "foreign-token", domain: "example.com")
        let theme = httpCookie(name: "theme", value: "dark", domain: "klas.kw.ac.kr")
        let klas = httpCookie(name: "SESSION", value: "klas-token", domain: "klas.kw.ac.kr")
        let app = httpCookie(name: "SESSION", value: "app-token", domain: ".kw.ac.kr")

        XCTAssertEqual(
            IosWebAuthDriver.sessionCookieHeader(from: [foreign, theme, klas, app]),
            "SESSION=klas-token; SESSION=app-token"
        )
    }

    func testSessionCookieHeaderDoesNotFallBackToForeignSession() {
        let foreign = httpCookie(name: "SESSION", value: "foreign-token", domain: "example.com")
        let theme = httpCookie(name: "theme", value: "dark", domain: "klas.kw.ac.kr")

        XCTAssertNil(IosWebAuthDriver.sessionCookieHeader(from: [foreign, theme]))
        XCTAssertNil(IosWebAuthDriver.sessionCookieHeader(from: []))
    }

    func testSchemeLoadFailureCompletesWithNetwork() {
        let handler = AuthTestSchemeHandler()
        handler.mode = .fail(
            NSError(domain: NSURLErrorDomain, code: NSURLErrorCannotConnectToHost, userInfo: nil)
        )
        let webView = makeWebView(handler: handler)
        let driver = IosWebAuthDriver(webView: webView, timeoutMillis: 5_000, loginURL: loginURL)

        let result = authenticate(driver, webView: webView)

        XCTAssertTrue(result is WebAuthResultFailure, "result=\(String(describing: result))")
        XCTAssertTrue(
            (result as? WebAuthResultFailure)?.failure is AuthFailureNetwork,
            "failure=\(String(describing: (result as? WebAuthResultFailure)?.failure))"
        )
        cleanup(driver, webView)
    }

    private func httpCookie(name: String, value: String, domain: String) -> HTTPCookie {
        let cookie = HTTPCookie(properties: [
            .name: name,
            .value: value,
            .domain: domain,
            .path: "/",
        ])
        XCTAssertNotNil(cookie, "domain=\(domain) name=\(name)")
        return cookie!
    }

    private func credential() -> StoredCredential {
        StoredCredential(
            accountId: "2020123456",
            encryptedPassword: SecretValue.companion.of(value: encryptedPassword)
        )
    }

    private func makeWebView(handler: AuthTestSchemeHandler) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.setURLSchemeHandler(handler, forURLScheme: scheme)
        return WKWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 480), configuration: configuration)
    }

    private func authenticate(
        _ driver: IosWebAuthDriver,
        webView: WKWebView,
        timeout: TimeInterval = 5
    ) -> WebAuthResult? {
        let completed = expectation(description: "auth result")
        var result: WebAuthResult?
        driver.authenticate(credential: credential()) { authResult, _ in
            result = authResult
            completed.fulfill()
        }
        wait(for: [completed], timeout: timeout)
        return result
    }

    private func waitUntil(_ description: String, timeout: TimeInterval = 3, predicate: @escaping () -> Bool) {
        let expectation = expectation(description: description)
        let started = Date()
        Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { timer in
            if predicate() {
                timer.invalidate()
                expectation.fulfill()
            } else if Date().timeIntervalSince(started) > timeout {
                timer.invalidate()
            }
        }
        wait(for: [expectation], timeout: timeout + 0.5)
    }

    private func cleanup(_ driver: IosWebAuthDriver, _ webView: WKWebView) {
        driver.cancel()
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        webView.removeFromSuperview()
    }
}

private final class AuthTestSchemeHandler: NSObject, WKURLSchemeHandler {
    enum Mode {
        case html
        case hang
        case fail(Error)
    }

    var html = "<!doctype html><html><body>login</body></html>"
    var mode = Mode.html
    private var hangingTasks: [WKURLSchemeTask] = []

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        switch mode {
        case .hang:
            hangingTasks.append(urlSchemeTask)
        case .fail(let error):
            urlSchemeTask.didFailWithError(error)
        case .html:
            let data = Data(html.utf8)
            let response = URLResponse(
                url: urlSchemeTask.request.url ?? URL(string: "klasauthtest://login/LoginForm.do")!,
                mimeType: "text/html",
                expectedContentLength: data.count,
                textEncodingName: "utf-8"
            )
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(data)
            urlSchemeTask.didFinish()
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        hangingTasks.removeAll { $0 === urlSchemeTask }
    }
}
