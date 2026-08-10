import Shared
import WebKit
import XCTest
@testable import kw_klas_plus

final class IosBridgeMessageAdapterTests: XCTestCase {
    private let trustedBaseURL = URL(string: "https://klasplus.yuntae.in/")!
    private let untrustedBaseURL = URL(string: "https://evil.example/")!
    private let videoBaseURL = URL(string: "https://vod.kw.ac.kr/")!
    private let rootKwBaseURL = URL(string: "https://kw.ac.kr/")!

    func testSuccessfulBridgeCallResolvesPromise() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            try {
              await window.KlasNativeBridge.completePageLoad();
              return JSON.stringify({ok:true});
            } catch (e) {
              return JSON.stringify({ok:false, code:(e && e.code) || String(e)});
            }
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, true)
    }

    func testAsyncHandlerResultResolvesAfterDelay() throws {
        let harness = try makeHarness(
            surface: .home,
            handler: AcceptingBridgeCommandHandler(delayMillis: 120)
        )
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            try {
              await window.KlasNativeBridge.reload();
              return JSON.stringify({ok:true});
            } catch (e) {
              return JSON.stringify({ok:false, code:(e && e.code) || String(e)});
            }
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, true)
    }

    func testUnknownMethodReturnsErrorCode() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            try {
              await window.KlasNativeBridge.call('missingMethod', []);
              return JSON.stringify({ok:true});
            } catch (e) {
              return JSON.stringify({ok:false, code:(e && e.code) || String(e)});
            }
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["code"] as? String, "UNKNOWN_METHOD")
    }

    func testMalformedPayloadReturnsMalformedRequest() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            const response = await webkit.messageHandlers.KlasNativeBridgeNative.postMessage('not-json');
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["code"] as? String, "MALFORMED_REQUEST")
    }

    func testOversizePayloadReturnsPayloadTooLarge() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        defer { harness.dispose() }

        let oversized = String(repeating: "a", count: 70_000)
        let result = try harness.evaluateBridge(
            """
            const response = await webkit.messageHandlers.KlasNativeBridgeNative.postMessage(JSON.stringify({
              version:1,
              id:'oversize-1',
              method:'reload',
              arguments:['\(oversized)']
            }));
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["code"] as? String, "PAYLOAD_TOO_LARGE")
    }

    func testIframeCallReturnsNotMainFrame() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            await new Promise((resolve, reject) => {
              const frame = document.createElement('iframe');
              frame.src = 'about:blank';
              frame.onload = () => resolve();
              frame.onerror = reject;
              document.body.appendChild(frame);
            });
            const frame = document.querySelector('iframe');
            const response = await frame.contentWindow.webkit.messageHandlers.KlasNativeBridgeNative.postMessage(JSON.stringify({
              version:1,
              id:'iframe-1',
              method:'reload',
              arguments:[]
            }));
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["code"] as? String, "NOT_MAIN_FRAME")
    }

    func testUntrustedOriginReturnsUntrustedOrigin() throws {
        let harness = try makeHarness(
            surface: .home,
            handler: AcceptingBridgeCommandHandler(),
            baseURL: untrustedBaseURL
        )
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            const response = await webkit.messageHandlers.KlasNativeBridgeNative.postMessage(JSON.stringify({
              version:1,
              id:'evil-1',
              method:'reload',
              arguments:[]
            }));
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["code"] as? String, "UNTRUSTED_ORIGIN")
    }

    func testVideoSurfaceAllowsKwSubdomainAndRejectsRootHost() throws {
        let allowed = try makeHarness(
            surface: .video,
            handler: AcceptingBridgeCommandHandler(),
            baseURL: videoBaseURL
        )
        defer { allowed.dispose() }
        let allowedResult = try allowed.evaluateBridge(
            """
            const response = await webkit.messageHandlers.KlasNativeBridgeNative.postMessage(JSON.stringify({
              version:1,
              id:'video-ok',
              method:'receiveVideoURL',
              arguments:['https://vod.kw.ac.kr/a.mp4']
            }));
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(allowedResult["ok"] as? Bool, true)

        let rejected = try makeHarness(
            surface: .video,
            handler: AcceptingBridgeCommandHandler(),
            baseURL: rootKwBaseURL
        )
        defer { rejected.dispose() }
        let rejectedResult = try rejected.evaluateBridge(
            """
            const response = await webkit.messageHandlers.KlasNativeBridgeNative.postMessage(JSON.stringify({
              version:1,
              id:'video-root',
              method:'receiveVideoURL',
              arguments:['https://kw.ac.kr/a.mp4']
            }));
            const data = typeof response === 'string' ? JSON.parse(response) : response;
            return JSON.stringify({ok:data.ok, code:data.error && data.error.code});
            """
        )
        XCTAssertEqual(rejectedResult["ok"] as? Bool, false)
        XCTAssertEqual(rejectedResult["code"] as? String, "UNTRUSTED_ORIGIN")
    }

    func testShortTimeoutRejectsWhenNativeNeverReplies() throws {
        let harness = try makeHarness(
            surface: .home,
            handler: HangingBridgeCommandHandler(),
            bridgeTimeoutMillis: 200
        )
        defer { harness.dispose() }

        let result = try harness.evaluateBridge(
            """
            try {
              await window.KlasNativeBridge.reload();
              return JSON.stringify({ok:true});
            } catch (e) {
              return JSON.stringify({ok:false, message:String((e && e.message) || e)});
            }
            """,
            timeout: 3
        )
        XCTAssertEqual(result["ok"] as? Bool, false)
        XCTAssertEqual(result["message"] as? String, "BRIDGE_TIMEOUT")
    }

    func testDisposeRemovesScriptMessageHandler() throws {
        let harness = try makeHarness(surface: .home, handler: AcceptingBridgeCommandHandler())
        harness.adapter.dispose()

        let expectation = expectation(description: "post after dispose")
        harness.webView.callAsyncJavaScript(
            """
            try {
              await webkit.messageHandlers.KlasNativeBridgeNative.postMessage('{}');
              return 'resolved';
            } catch (e) {
              return 'rejected';
            }
            """,
            arguments: [:],
            in: nil,
            in: .page
        ) { result in
            switch result {
            case .success(let value):
                XCTAssertEqual(value as? String, "rejected")
            case .failure:
                break
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 3)
        harness.disposeWebViewOnly()
    }

    // MARK: - Helpers

    private func makeHarness(
        surface: BridgeSurface,
        handler: BridgeCommandHandler,
        baseURL: URL? = nil,
        bridgeTimeoutMillis: Int32 = KlasNativeBridgeScripts.shared.DEFAULT_BRIDGE_TIMEOUT_MILLIS
    ) throws -> BridgeTestHarness {
        let adapter = IosBridgeMessageAdapter(
            surface: surface,
            handler: handler,
            bridgeTimeoutMillis: bridgeTimeoutMillis
        )
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        adapter.install(into: configuration)
        let webView = WKWebView(frame: .init(x: 0, y: 0, width: 320, height: 480), configuration: configuration)
        let harness = BridgeTestHarness(webView: webView, adapter: adapter)
        try harness.loadHTML("<!doctype html><html><body>bridge</body></html>", baseURL: baseURL ?? trustedBaseURL)
        let probe = try harness.evaluateBridge(
            """
            return JSON.stringify({
              hasHandler: !!(webkit && webkit.messageHandlers && webkit.messageHandlers.KlasNativeBridgeNative),
              hasTransport: !!(window.KlasNativeBridgeNative && window.KlasNativeBridgeNative.postMessage),
              hasAdapter: !!window.KlasNativeBridge
            });
            """
        )
        XCTAssertEqual(probe["hasHandler"] as? Bool, true, "WK message handler missing")
        XCTAssertEqual(probe["hasTransport"] as? Bool, true, "WebKit transport shim missing")
        XCTAssertEqual(probe["hasAdapter"] as? Bool, true, "KlasNativeBridge adapter missing")
        return harness
    }
}

private final class BridgeTestHarness {
    let webView: WKWebView
    let adapter: IosBridgeMessageAdapter

    init(webView: WKWebView, adapter: IosBridgeMessageAdapter) {
        self.webView = webView
        self.adapter = adapter
    }

    func loadHTML(_ html: String, baseURL: URL) throws {
        let expectation = XCTestExpectation(description: "load html")
        let navigator = NavigationFinishWaiter(expectation: expectation)
        webView.navigationDelegate = navigator
        webView.loadHTMLString(html, baseURL: baseURL)
        let result = XCTWaiter.wait(for: [expectation], timeout: 5)
        webView.navigationDelegate = nil
        guard result == .completed else {
            throw NSError(domain: "BridgeTestHarness", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "HTML load timed out",
            ])
        }
    }

    func evaluateBridge(_ script: String, timeout: TimeInterval = 5) throws -> [String: Any] {
        let value = try evaluateJavaScript(script, timeout: timeout)
        return try parseBridgeJSONObject(value)
    }

    func evaluateJavaScript(_ script: String, timeout: TimeInterval = 5) throws -> Any? {
        let expectation = XCTestExpectation(description: "evaluate js")
        var output: Any?
        var failure: Error?
        webView.callAsyncJavaScript(
            script,
            arguments: [:],
            in: nil,
            in: .page
        ) { result in
            switch result {
            case .success(let value):
                output = value
            case .failure(let error):
                failure = error
            }
            expectation.fulfill()
        }
        let waited = XCTWaiter.wait(for: [expectation], timeout: timeout)
        if let failure {
            throw failure
        }
        guard waited == .completed else {
            throw NSError(domain: "BridgeTestHarness", code: 3, userInfo: [
                NSLocalizedDescriptionKey: "JS evaluation timed out",
            ])
        }
        return output
    }

    func dispose() {
        adapter.dispose()
        disposeWebViewOnly()
    }

    func disposeWebViewOnly() {
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.removeFromSuperview()
    }
}

private final class NavigationFinishWaiter: NSObject, WKNavigationDelegate {
    private let expectation: XCTestExpectation

    init(expectation: XCTestExpectation) {
        self.expectation = expectation
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        expectation.fulfill()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        expectation.fulfill()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        expectation.fulfill()
    }
}

fileprivate func parseBridgeJSONObject(_ value: Any?) throws -> [String: Any] {
    if let text = value as? String,
       let data = text.data(using: .utf8),
       let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
        return object
    }
    if let object = value as? [String: Any] {
        return object
    }
    throw NSError(domain: "IosBridgeMessageAdapterTests", code: 1, userInfo: [
        NSLocalizedDescriptionKey: "expected JSON object, got \(String(describing: value))",
    ])
}
