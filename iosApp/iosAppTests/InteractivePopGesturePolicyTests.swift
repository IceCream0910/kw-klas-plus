import SwiftUI
import UIKit
import WebKit
import XCTest
@testable import kw_klas_plus

@MainActor
final class InteractivePopGesturePolicyTests: XCTestCase {
    private final class MockWebView: WKWebView {
        var mockCanGoBack: Bool = false
        override var canGoBack: Bool { mockCanGoBack }
    }

    private func makeNavigationController(root: UIViewController) -> UINavigationController {
        let nav = UINavigationController(rootViewController: root)
        nav.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        nav.interactivePopGestureRecognizer?.delegate = nav
        return nav
    }

    func testPolicyIgnoresRootEvenWithNativeLayer() {
        let policy = InteractivePopPolicy(
            isPushed: false,
            consumesNativeBack: true,
            activeWebViewCanGoBack: true
        )
        XCTAssertEqual(policy.decision, .ignore)
    }

    func testPolicyPrefersNativeLayerOverWebHistory() {
        let policy = InteractivePopPolicy(
            isPushed: true,
            consumesNativeBack: true,
            activeWebViewCanGoBack: true
        )
        XCTAssertEqual(policy.decision, .consumeNativeBack)
    }

    func testPolicyYieldsToWebHistoryWhenNoNativeLayer() {
        let policy = InteractivePopPolicy(
            isPushed: true,
            consumesNativeBack: false,
            activeWebViewCanGoBack: true
        )
        XCTAssertEqual(policy.decision, .yieldToWebHistory)
    }

    func testPolicyBeginsSystemPopWhenPushedWithoutLayerOrHistory() {
        let policy = InteractivePopPolicy(
            isPushed: true,
            consumesNativeBack: false,
            activeWebViewCanGoBack: false
        )
        XCTAssertEqual(policy.decision, .beginSystemPop)
    }

    func testNativeBackCommitUsesTranslationOrVelocityThreshold() {
        XCTAssertTrue(InteractivePopPolicy.shouldCommitNativeBack(translationX: 81, velocityX: 0))
        XCTAssertTrue(InteractivePopPolicy.shouldCommitNativeBack(translationX: 10, velocityX: 501))
        XCTAssertFalse(InteractivePopPolicy.shouldCommitNativeBack(translationX: 80, velocityX: 500))
    }

    func testLectureConsumesNativeBackOnlyWhenKlasOverlayHasNoHistory() {
        XCTAssertTrue(LectureScreenModel.consumesNativeBack(showingKlas: true, klasCanGoBack: false))
        XCTAssertFalse(LectureScreenModel.consumesNativeBack(showingKlas: true, klasCanGoBack: true))
        XCTAssertFalse(LectureScreenModel.consumesNativeBack(showingKlas: false, klasCanGoBack: false))
    }

    func testRootViewControllerDoesNotBeginGesture() {
        let nav = makeNavigationController(root: UIViewController())
        nav.view.layoutIfNeeded()

        guard let gesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }

        XCTAssertFalse(nav.gestureRecognizerShouldBegin(gesture))
    }

    func testPushedNativeViewBeginsGesture() {
        let root = UIViewController()
        let pushed = UIViewController()
        pushed.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)

        let nav = makeNavigationController(root: root)
        nav.pushViewController(pushed, animated: false)
        nav.view.layoutIfNeeded()

        guard let gesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }

        XCTAssertTrue(nav.gestureRecognizerShouldBegin(gesture))
    }

    func testPushedViewWithWebViewCannotGoBackBeginsGesture() {
        let root = UIViewController()
        let pushed = UIViewController()
        pushed.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)

        let webView = MockWebView(frame: pushed.view.bounds, configuration: WKWebViewConfiguration())
        webView.mockCanGoBack = false
        pushed.view.addSubview(webView)

        let nav = makeNavigationController(root: root)
        nav.pushViewController(pushed, animated: false)
        nav.view.layoutIfNeeded()

        guard let gesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }

        XCTAssertTrue(nav.gestureRecognizerShouldBegin(gesture))
    }

    func testPushedViewWithWebViewCanGoBackYieldsGesture() {
        let root = UIViewController()
        let pushed = UIViewController()
        pushed.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)

        let webView = MockWebView(frame: pushed.view.bounds, configuration: WKWebViewConfiguration())
        webView.mockCanGoBack = true
        pushed.view.addSubview(webView)

        let nav = makeNavigationController(root: root)
        nav.pushViewController(pushed, animated: false)
        nav.view.layoutIfNeeded()

        guard let gesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }

        XCTAssertFalse(nav.gestureRecognizerShouldBegin(gesture))
    }

    func testStackedWebViewsResolvesTopmostActiveWebView() {
        let root = UIViewController()
        let pushed = UIViewController()
        pushed.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)

        let bottomWebView = MockWebView(frame: pushed.view.bounds, configuration: WKWebViewConfiguration())
        bottomWebView.mockCanGoBack = true

        let topWebView = MockWebView(frame: pushed.view.bounds, configuration: WKWebViewConfiguration())
        topWebView.mockCanGoBack = false
        topWebView.alpha = 0
        topWebView.isUserInteractionEnabled = false

        pushed.view.addSubview(bottomWebView)
        pushed.view.addSubview(topWebView)

        let nav = makeNavigationController(root: root)
        nav.pushViewController(pushed, animated: false)
        nav.view.layoutIfNeeded()

        guard let gesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }

        // topWebView is hidden & non-interactive -> bottomWebView is active and canGoBack -> yields
        XCTAssertFalse(nav.gestureRecognizerShouldBegin(gesture))

        // Now activate topWebView
        topWebView.alpha = 1
        topWebView.isUserInteractionEnabled = true

        // topWebView is now active and cannot go back -> pop allowed
        XCTAssertTrue(nav.gestureRecognizerShouldBegin(gesture))
    }

    func testNativeLayerBlocksSystemPopEvenWhenWebCanGoBack() {
        let root = UIViewController()
        let pushed = UIViewController()
        pushed.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)

        let webView = MockWebView(frame: pushed.view.bounds, configuration: WKWebViewConfiguration())
        webView.mockCanGoBack = true
        webView.allowsBackForwardNavigationGestures = true
        pushed.view.addSubview(webView)

        let installer = InteractivePopGestureInstaller.Controller()
        installer.consumesNativeBack = true
        var consumed = 0
        installer.onConsumeBack = { consumed += 1 }
        pushed.addChild(installer)
        installer.didMove(toParent: pushed)

        let nav = makeNavigationController(root: root)
        nav.pushViewController(pushed, animated: false)
        nav.view.layoutIfNeeded()
        installer.configureNav()

        guard let popGesture = nav.interactivePopGestureRecognizer else {
            XCTFail("interactivePopGestureRecognizer must exist")
            return
        }
        guard let consumeGesture = nav.consumeBackRecognizer else {
            XCTFail("consumeBackRecognizer must exist")
            return
        }

        XCTAssertFalse(nav.gestureRecognizerShouldBegin(popGesture))
        XCTAssertTrue(nav.gestureRecognizerShouldBegin(consumeGesture))
        XCTAssertFalse(webView.allowsBackForwardNavigationGestures)

        nav.commitConsumeBackIfNeeded(translationX: 10, velocityX: 0)
        XCTAssertEqual(consumed, 0)

        nav.commitConsumeBackIfNeeded(translationX: 100, velocityX: 0)
        XCTAssertEqual(consumed, 1)

        installer.consumesNativeBack = false
        installer.configureNav()
        XCTAssertFalse(nav.gestureRecognizerShouldBegin(popGesture))
        XCTAssertFalse(nav.gestureRecognizerShouldBegin(consumeGesture))
        XCTAssertTrue(webView.allowsBackForwardNavigationGestures)

        webView.mockCanGoBack = false
        XCTAssertTrue(nav.gestureRecognizerShouldBegin(popGesture))
        XCTAssertFalse(nav.gestureRecognizerShouldBegin(consumeGesture))
    }

    func testInstallerConfiguresNavigationControllerDelegate() {
        let root = UIViewController()
        let nav = UINavigationController(rootViewController: root)
        let controller = InteractivePopGestureInstaller.Controller()

        root.addChild(controller)
        controller.didMove(toParent: root)

        XCTAssertTrue(nav.interactivePopGestureRecognizer?.delegate === nav)
    }

    func testSwiftUINavigationStackIntegration() {
        struct TestHost: View {
            @State var path = [1]
            var body: some View {
                NavigationStack(path: $path) {
                    Text("Home")
                        .navigationDestination(for: Int.self) { _ in
                            Text("Detail")
                        }
                }
                .interactivePopGesture()
            }
        }

        let host = UIHostingController(rootView: TestHost())
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.layoutIfNeeded()

        func findNavController(in view: UIView) -> UINavigationController? {
            var responder: UIResponder? = view
            while let r = responder {
                if let nav = r as? UINavigationController { return nav }
                responder = r.next
            }
            for subview in view.subviews {
                if let found = findNavController(in: subview) { return found }
            }
            return nil
        }

        let nav = findNavController(in: host.view)
        print("FOUND NAV: \(String(describing: nav))")
        print("DELEGATE: \(String(describing: nav?.interactivePopGestureRecognizer?.delegate))")
        XCTAssertNotNil(nav)
        XCTAssertTrue(nav?.interactivePopGestureRecognizer?.delegate === nav)
    }
}
