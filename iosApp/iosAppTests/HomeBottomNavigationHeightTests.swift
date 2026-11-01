import Shared
import SwiftUI
import UIKit
import XCTest
@testable import kw_klas_plus

@available(iOS 18.0, *)
@MainActor
final class HomeBottomNavigationHeightTests: XCTestCase {
    private var window: UIWindow?

    override func tearDown() {
        window?.isHidden = true
        window = nil
        super.tearDown()
    }

    func testHomeViewPublishesTabBarHeightAgainOnlyAfterTabBarReappears() throws {
        let suite = "com.icecream.kwklasplus.test.home.height.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let coordinator = HomeCoordinator(authRuntime: IosAuthRuntime.companion.create(defaults: defaults), onLogout: {})
        coordinator.handleBootstrap(
            HomeBootstrapResultReady(
                sessionToken: SecretValue.companion.of(value: "session"),
                yearHakgi: "2026,1",
                yearHakgiListJoined: "2026,1",
                timetableJson: "{}",
                deadlineJson: "[]",
                promptYearHakgiChange: false
            )
        )
        let holder = try XCTUnwrap(coordinator.homeHolder)
        let recorder = Recorder()
        let root = NavigationStack {
            HomeView(holder: holder, coordinator: coordinator)
        }
        .onPreferenceChange(HomeSystemTabBarHeightKey.self) { recorder.values.append($0) }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIHostingController(rootView: root)
        window.makeKeyAndVisible()
        self.window = window
        defer { coordinator.dispose() }
        guard window.traitCollection.horizontalSizeClass == .compact else { throw XCTSkip("하단 탭 바가 있는 폭에서만 검증한다") }
        holder.loadHTML("<html><body>feed</body></html>", baseURL: URL(string: "https://klasplus.yuntae.in/feed")!)
        waitUntil { !holder.webView.isLoading }
        // CI처럼 느린 환경에서는 페이지 완료 처리가 늦을 수 있어 로딩 종료를 직접 맞춘다
        coordinator.isPageLoading = false
        waitUntil { ((recorder.values.last ?? nil) ?? 0) > 0 }
        XCTAssertGreaterThan((recorder.values.last ?? nil) ?? 0, 0)

        coordinator.isSearchOverlayOpen = true
        waitUntil { recorder.values.last == .some(nil) }
        XCTAssertNil(recorder.values.last ?? nil, "탭 바가 숨겨지면 카드를 띄우지 않는다")

        let shownFrom = recorder.values.count
        coordinator.isSearchOverlayOpen = false
        waitUntil { recorder.values[shownFrom...].contains { $0 != nil } }

        let visible = recorder.values[shownFrom...].compactMap { $0 }
        XCTAssertFalse(visible.isEmpty, "탭 바가 다시 보이면 카드가 놓일 높이를 내보내야 한다")
        XCTAssertTrue(visible.allSatisfy { $0 > 0 }, "탭 바와 겹치는 높이를 내보냈다: \(recorder.values)")
    }

    private func waitUntil(timeout: TimeInterval = 10, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline { pump(0.05) }
    }

    private func pump(_ seconds: TimeInterval) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.05)) }
    }
}

@MainActor
private final class Recorder {
    var values: [CGFloat?] = []
}
