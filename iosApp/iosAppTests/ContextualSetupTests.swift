import XCTest
import Shared
import SwiftUI
import UIKit
@testable import kw_klas_plus

@MainActor
final class ContextualSetupTests: XCTestCase {
    func testCompletionAutomaticallyFinishesAfterTwoSeconds() async {
        let name = "completion.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let runtime = IosAuthRuntime.companion.create(defaults: defaults)
        let lock = AppLockController(store: runtime.dependencies.appLockStore)
        var state = LoginUiState(onboardingVisible: false, studentId: "", password: "", agreementAccepted: true)
        state.step = .complete
        let finished = expectation(description: "완료 안내 후 자동 홈 진입")
        let started = Date()
        let view = LoginFunnelView(state: .constant(state), onContinue: {}, onBack: {}, onSaveLibrary: {}, onSkipLibrary: {}, onOpenURL: { _ in }, onFinish: {
            XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(started), 1.8)
            finished.fulfill()
        }, onStudentIdChange: { _ in }).environmentObject(lock)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = UIHostingController(rootView: view)
        window.isHidden = false
        defer { window.isHidden = true; window.rootViewController = nil }
        await fulfillment(of: [finished], timeout: 5)
    }

    func testCompactSuggestionFitsHomeWithoutLargeButtonPanel() throws {
        for kind in ContextualSetup.Suggestion.allCases {
            let content = SetupSuggestionCard(kind: kind, onClose: {}, onAction: {})
                .frame(width: 393).environment(\.dynamicTypeSize, .large)
            let image = try XCTUnwrap(ImageRenderer(content: content).uiImage)
            XCTAssertLessThanOrEqual(image.size.height, 140, kind.rawValue)
            if kind == .notifications {
                let path = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("klas-contextual-compact.png")
                try image.pngData()?.write(to: path)
                print("CONTEXTUAL_PREVIEW: \(path.path)")
            }
        }
    }

    func testEachSuggestionIsDisplayedOnceAcrossControllerRecreation() {
        let name = "contextual-setup.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        for kind in ContextualSetup.Suggestion.allCases {
            let setup = ContextualSetup(defaults: defaults)
            setup.offer(kind, configured: false)
            XCTAssertEqual(setup.suggestion, kind)
            setup.displayed(kind)
            setup.dismiss()
            let recreated = ContextualSetup(defaults: defaults)
            recreated.offer(kind, configured: false)
            XCTAssertNil(recreated.suggestion)
        }
    }

    func testConfiguredFeatureIsNotSuggestedAndUnshownFeaturesRemainEligible() {
        let name = "contextual-setup.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let setup = ContextualSetup(defaults: defaults)
        setup.offer(.appLock, configured: true)
        XCTAssertNil(setup.suggestion)
        setup.offer(.appLock, configured: false)
        setup.displayed(.appLock)
        setup.dismiss()
        XCTAssertNil(setup.suggestion)
        setup.offer(.notifications, configured: false)
        XCTAssertEqual(setup.suggestion, .notifications)
    }

    func testAppLockCompletionDoesNotConsumeNotificationsSuggestion() {
        let name = "contextual-setup.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let setup = ContextualSetup(defaults: defaults)
        setup.offer(.appLock, configured: false)
        setup.displayed(.appLock)
        setup.offer(.notifications, configured: false)
        setup.dismiss()
        setup.offer(.notifications, configured: false)
        XCTAssertEqual(setup.suggestion, .notifications)
        XCTAssertFalse(defaults.bool(forKey: "setup_prompt_seen_notifications"))
    }

    func testLibrarySuggestionStartsAtMenuTabAndSkipsConfiguredAccounts() {
        let name = "contextual-setup.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let setup = ContextualSetup(defaults: defaults)
        let coordinator = HomeCoordinator(authRuntime: IosAuthRuntime.companion.create(defaults: defaults), onLogout: {}, contextualSetup: setup)
        defer { coordinator.dispose() }
        coordinator.switchToTab("feed")
        XCTAssertNil(setup.suggestion)
        coordinator.switchToTab("menu")
        XCTAssertEqual(setup.suggestion, .library)
        setup.dismiss()
        setup.offer(.library, configured: true)
        XCTAssertNil(setup.suggestion)
    }

    func testOnlySecondProcessLaunchQualifiesForLockSuggestion() {
        let name = "contextual-setup.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let setup = ContextualSetup(defaults: defaults)
        ContextualSetup.recordLaunch(defaults: defaults)
        XCTAssertFalse(setup.isSecondLaunch)
        ContextualSetup.recordLaunch(defaults: defaults)
        XCTAssertTrue(setup.isSecondLaunch)
    }
}
