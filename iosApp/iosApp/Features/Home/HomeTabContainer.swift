import SwiftUI
import UIKit
import WebKit

@available(iOS 18.0, *)
struct HomeTabContainer: UIViewControllerRepresentable {
    let webView: WKWebView
    let selectedTab: String
    let isTabBarHidden: Bool
    let isWebAccessibilityHidden: Bool
    let onSelect: (String) -> Void
    let onBottomNavigationHeightChange: (CGFloat) -> Void

    func makeUIViewController(context: Context) -> HomeTabController {
        HomeTabController(
            webView: webView,
            initialTab: selectedTab,
            onSelect: onSelect,
            onBottomNavigationHeightChange: onBottomNavigationHeightChange
        )
    }

    func updateUIViewController(_ controller: HomeTabController, context: Context) {
        controller.onSelect = onSelect
        controller.onBottomNavigationHeightChange = onBottomNavigationHeightChange
        controller.select(selectedTab)
        controller.setTabBarHiddenIfNeeded(isTabBarHidden)
        webView.accessibilityLabel = "KLAS+"
        webView.accessibilityElementsHidden = isWebAccessibilityHidden
    }

    static func dismantleUIViewController(_ controller: HomeTabController, coordinator: ()) {
        controller.detachWebViewIfOwned()
    }
}

// 탭마다 WebView를 만들지 않고 선택된 탭 화면으로 같은 WKWebView를 옮긴다
@available(iOS 18.0, *)
final class HomeTabController: UITabBarController, UITabBarControllerDelegate {
    static let tabIDs = ["feed", "timetable", "calendar", "menu"]

    let webView: WKWebView
    var onSelect: (String) -> Void
    var onBottomNavigationHeightChange: (CGFloat) -> Void
    private var isSelectingProgrammatically = false
    private var reportedBottomNavigationHeight: CGFloat?

    init(
        webView: WKWebView,
        initialTab: String,
        onSelect: @escaping (String) -> Void,
        onBottomNavigationHeightChange: @escaping (CGFloat) -> Void = { _ in }
    ) {
        self.webView = webView
        self.onSelect = onSelect
        self.onBottomNavigationHeightChange = onBottomNavigationHeightChange
        super.init(nibName: nil, bundle: nil)
        tabs = [
            makeTab(id: "feed", title: "피드", systemImage: "square.grid.2x2"),
            makeTab(id: "timetable", title: "시간표", systemImage: "rectangle.split.3x1"),
            makeTab(id: "calendar", title: "캘린더", systemImage: "calendar"),
            makeTab(id: "menu", title: "전체", systemImage: "line.3.horizontal")
        ]
        // 탭이 네 개뿐이라 iPad에서도 sidebar 없이 탭 바만 쓴다
        mode = .tabBar
        if #available(iOS 26.0, *) {
            tabBarMinimizeBehavior = .onScrollDown
        }
        delegate = self
        tabBar.accessibilityIdentifier = "native_home_navigation"
        tabBar.tintColor = UIColor(KlasTheme.primary)
        tabBar.unselectedItemTintColor = UIColor(KlasTheme.onSurfaceVariant)
        select(initialTab)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(KlasTheme.background)
    }

    func select(_ tabID: String) {
        guard let tab = tabs.first(where: { $0.identifier == tabID }), selectedTab !== tab else { return }
        // iOS 26은 selectedTab 대입에도 didSelectTab을 호출하므로 웹 시작 이동을 네이티브 선택으로 되돌려 보내지 않는다
        isSelectingProgrammatically = true
        selectedTab = tab
        isSelectingProgrammatically = false
    }

    func setTabBarHiddenIfNeeded(_ hidden: Bool) {
        guard isTabBarHidden != hidden else { return }
        setTabBarHidden(hidden, animated: true)
    }

    func detachWebViewIfOwned() {
        guard isViewLoaded, webView.isDescendant(of: view) else { return }
        webView.removeFromSuperview()
    }

    func tabBarController(_ tabBarController: UITabBarController, didSelectTab selectedTab: UITab, previousTab: UITab?) {
        guard !isSelectingProgrammatically, Self.tabIDs.contains(selectedTab.identifier) else { return }
        onSelect(selectedTab.identifier)
    }

    fileprivate func attachWebView(to content: UIViewController) {
        guard webView.superview !== content.view else { return }
        webView.translatesAutoresizingMaskIntoConstraints = false
        content.view.addSubview(webView)
        let guide = content.view.safeAreaLayoutGuide
        // 탭 화면 safe area에는 iPad 상단 탭 바 높이가 포함되므로 위쪽은 상태 바 기준에 맞춰 탭 바가 웹 헤더 위에 뜨게 한다
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: content.view.bottomAnchor)
        ])
        content.setContentScrollView(webView.scrollView, for: .bottom)
        if #available(iOS 26.0, *) {
            webView.scrollView.topEdgeEffect.isHidden = true
        }
    }

    fileprivate func reportBottomNavigationHeight(from content: UIViewController) {
        guard content === selectedViewController, isViewLoaded else { return }
        let height = max(0, content.view.safeAreaInsets.bottom - view.safeAreaInsets.bottom)
        guard height != reportedBottomNavigationHeight else { return }
        reportedBottomNavigationHeight = height
        onBottomNavigationHeightChange(height)
    }

    private func makeTab(id: String, title: String, systemImage: String) -> UITab {
        let tab = UITab(title: title, image: UIImage(systemName: systemImage), identifier: id) { [weak self] tab in
            HomeTabContentViewController(
                onAppearing: { self?.attachWebView(to: $0) },
                onSafeAreaChange: { self?.reportBottomNavigationHeight(from: $0) }
            )
        }
        // iPad 탭 바를 길게 눌러 편집할 때 탭을 지우거나 옮기지 못하게 한다
        tab.preferredPlacement = .fixed
        return tab
    }
}

private final class HomeTabContentViewController: UIViewController {
    private let onAppearing: (UIViewController) -> Void
    private let onSafeAreaChange: (UIViewController) -> Void

    init(onAppearing: @escaping (UIViewController) -> Void, onSafeAreaChange: @escaping (UIViewController) -> Void) {
        self.onAppearing = onAppearing
        self.onSafeAreaChange = onSafeAreaChange
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(KlasTheme.background)
    }

    override func viewIsAppearing(_ animated: Bool) {
        super.viewIsAppearing(animated)
        onAppearing(self)
        onSafeAreaChange(self)
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        onSafeAreaChange(self)
    }
}
