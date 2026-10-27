import UIKit
import WebKit
import XCTest
@testable import kw_klas_plus

@available(iOS 18.0, *)
@MainActor
final class HomeTabControllerTests: XCTestCase {
    private var window: UIWindow?

    override func tearDown() {
        window?.isHidden = true
        window = nil
        super.tearDown()
    }

    func testSelectingEachTabMovesSameWebViewIntoSelectedContent() throws {
        let holder = WebViewHolder()
        let creationID = holder.creationID
        let webView = holder.webView
        let controller = try makeHostedController(holder: holder, initialTab: "feed")

        for tab in HomeTabController.tabIDs + ["feed"] {
            controller.select(tab)
            pumpRunLoop()
            let content = try XCTUnwrap(controller.selectedViewController)
            XCTAssertEqual(controller.selectedTab?.identifier, tab)
            XCTAssertTrue(controller.webView === webView)
            XCTAssertTrue(webView.superview === content.view, "탭 \(tab)")
            XCTAssertNotNil(webView.window)
            XCTAssertTrue(content.contentScrollView(for: .bottom) === webView.scrollView)
        }
        XCTAssertEqual(holder.creationID, creationID)
        XCTAssertEqual(countWebViews(in: controller.view), 1)
    }

    func testProgrammaticSelectionDoesNotReportNativeSelection() throws {
        var selected: [String] = []
        let controller = try makeHostedController(holder: WebViewHolder(), initialTab: "feed") { selected.append($0) }

        controller.select("calendar")
        controller.select("unknown")
        pumpRunLoop()

        XCTAssertEqual(controller.selectedTab?.identifier, "calendar")
        XCTAssertEqual(selected, [])
    }

    func testUserSelectionReportsTabIdentifier() throws {
        var selected: [String] = []
        let controller = try makeHostedController(holder: WebViewHolder(), initialTab: "feed") { selected.append($0) }
        let menu = try XCTUnwrap(controller.tabs.first { $0.identifier == "menu" })

        controller.tabBarController(controller, didSelectTab: menu, previousTab: controller.tabs.first)

        XCTAssertEqual(selected, ["menu"])
    }

    func testEmptyInitialTabFallsBackToFirstTab() throws {
        let holder = WebViewHolder()
        let controller = try makeHostedController(holder: holder, initialTab: "")

        XCTAssertEqual(controller.selectedTab?.identifier, "feed")
        XCTAssertTrue(holder.webView.superview === controller.selectedViewController?.view)
    }

    func testDetachOnlyRemovesWebViewOwnedByController() throws {
        let holder = WebViewHolder()
        let old = try makeHostedController(holder: holder, initialTab: "feed")
        let replacement = HomeTabController(webView: holder.webView, initialTab: "timetable", onSelect: { _ in })
        window?.rootViewController = replacement
        pumpRunLoop()

        old.detachWebViewIfOwned()

        XCTAssertTrue(holder.webView.isDescendant(of: replacement.view))
        replacement.detachWebViewIfOwned()
        XCTAssertNil(holder.webView.superview)
    }

    func testWebViewStartsBelowStatusBarAndExtendsBehindTabBars() throws {
        let holder = WebViewHolder()
        let controller = try makeHostedController(holder: holder, initialTab: "feed")
        let content = try XCTUnwrap(controller.selectedViewController)
        controller.view.layoutIfNeeded()

        let statusBarInset = controller.view.safeAreaInsets.top
        let sideInsets = content.view.safeAreaInsets
        XCTAssertGreaterThan(statusBarInset, 0)
        XCTAssertEqual(holder.webView.frame.minY, statusBarInset, accuracy: 0.5)
        XCTAssertEqual(holder.webView.frame.maxY, content.view.bounds.maxY, accuracy: 0.5)
        XCTAssertEqual(holder.webView.frame.width, content.view.bounds.width - sideInsets.left - sideInsets.right, accuracy: 0.5)
    }

    func testWebViewTopStaysBelowStatusBarAcrossTabSwitches() throws {
        let holder = WebViewHolder()
        let controller = try makeHostedController(holder: holder, initialTab: "feed")

        for tab in HomeTabController.tabIDs {
            controller.select(tab)
            pumpRunLoop()
            controller.view.layoutIfNeeded()
            let frame = holder.webView.convert(holder.webView.bounds, to: controller.view)
            XCTAssertEqual(frame.minY, controller.view.safeAreaInsets.top, accuracy: 0.5, "탭 \(tab)")
        }
    }

    func testTabsCannotBeRemovedOrReorderedByUser() throws {
        let controller = try makeHostedController(holder: WebViewHolder(), initialTab: "feed")

        XCTAssertEqual(controller.tabs.map(\.identifier), HomeTabController.tabIDs)
        for tab in controller.tabs {
            XCTAssertEqual(tab.preferredPlacement, .fixed, tab.identifier)
            XCTAssertFalse(tab.allowsHiding, tab.identifier)
            XCTAssertFalse(tab.isHidden, tab.identifier)
        }
    }

    func testUsesTabBarModeWithoutSidebar() throws {
        let controller = try makeHostedController(holder: WebViewHolder(), initialTab: "feed")

        XCTAssertEqual(controller.mode, .tabBar)
    }

    func testReportsTabBarHeightAboveBottomSafeArea() throws {
        var heights: [CGFloat] = []
        let controller = try makeHostedController(holder: WebViewHolder(), initialTab: "feed", onHeightChange: { heights.append($0) })
        let content = try XCTUnwrap(controller.selectedViewController)
        let expected = content.view.safeAreaInsets.bottom - controller.view.safeAreaInsets.bottom

        XCTAssertEqual(try XCTUnwrap(heights.last), max(0, expected), accuracy: 0.5)
        if controller.traitCollection.horizontalSizeClass == .compact {
            XCTAssertGreaterThan(expected, 0)
        }
    }

    func testInjectsWindowControlsInsetAndRestoresItAfterDocumentReload() throws {
        let holder = WebViewHolder()
        var layout = HomeHeaderLayout(windowControlsInset: 72)
        let controller = try makeHostedController(holder: holder, initialTab: "feed", measureHeaderLayout: { _ in layout })
        controller.view.layoutIfNeeded()

        loadPage(in: holder)
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.windowControlsInsetProperty, in: holder.webView, toBe: "72px"), "72px")

        loadPage(in: holder)
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.windowControlsInsetProperty, in: holder.webView, toBe: "72px"), "72px")

        layout = HomeHeaderLayout()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.windowControlsInsetProperty, in: holder.webView, toBe: "0px"), "0px")
    }

    func testInjectsTopBarRowOnlyWhileSystemTopRowExists() throws {
        let holder = WebViewHolder()
        var layout = HomeHeaderLayout(topBarHeight: 44)
        let controller = try makeHostedController(holder: holder, initialTab: "feed", measureHeaderLayout: { _ in layout })
        controller.view.layoutIfNeeded()

        loadPage(in: holder)
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.topBarTopProperty, in: holder.webView, toBe: "0px"), "0px")
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.topBarHeightProperty, in: holder.webView, toBe: "44px"), "44px")

        layout = HomeHeaderLayout()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.topBarTopProperty, in: holder.webView, toBe: ""), "")
        XCTAssertEqual(waitForCSSVariable(HomeHeaderLayout.topBarHeightProperty, in: holder.webView, toBe: ""), "")
    }

    func testDoesNotInjectHeaderLayoutWithoutSystemControls() throws {
        let holder = WebViewHolder()
        let controller = try makeHostedController(holder: holder, initialTab: "feed", measureHeaderLayout: { _ in HomeHeaderLayout() })
        controller.view.layoutIfNeeded()

        loadPage(in: holder)

        for property in [HomeHeaderLayout.windowControlsInsetProperty, HomeHeaderLayout.topBarTopProperty, HomeHeaderLayout.topBarHeightProperty] {
            XCTAssertEqual(waitForCSSVariable(property, in: holder.webView, toBe: "", timeout: 0.5), "", property)
        }
    }

    private func makeHostedController(
        holder: WebViewHolder,
        initialTab: String,
        onHeightChange: @escaping (CGFloat) -> Void = { _ in },
        measureHeaderLayout: @escaping (UIViewController) -> HomeHeaderLayout = HomeTabController.measuredHeaderLayout,
        onSelect: @escaping (String) -> Void = { _ in }
    ) throws -> HomeTabController {
        let controller = HomeTabController(
            webView: holder.webView,
            initialTab: initialTab,
            onSelect: onSelect,
            onBottomNavigationHeightChange: onHeightChange,
            measureHeaderLayout: measureHeaderLayout
        )
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = controller
        window.makeKeyAndVisible()
        self.window = window
        pumpRunLoop()
        return controller
    }

    private func loadPage(in holder: WebViewHolder) {
        holder.loadHTML("<html><body>home</body></html>", baseURL: URL(string: "https://klasplus.yuntae.in/feed")!)
        let deadline = Date().addingTimeInterval(5)
        repeat { pumpRunLoop() } while holder.webView.isLoading && Date() < deadline
    }

    private func waitForCSSVariable(_ property: String, in webView: WKWebView, toBe expected: String, timeout: TimeInterval = 5) -> String? {
        let script = "getComputedStyle(document.documentElement).getPropertyValue('\(property)').trim()"
        var value: String?
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            var finished = false
            webView.evaluateJavaScript(script) { result, _ in
                value = result as? String
                finished = true
            }
            while !finished && Date() < deadline { pumpRunLoop() }
        } while value != expected && Date() < deadline
        return value
    }

    private func countWebViews(in root: UIView) -> Int {
        (root is WKWebView ? 1 : 0) + root.subviews.reduce(0) { $0 + countWebViews(in: $1) }
    }

    private func pumpRunLoop() {
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
    }
}
