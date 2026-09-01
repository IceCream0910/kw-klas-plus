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

    func testWebViewHolderKeepsViewportAndIdentityPoliciesStable() {
        let holder = WebViewHolder.withLegacyBridge(
            surface: .home,
            handler: AcceptingBridgeCommandHandler()
        )
        defer { holder.dispose() }

        let first = holder.webView
        let second = holder.webView
        XCTAssertTrue(first === second)
        XCTAssertEqual(first.scrollView.keyboardDismissMode, .interactive)
        XCTAssertTrue(WebSurfaceViewportScript.source.contains("visualViewport"))
        XCTAssertTrue(WebSurfaceViewportScript.source.contains("klas-visual-viewport-height"))
        XCTAssertTrue(WebSurfaceViewportScript.source.contains("__klasPlusViewportPublishing"))
        XCTAssertTrue(WebSurfaceLayoutPolicy.product.extendsUnderHomeIndicator)
        XCTAssertFalse(WebSurfaceLayoutPolicy.embedded.extendsUnderHomeIndicator)
    }

    func testJavaScriptAlertCompletionStaysOnThePresentingHolder() {
        let primary = WebViewHolder()
        let secondary = WebViewHolder()
        defer {
            primary.dispose()
            secondary.dispose()
        }

        var primaryConfirmed = false
        var secondaryConfirmed = false
        primary.presentJavaScriptAlert(message: "ui") { primaryConfirmed = true }
        secondary.presentJavaScriptAlert(message: "klas") { secondaryConfirmed = true }

        primary.confirmJavaScriptAlert()
        XCTAssertTrue(primaryConfirmed)
        XCTAssertNil(primary.javaScriptAlertMessage)
        XCTAssertFalse(secondaryConfirmed)
        XCTAssertEqual(secondary.javaScriptAlertMessage, "klas")

        secondary.confirmJavaScriptAlert()
        XCTAssertTrue(secondaryConfirmed)
        XCTAssertNil(secondary.javaScriptAlertMessage)
    }

    func testJavaScriptAlertPresentationKeepsStickyHolderUntilDismissed() {
        let primary = WebViewHolder()
        let secondary = WebViewHolder()
        defer {
            primary.dispose()
            secondary.dispose()
        }
        primary.presentJavaScriptAlert(message: "ui") {}
        secondary.presentJavaScriptAlert(message: "klas") {}

        let first = WebJavaScriptAlertPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: true,
            sticky: nil
        )
        XCTAssertTrue(first === primary)

        let sticky = WebJavaScriptAlertPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: true,
            sticky: primary
        )
        XCTAssertTrue(sticky === primary)

        primary.confirmJavaScriptAlert()
        let queued = WebJavaScriptAlertPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: true,
            sticky: primary
        )
        XCTAssertTrue(queued === secondary)
    }

    func testJavaScriptAlertPresentationIgnoresDisabledSecondary() {
        let primary = WebViewHolder()
        let secondary = WebViewHolder()
        defer {
            primary.dispose()
            secondary.dispose()
        }
        secondary.presentJavaScriptAlert(message: "klas") {}

        XCTAssertNil(
            WebJavaScriptAlertPresentation.holder(
                primary: primary,
                secondary: secondary,
                secondaryEnabled: false,
                sticky: nil
            )
        )

        primary.presentJavaScriptAlert(message: "ui") {}
        let visible = WebJavaScriptAlertPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: false,
            sticky: nil
        )
        XCTAssertTrue(visible === primary)
    }

    func testJavaScriptAlertSuppressionCompletesWithoutPresenting() {
        let holder = WebViewHolder()
        defer { holder.dispose() }

        var suppressedCount = 0
        var presented = false
        holder.suppressJavaScriptAlertContaining = LectureScreenModel.bootstrapLectureErrorMarker
        holder.onSuppressedJavaScriptAlert = { suppressedCount += 1 }

        holder.presentJavaScriptAlert(message: "오류가 발생하였습니다.") { presented = true }
        XCTAssertEqual(suppressedCount, 1)
        XCTAssertTrue(presented)
        XCTAssertNil(holder.javaScriptAlertMessage)

        presented = false
        holder.presentJavaScriptAlert(message: "다른 안내") { presented = true }
        XCTAssertEqual(suppressedCount, 1)
        XCTAssertEqual(holder.javaScriptAlertMessage, "다른 안내")
        XCTAssertFalse(presented)
        holder.confirmJavaScriptAlert()
        XCTAssertTrue(presented)
    }

    func testJavaScriptConfirmAnswersCompleteHandler() {
        let holder = WebViewHolder()
        defer { holder.dispose() }

        var answered: Bool?
        holder.presentJavaScriptConfirm(message: "수강하시겠습니까?") { answered = $0 }
        XCTAssertEqual(holder.javaScriptConfirmMessage, "수강하시겠습니까?")
        holder.answerJavaScriptConfirm(true)
        XCTAssertEqual(answered, true)
        XCTAssertNil(holder.javaScriptConfirmMessage)

        holder.presentJavaScriptConfirm(message: "취소 테스트") { answered = $0 }
        holder.answerJavaScriptConfirm(false)
        XCTAssertEqual(answered, false)
        XCTAssertNil(holder.javaScriptConfirmMessage)
    }

    func testJavaScriptConfirmPresentationIgnoresDisabledSecondary() {
        let primary = WebViewHolder()
        let secondary = WebViewHolder()
        defer {
            primary.dispose()
            secondary.dispose()
        }
        secondary.presentJavaScriptConfirm(message: "klas") { _ in }

        XCTAssertNil(
            WebJavaScriptConfirmPresentation.holder(
                primary: primary,
                secondary: secondary,
                secondaryEnabled: false,
                sticky: nil
            )
        )

        primary.presentJavaScriptConfirm(message: "ui") { _ in }
        let visible = WebJavaScriptConfirmPresentation.holder(
            primary: primary,
            secondary: secondary,
            secondaryEnabled: false,
            sticky: nil
        )
        XCTAssertTrue(visible === primary)
    }

    func testBootstrapLectureErrorMatcher() {
        XCTAssertTrue(LectureScreenModel.isBootstrapLectureError("오류가 발생하였습니다."))
        XCTAssertFalse(LectureScreenModel.isBootstrapLectureError("다른 안내"))
    }

    @MainActor
    func testOpenLectureWindowExpiryEndsSuppressionWithoutSecondCall() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        let model = LectureScreenModel(
            subjectId: "TEST001",
            subjectName: "테스트강의",
            yearSemester: "2026,1",
            sessionToken: SecretValue.companion.of(value: "session"),
            coordinator: coordinator
        )
        defer {
            model.uiHolder.dispose()
            model.klasHolder.dispose()
        }

        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/cmn/frame/Frame.do"))
        )
        XCTAssertEqual(model.bootstrap, .opening)
        XCTAssertEqual(
            model.klasHolder.suppressJavaScriptAlertContaining,
            LectureScreenModel.bootstrapLectureErrorMarker
        )

        model.handleOpenLectureWindowExpired()
        XCTAssertEqual(model.bootstrap, .finished)
        XCTAssertNil(model.klasHolder.suppressJavaScriptAlertContaining)
    }

    @MainActor
    func testWebContentTerminationResetsLectureBootstrapBeforeLctrumHome() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        let model = LectureScreenModel(
            subjectId: "TEST001",
            subjectName: "테스트강의",
            yearSemester: "2026,1",
            sessionToken: SecretValue.companion.of(value: "session"),
            coordinator: coordinator
        )
        defer {
            model.uiHolder.dispose()
            model.klasHolder.dispose()
        }
        _ = model.klasHolder.webView

        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/cmn/frame/Frame.do"))
        )
        XCTAssertEqual(model.bootstrap, .opening)

        model.klasHolder.handleWebContentProcessDidTerminate()
        XCTAssertEqual(model.bootstrap, .idle)
        XCTAssertNil(model.klasHolder.suppressJavaScriptAlertContaining)

        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/cmn/frame/Frame.do"))
        )
        XCTAssertEqual(model.bootstrap, .opening)
    }

    @MainActor
    func testWebContentTerminationDoesNotResetAfterLectureHome() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        let model = LectureScreenModel(
            subjectId: "TEST001",
            subjectName: "테스트강의",
            yearSemester: "2026,1",
            sessionToken: SecretValue.companion.of(value: "session"),
            coordinator: coordinator
        )
        defer {
            model.uiHolder.dispose()
            model.klasHolder.dispose()
        }

        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/cmn/frame/Frame.do"))
        )
        model.handleKlasNavigation(
            WebNavigationState(loadPhase: .ready(url: "https://klas.kw.ac.kr/std/lis/evltn/LctrumHomeStdPage.do"))
        )
        XCTAssertEqual(model.bootstrap, .finished)

        model.prepareLectureBootstrapAfterWebContentTermination()
        XCTAssertEqual(model.bootstrap, .finished)
    }

    func testWindowWidthClassKeepsResponsiveBoundaries() {
        XCTAssertEqual(AppWindowWidthClass.classify(width: 599), .compact)
        XCTAssertEqual(AppWindowWidthClass.classify(width: 600), .medium)
        XCTAssertEqual(AppWindowWidthClass.classify(width: 839), .medium)
        XCTAssertEqual(AppWindowWidthClass.classify(width: 840), .expanded)
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

    @MainActor
    func testEmptyTermsKeepsSessionTokenForNotReady() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        XCTAssertNil(coordinator.sessionToken)

        coordinator.handleBootstrap(
            HomeBootstrapResultEmptyTerms(
                sessionToken: SecretValue.companion.of(value: "empty-session")
            )
        )

        XCTAssertEqual(coordinator.bootstrapPhase, .emptyTerms)
        XCTAssertEqual(coordinator.sessionToken?.reveal(), "empty-session")
    }

    @MainActor
    func testHomeNavigationFailureClearsLoadingWithoutFailingBootstrap() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())

        XCTAssertEqual(coordinator.bootstrapPhase, .ready)
        XCTAssertTrue(coordinator.isPageLoading)

        coordinator.handleHomeNavigation(
            WebNavigationState(
                loadPhase: .ready(url: "https://klasplus.yuntae.in/feed?yearHakgi=2026,1")
            )
        )
        XCTAssertTrue(coordinator.isPageLoading)
        XCTAssertEqual(coordinator.bootstrapPhase, .ready)

        coordinator.handleHomeNavigation(
            WebNavigationState(
                loadPhase: .failed(url: "https://klasplus.yuntae.in/feed?yearHakgi=2026,1", category: .network)
            )
        )
        XCTAssertFalse(coordinator.isPageLoading)
        XCTAssertEqual(coordinator.bootstrapPhase, .ready)
        XCTAssertEqual(
            HomeCoordinator.pageLoadFailureMessage(for: .network),
            "네트워크 연결을 확인해 주세요."
        )
        XCTAssertEqual(
            HomeCoordinator.pageLoadFailureMessage(for: .tls),
            "보안 연결에 실패했습니다."
        )
        XCTAssertEqual(
            HomeCoordinator.pageLoadFailureMessage(for: .http),
            "페이지를 불러오지 못했습니다."
        )
    }

    @MainActor
    func testReloadCurrentTabBypassesSameTabGuard() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        coordinator.handleBootstrap(Self.readyHomeResult())
        XCTAssertEqual(coordinator.currentTab, "feed")

        coordinator.isPageLoading = false
        coordinator.reloadCurrentTab()

        XCTAssertEqual(coordinator.currentTab, "feed")
        XCTAssertTrue(coordinator.isPageLoading)
        XCTAssertEqual(coordinator.bootstrapPhase, .ready)
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

    @MainActor
    func testUniversityNoticeOpenPagePushesInAppLink() {
        let coordinator = makeHomeCoordinator()
        defer { coordinator.dispose() }
        let notice = "https://www.kw.ac.kr/ko/life/notice.jsp?mode=view"

        coordinator.openWeb(url: notice)
        XCTAssertEqual(coordinator.path.count, 1)

        coordinator.openWeb(url: "javascript:alert(1)")
        XCTAssertEqual(coordinator.path.count, 1)
    }

    @MainActor
    private func makeHomeCoordinator() -> HomeCoordinator {
        let suite = "com.icecream.kwklasplus.test.home.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return HomeCoordinator(
            authRuntime: IosAuthRuntime.companion.create(defaults: defaults),
            onLogout: {}
        )
    }

    private static func readyHomeResult() -> HomeBootstrapResultReady {
        HomeBootstrapResultReady(
            sessionToken: SecretValue.companion.of(value: "session"),
            yearHakgi: "2026,1",
            yearHakgiListJoined: "2026,1",
            timetableJson: "{}",
            deadlineJson: "[]",
            promptYearHakgiChange: false
        )
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
