import Foundation
import Shared
import WebKit
import XCTest
@testable import kw_klas_plus

final class IosHomeHostTests: XCTestCase {
    func testWebViewUserAgentUsesIosAppToken() {
        let token = WebViewHolder.iosAppUserAgentToken
        XCTAssertTrue(token.hasPrefix("iOSApp_v"), token)
        XCTAssertNotNil(Int(token.dropFirst("iOSApp_v".count)), token)

        let holder = WebViewHolder.withLegacyBridge(
            surface: .home,
            handler: AcceptingBridgeCommandHandler()
        )
        defer { holder.dispose() }
        let userAgent = holder.webView.value(forKey: "userAgent") as? String ?? ""
        XCTAssertTrue(userAgent.contains(token), userAgent)
        XCTAssertFalse(userAgent.contains("AndroidApp_v"), userAgent)
        XCTAssertEqual(holder.webView.scrollView.contentInsetAdjustmentBehavior, .never)
        XCTAssertEqual(holder.webView.scrollView.contentInset, .zero)
    }

    @MainActor
    func testHomeTabUrlsMatchAndroid() {
        XCTAssertEqual(
            ProductWebUrls.shared.homeTab(tab: "feed", yearHakgi: "2026,1"),
            "https://klasplus.yuntae.in/feed?yearHakgi=2026,1"
        )
        XCTAssertEqual(
            ProductWebUrls.shared.homeTab(tab: "timetable", yearHakgi: "2026,1"),
            "https://klasplus.yuntae.in/timetableTab?yearHakgi=2026,1"
        )
        XCTAssertEqual(
            ProductWebUrls.shared.homeTab(tab: "menu", yearHakgi: "2026,1"),
            "https://klasplus.yuntae.in/profile"
        )
        XCTAssertEqual(
            HomeCoordinator.homeTab(fromUrl: "https://klasplus.yuntae.in/timetableTab?yearHakgi=2026,1"),
            "timetable"
        )
        XCTAssertEqual(
            HomeCoordinator.homeTab(fromUrl: "https://klasplus.yuntae.in/feed?yearHakgi=2026,1", fallback: "menu"),
            "feed"
        )
        XCTAssertEqual(
            HomeCoordinator.homeTab(fromUrl: "https://klasplus.yuntae.in/calendar?yearHakgi=2026,1"),
            "calendar"
        )
        XCTAssertEqual(
            HomeCoordinator.homeTab(fromUrl: "https://klasplus.yuntae.in/profile"),
            "menu"
        )
        XCTAssertEqual(
            ProductWebUrls.shared.boardList(title: "공지"),
            "https://klasplus.yuntae.in/boardList?title=공지"
        )
        XCTAssertEqual(
            ProductWebUrls.shared.task(path: "/std/lis/evltn/TaskStdPage.do"),
            "https://klas.kw.ac.kr/std/lis/evltn/TaskStdPage.do"
        )
    }

    @MainActor
    func testRetryAfterBootstrapFailureAttachesHomeHolder() {
        let suite = "com.icecream.kwklasplus.test.home.retry.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defer { defaults.removePersistentDomain(forName: suite) }

        let coordinator = HomeCoordinator(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            onLogout: {}
        )
        coordinator.handleBootstrap(HomeBootstrapResultFailure(message: "temporary failure"))
        XCTAssertEqual(coordinator.bootstrapPhase, .failed("temporary failure"))

        coordinator.handleRefresh(
            HomeBootstrapResultReady(
                sessionToken: SecretValue.companion.of(value: "session"),
                yearHakgi: "2026,1",
                yearHakgiListJoined: "2026,1",
                timetableJson: "{}",
                deadlineJson: "[]",
                promptYearHakgiChange: false
            )
        )

        XCTAssertEqual(coordinator.bootstrapPhase, .ready)
        XCTAssertNotNil(coordinator.homeHolder)
        coordinator.dispose()
        XCTAssertNil(coordinator.homeHolder)
    }

    func testReceivedDataCallbacksUseLegacyArgumentCounts() {
        XCTAssertTrue(IosWebCallbacks.shared.receivedData(token: "t", subjectId: "s").reveal().contains("window.receivedData"))
        let three = IosWebCallbacks.shared.receivedData(token: "t", subjectId: "s", yearHakgi: "2026,1").reveal()
        XCTAssertTrue(three.contains("\"2026,1\""))
        let four = IosWebCallbacks.shared.receivedData(
            token: "t",
            subjectId: "s",
            yearHakgi: "2026,1",
            path: "board-path"
        ).reveal()
        XCTAssertTrue(four.contains("\"board-path\""))
    }

    func testCompletePageLoadInjectsReceiveToken() throws {
        let host = RecordingHomeHost()
        let holder = WebViewHolder.withLegacyBridge(
            surface: .home,
            handler: IosHomeLegacyBridgeCommandHandler(host: host)
        )
        defer { holder.dispose() }

        let html = """
        <!doctype html><html><body>
        <script>
        window.__token = null;
        window.receiveToken = function(value) { window.__token = value; };
        </script>
        </body></html>
        """
        try loadHTML(holder.webView, html, baseURL: URL(string: "https://klasplus.yuntae.in/")!)
        holder.evaluate(IosWebCallbacks.shared.receiveToken(token: "session-token"))
        let expectation = expectation(description: "token")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            holder.webView.evaluateJavaScript("window.__token") { result, _ in
                XCTAssertEqual(result as? String, "session-token")
                expectation.fulfill()
            }
        }
        waitForExpectations(timeout: 5)
    }

    func testCloseBottomSheetScriptName() {
        XCTAssertTrue(
            KlasWebAutomationScripts.shared.closeBottomSheet().reveal().contains("window.closeWebViewBottomSheet")
        )
    }

    @MainActor
    func testNativeOverlaysAndSettingsRouteWithoutSession() {
        let suite = "com.icecream.kwklasplus.test.home.overlays.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        defer { defaults.removePersistentDomain(forName: suite) }

        let runtime = IosAuthRuntime.companion.create(defaults: defaults)
        let coordinator = HomeCoordinator(authRuntime: runtime, onLogout: {})

        coordinator.openLecture(subjectId: "TEST001", subjectName: "테스트강의")
        coordinator.openBoardList(
            path: "/std/lis/sport/spt/NoticeStdPage.do",
            title: "공지",
            subjectId: "TEST001",
            yearSemester: "2026,1"
        )
        coordinator.openTask(
            path: "/std/lis/evltn/TaskStdPage.do",
            subjectId: "TEST001",
            yearSemester: "2026,1"
        )
        XCTAssertEqual(coordinator.path.count, 0)

        coordinator.openSettings()
        XCTAssertEqual(coordinator.path.count, 1)

        coordinator.presentYearHakgiPicker(isUpdate: true)
        coordinator.showOptionsMenu = true
        coordinator.openDateTimePicker(currentDateTime: nil, isStart: true)
        XCTAssertTrue(coordinator.showYearHakgiPicker)
        XCTAssertTrue(coordinator.yearHakgiPickerIsUpdate)
        XCTAssertTrue(coordinator.showOptionsMenu)
        XCTAssertTrue(coordinator.showDatePicker)
        coordinator.dispose()
    }

    private func loadHTML(_ webView: WKWebView, _ html: String, baseURL: URL) throws {
        let expectation = XCTestExpectation(description: "load")
        let waiter = FinishWaiter(expectation: expectation)
        webView.navigationDelegate = waiter
        webView.loadHTMLString(html, baseURL: baseURL)
        let result = XCTWaiter.wait(for: [expectation], timeout: 5)
        webView.navigationDelegate = nil
        guard result == .completed else {
            throw NSError(domain: "IosHomeHostTests", code: 1)
        }
    }
}

private final class FinishWaiter: NSObject, WKNavigationDelegate {
    private let expectation: XCTestExpectation
    init(expectation: XCTestExpectation) { self.expectation = expectation }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { expectation.fulfill() }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        expectation.fulfill()
    }
    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        expectation.fulfill()
    }
}

private final class RecordingHomeHost: HomeBridgeHost {
    func changeTab(tab: String) {}
    func evaluate(url: String, yearHakgi: String, subj: String) {}
    func openPage(url: String) {}
    func openExternalPage(url: String) {}
    func completePageLoad() {}
    func openLibraryQR() {}
    func openLibraryQRSettingsModal() {}
    func openLectureActivity(subj: String, subjName: String) {}
    func qrCheckIn(subjId: String, subjName: String) {}
    func openDateTimePicker(currentDateTime: String?, isStart: Bool) {}
    func openWebViewBottomSheet() {}
    func closeWebViewBottomSheet() {}
    func openOptionsMenu() {}
    func openYearHakgiBottomSheet() {}
    func reload() {}
    func performHapticFeedback(type: String) {}
    func requestIdCardQRValue() {}
}
